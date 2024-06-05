package org.togetherjava.tjbot.features.moderation.audit;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.TimeUtil;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageRequest;
import net.dv8tion.jda.internal.requests.CompletedRestAction;

import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.SlashCommandAdapter;
import org.togetherjava.tjbot.features.moderation.ActionRecord;
import org.togetherjava.tjbot.features.moderation.ModerationAction;
import org.togetherjava.tjbot.features.moderation.ModerationActionsStore;
import org.togetherjava.tjbot.features.moderation.ModerationUtils;

import javax.annotation.Nullable;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This command lists all moderation actions that have been taken against a given user, for example
 * warnings, mutes and bans.
 * <p>
 * The command fails if the user triggering it is lacking permissions to either audit other users or
 * to audit the specific given user (for example a moderator attempting to audit an admin).
 */
public final class AuditCommand extends SlashCommandAdapter {
    private static final String TARGET_OPTION = "user";
    private static final String COMMAND_NAME = "audit";
    private static final String ACTION_VERB = "audit";
    private static final int MAX_PAGE_LENGTH = 10;
    private static final String PREVIOUS_BUTTON_LABEL = "⬅";
    private static final String NEXT_BUTTON_LABEL = "➡";
    private final ModerationActionsStore actionsStore;

    /**
     * Constructs an instance.
     *
     * @param actionsStore used to store actions issued by this command
     */
    public AuditCommand(ModerationActionsStore actionsStore) {
        super(COMMAND_NAME, "Lists all moderation actions that have been taken against a user",
                CommandVisibility.GUILD);

        getData().addOption(OptionType.USER, TARGET_OPTION, "The user who to retrieve actions for",
                true);

        this.actionsStore = Objects.requireNonNull(actionsStore);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {
        OptionMapping targetOption =
                Objects.requireNonNull(event.getOption(TARGET_OPTION), "The target is null");
        User target = targetOption.getAsUser();
        Member author = Objects.requireNonNull(event.getMember(), "The author is null");

        Guild guild = Objects.requireNonNull(event.getGuild());
        Member bot = guild.getSelfMember();

        if (!handleChecks(bot, author, targetOption.getAsMember(), event)) {
            return;
        }

        auditUser(MessageCreateBuilder::new, guild.getIdLong(), target.getIdLong(),
                event.getMember().getIdLong(), -1, event.getJDA())
            .map(MessageCreateBuilder::build)
            .flatMap(event::reply)
            .queue();
    }

    private boolean handleChecks(Member bot, Member author, @Nullable Member target,
            IReplyCallback event) {
        // Member doesn't exist if attempting to audit a user who is not part of the guild.
        if (target == null) {
            return true;
        }
        return ModerationUtils.handleCanInteractWithTarget(ACTION_VERB, bot, author, target, event);
    }

    /**
     * @param pageNumber page number to display when actions are divided into pages and each page
     *        can contain {@link AuditCommand#MAX_PAGE_LENGTH} actions, {@code -1} encodes the last
     *        page
     */
    private <R extends MessageRequest<R>> RestAction<R> auditUser(
            Supplier<R> messageBuilderSupplier, long guildId, long targetId, long callerId,
            int pageNumber, JDA jda) {
        List<ActionRecord> actions = actionsStore.getActionsByTargetAscending(guildId, targetId);
        List<List<ActionRecord>> groupedActions = groupActionsByPages(actions);
        int totalPages = groupedActions.size();

        int pageNumberInLimits;
        if (pageNumber == -1) {
            pageNumberInLimits = totalPages;
        } else {
            pageNumberInLimits = Math.clamp(pageNumber, 1, totalPages);
        }

        return jda.retrieveUserById(targetId)
            .map(user -> createSummaryEmbed(user, actions))
            .flatMap(auditEmbed -> attachEmbedFields(auditEmbed, groupedActions, pageNumberInLimits,
                    totalPages, jda))
            .map(auditEmbed -> attachPageTurnButtons(messageBuilderSupplier, auditEmbed,
                    pageNumberInLimits, totalPages, guildId, targetId, callerId));
    }

    private List<List<ActionRecord>> groupActionsByPages(List<ActionRecord> actions) {
        List<List<ActionRecord>> groupedActions = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            if (i % AuditCommand.MAX_PAGE_LENGTH == 0) {
                groupedActions.add(new ArrayList<>(AuditCommand.MAX_PAGE_LENGTH));
            }

            groupedActions.getLast().add(actions.get(i));
        }

        return groupedActions;
    }

    private static EmbedBuilder createSummaryEmbed(User user, Collection<ActionRecord> actions) {
        String avatarOrDefaultUrl = user.getEffectiveAvatarUrl();

        return new EmbedBuilder().setTitle("Audit log of **%s**".formatted(user.getName()))
            .setAuthor(user.getName(), null, avatarOrDefaultUrl)
            .setDescription(createSummaryMessageDescription(actions))
            .setColor(ModerationUtils.AMBIENT_COLOR);
    }

