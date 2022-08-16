package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.TimeUtil;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
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
    private static final int MAX_PAGE_LENGTH = 25;
    private static final String PREVIOUS_BUTTON_LABEL = "⬅";
    private static final String NEXT_BUTTON_LABEL = "➡";
    private final ModerationActionsStore actionsStore;

    /**
     * Constructs an instance.
     * 
     * @param actionsStore used to store actions issued by this command
     */
    public AuditCommand(@NotNull ModerationActionsStore actionsStore) {
        super(COMMAND_NAME, "Lists all moderation actions that have been taken against a user",
                SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, TARGET_OPTION, "The user who to retrieve actions for",
                true);

        this.actionsStore = Objects.requireNonNull(actionsStore);
    }

    private static @NotNull EmbedBuilder createSummaryEmbed(@NotNull User user,
            @NotNull Collection<ActionRecord> actions) {
        return new EmbedBuilder().setTitle("Audit log of **%s**".formatted(user.getAsTag()))
            .setAuthor(user.getName(), null, user.getAvatarUrl())
            .setDescription(createSummaryMessageDescription(actions))
            .setColor(ModerationUtils.AMBIENT_COLOR);
    }

    private static @NotNull String createSummaryMessageDescription(
            @NotNull Collection<ActionRecord> actions) {
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

    private static @NotNull MessageEmbed.Field actionToField(@NotNull ActionRecord action,
            @NotNull JDA jda) {
        Function<Instant, String> formatTime = instant -> {
            if (instant == null) {
                return "";
            }
            return TimeUtil.getDateTimeString(instant.atOffset(ZoneOffset.UTC));
        };

        User author = jda.getUserById(action.authorId());

        Instant expiresAt = action.actionExpiresAt();
        String expiresAtFormatted = expiresAt == null ? ""
                : "\nTemporary action, expires at: " + formatTime.apply(expiresAt);

        return new MessageEmbed.Field(
                action.actionType().name() + " by "
                        + (author == null ? "(unknown user)" : author.getAsTag()),
                action.reason() + "\nIssued at: " + formatTime.apply(action.issuedAt())
                        + expiresAtFormatted,
                false);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        OptionMapping targetOption =
                Objects.requireNonNull(event.getOption(TARGET_OPTION), "The target is null");
        User target = targetOption.getAsUser();
        Member author = Objects.requireNonNull(event.getMember(), "The author is null");

        Guild guild = Objects.requireNonNull(event.getGuild());
        Member bot = guild.getSelfMember();

        if (!handleChecks(bot, author, targetOption.getAsMember(), event)) {
            return;
        }

        event
            .reply(auditUser(guild.getIdLong(), target.getIdLong(), event.getMember().getIdLong(),
                    1, event.getJDA()))
            .queue();
    }

    @SuppressWarnings("BooleanMethodNameMustStartWithQuestion")
    private boolean handleChecks(@NotNull Member bot, @NotNull Member author,
            @Nullable Member target, @NotNull IReplyCallback event) {
        // Member doesn't exist if attempting to audit a user who is not part of the guild.
        if (target == null) {
            return false;
        }
        return ModerationUtils.handleCanInteractWithTarget(ACTION_VERB, bot, author, target, event);
    }

    private @NotNull List<List<ActionRecord>> groupActionsByPages(
            @NotNull List<ActionRecord> actions) {
        List<List<ActionRecord>> groupedActions = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            if (i % AuditCommand.MAX_PAGE_LENGTH == 0) {
                groupedActions.add(new ArrayList<>(AuditCommand.MAX_PAGE_LENGTH));
            }

            groupedActions.get(groupedActions.size() - 1).add(actions.get(i));
        }

        return groupedActions;
    }

    /**
     * @param pageNumber page number to display when actions are divided into pages and each page
     *        can contain {@link AuditCommand#MAX_PAGE_LENGTH} actions
     */
    private @NotNull Message auditUser(long guildId, long targetId, long callerId, int pageNumber,
            @NotNull JDA jda) {
        List<ActionRecord> actions = actionsStore.getActionsByTargetAscending(guildId, targetId);
        List<List<ActionRecord>> groupedActions = groupActionsByPages(actions);
        int totalPages = groupedActions.size();

        // Handles the case of too low page number and too high page number
        pageNumber = Math.max(1, pageNumber);
        pageNumber = Math.min(totalPages, pageNumber);

        EmbedBuilder audit = createSummaryEmbed(jda.retrieveUserById(targetId).complete(), actions);

        if (groupedActions.isEmpty()) {
            return new MessageBuilder(audit.build()).build();
        }

        groupedActions.get(pageNumber - 1)
            .forEach(action -> audit.addField(actionToField(action, jda)));

        return new MessageBuilder(audit.setFooter("Page: " + pageNumber + "/" + totalPages).build())
            .setActionRows(makeActionRow(guildId, targetId, callerId, pageNumber, totalPages))
            .build();
    }

    private @NotNull ActionRow makeActionRow(long guildId, long targetId, long callerId,
            int pageNumber, int totalPages) {
        int previousButtonTurnPageBy = -1;
        Button previousButton = createPageTurnButton(PREVIOUS_BUTTON_LABEL, guildId, targetId,
                callerId, pageNumber, previousButtonTurnPageBy);
        if (pageNumber == 1) {
            previousButton = previousButton.asDisabled();
        }

        int nextButtonTurnPageBy = 1;
        Button nextButton = createPageTurnButton(NEXT_BUTTON_LABEL, guildId, targetId, callerId,
                pageNumber, nextButtonTurnPageBy);
        if (pageNumber == totalPages) {
            nextButton = nextButton.asDisabled();
        }

        return ActionRow.of(previousButton, nextButton);
    }

    private @NotNull Button createPageTurnButton(@NotNull String label, long guildId, long targetId,
            long callerId, long pageNumber, int turnPageBy) {
        return Button.primary(generateComponentId(String.valueOf(guildId), String.valueOf(targetId),
                String.valueOf(callerId), String.valueOf(pageNumber), String.valueOf(turnPageBy)),
                label);
    }

    @Override
    public void onButtonClick(@NotNull ButtonInteractionEvent event, @NotNull List<String> args) {
        long callerId = Long.parseLong(args.get(2));
        long interactorId = event.getMember().getIdLong();

        if (callerId != interactorId) {
            event.reply("Only the user who triggered the command can use these buttons.")
                .setEphemeral(true)
                .queue();

            return;
        }

        int currentPage = Integer.parseInt(args.get(3));
        int turnPageBy = Integer.parseInt(args.get(4));

        long guildId = Long.parseLong(args.get(0));
        long targetId = Long.parseLong(args.get(1));
        int pageToDisplay = currentPage + turnPageBy;

        event.editMessage(auditUser(guildId, targetId, interactorId, pageToDisplay, event.getJDA()))
            .queue();
    }
}