    private static String createSummaryMessageDescription(Collection<ActionRecord> actions) {
        int actionAmount = actions.size();

        String shortSummary = "There are **%s actions** against the user."
            .formatted(actionAmount == 0 ? "no" : actionAmount);

        if (actionAmount == 0) {
            return shortSummary;
        }

        // Summary of all actions with their count, like "- Warn: 5", descending
        Map<ModerationAction, Long> actionTypeToCount = actions.stream()
            .collect(Collectors.groupingBy(ActionRecord::actionType, Collectors.counting()));

        String typeCountSummary = actionTypeToCount.entrySet()
            .stream()
            .filter(typeAndCount -> typeAndCount.getValue() > 0)
            .sorted(Map.Entry.<ModerationAction, Long>comparingByValue().reversed())
            .map(typeAndCount -> "- **%s**: %d".formatted(typeAndCount.getKey(),
                    typeAndCount.getValue()))
            .collect(Collectors.joining("\n"));

        return shortSummary + "\n" + typeCountSummary;
    }

    private RestAction<EmbedBuilder> attachEmbedFields(EmbedBuilder auditEmbed,
            List<? extends List<ActionRecord>> groupedActions, int pageNumber, int totalPages,
            JDA jda) {
        if (groupedActions.isEmpty()) {
            return new CompletedRestAction<>(jda, auditEmbed);
        }

        List<RestAction<MessageEmbed.Field>> embedFieldTasks = new ArrayList<>();
        groupedActions.get(pageNumber - 1)
            .forEach(action -> embedFieldTasks.add(actionToField(action, jda)));

        return RestAction.allOf(embedFieldTasks).map(embedFields -> {
            embedFields.forEach(auditEmbed::addField);

            auditEmbed.setFooter("Page: " + pageNumber + "/" + totalPages);
            return auditEmbed;
        });
    }

    private static RestAction<MessageEmbed.Field> actionToField(ActionRecord action, JDA jda) {
        return jda.retrieveUserById(action.authorId())
            .map(author -> author == null ? "(unknown user)" : author.getName())
            .map(authorText -> {
                String expiresAtFormatted = action.actionExpiresAt() == null ? ""
                        : "\nTemporary action, expires at: " + formatTime(action.actionExpiresAt());

                String fieldName = "%s by %s".formatted(action.actionType().name(), authorText);
                String fieldDescription = """
                        %s
                        Issued at: %s%s
                        """.formatted(action.reason(), formatTime(action.issuedAt()),
                        expiresAtFormatted);

                return new MessageEmbed.Field(fieldName, fieldDescription, false);
            });
    }

    private static String formatTime(Instant when) {
        return TimeUtil.getDateTimeString(when.atOffset(ZoneOffset.UTC));
    }

    private <R extends MessageRequest<R>> R attachPageTurnButtons(
            Supplier<R> messageBuilderSupplier, EmbedBuilder auditEmbed, int pageNumber,
            int totalPages, long guildId, long targetId, long callerId) {
        var messageBuilder = messageBuilderSupplier.get();
        messageBuilder.setEmbeds(auditEmbed.build());

        if (totalPages <= 1) {
            return messageBuilder;
        }
        List<Button> pageTurnButtons =
                createPageTurnButtons(guildId, targetId, callerId, pageNumber, totalPages);

        return messageBuilder.setActionRow(pageTurnButtons);
    }

    private List<Button> createPageTurnButtons(long guildId, long targetId, long callerId,
            int pageNumber, int totalPages) {
        int previousButtonTurnPageBy = -1;
        Button previousButton = createPageTurnButton(PREVIOUS_BUTTON_LABEL, guildId, targetId,
                callerId, pageNumber, previousButtonTurnPageBy);
        if (pageNumber <= 1) {
            previousButton = previousButton.asDisabled();
        }

        int nextButtonTurnPageBy = 1;
        Button nextButton = createPageTurnButton(NEXT_BUTTON_LABEL, guildId, targetId, callerId,
                pageNumber, nextButtonTurnPageBy);
        if (pageNumber >= totalPages) {
            nextButton = nextButton.asDisabled();
        }

        return List.of(previousButton, nextButton);
    }

    private Button createPageTurnButton(String label, long guildId, long targetId, long callerId,
            long pageNumber, int turnPageBy) {
        return Button.primary(generateComponentId(String.valueOf(guildId), String.valueOf(targetId),
                String.valueOf(callerId), String.valueOf(pageNumber), String.valueOf(turnPageBy)),
                label);
    }

    @Override
    public void onButtonClick(ButtonInteractionEvent event, List<String> args) {
        long commandUserId = Long.parseLong(args.get(2));
        long buttonUserId = event.getMember().getIdLong();

        if (commandUserId != buttonUserId) {
            event.reply("Only the user who triggered the command can turn pages.")
                .setEphemeral(true)
                .queue();

            return;
        }

        int currentPage = Integer.parseInt(args.get(3));
        int turnPageBy = Integer.parseInt(args.get(4));

        long guildId = Long.parseLong(args.getFirst());
        long targetId = Long.parseLong(args.get(1));
        int pageToDisplay = currentPage + turnPageBy;

        auditUser(MessageEditBuilder::new, guildId, targetId, buttonUserId, pageToDisplay,
                event.getJDA())
            .map(MessageEditBuilder::build)
            .flatMap(event::editMessage)
            .queue();
    }
}
