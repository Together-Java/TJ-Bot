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
import org.togetherjava.tjbot.config.Config;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
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
    private final Predicate<String> hasRequiredRole;
    private final ModerationActionsStore actionsStore;

    /**
     * Constructs an instance.
     *
     * @param actionsStore used to store actions issued by this command
     * @param config the config to use for this
     */
    public AuditCommand(@NotNull ModerationActionsStore actionsStore, @NotNull Config config) {
        super(COMMAND_NAME, "Lists all moderation actions that have been taken against a user",
                SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, TARGET_OPTION, "The user who to retrieve actions for",
                true);

        hasRequiredRole =
                Pattern.compile(config.getHeavyModerationRolePattern()).asMatchPredicate();
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

        event.reply(auditUser(guild.getIdLong(), target.getIdLong(), 1, event.getJDA())).queue();
    }

    @SuppressWarnings("BooleanMethodNameMustStartWithQuestion")
    private boolean handleChecks(@NotNull Member bot, @NotNull Member author,
            @Nullable Member target, @NotNull IReplyCallback event) {
        // Member doesn't exist if attempting to audit a user who is not part of the guild.
        if (target != null && !ModerationUtils.handleCanInteractWithTarget(ACTION_VERB, bot, author,
                target, event)) {
            return false;
        }
        return ModerationUtils.handleHasAuthorRole(ACTION_VERB, hasRequiredRole, author, event);
    }

    private Message auditUser(long guildId, long targetId, int pageNo, @NotNull JDA jda) {
        List<ActionRecord> actions = actionsStore.getActionsByTargetAscending(guildId, targetId);

        List<List<ActionRecord>> groupedActions = new ArrayList<>();
        for (int i = 0; i < actions.size(); i++) {
            if (i % 25 == 0) {
                groupedActions.add(new ArrayList<>(25));
            }

            groupedActions.get(groupedActions.size() - 1).add(actions.get(i));
        }

        int totalPage = groupedActions.size();

        EmbedBuilder audit = createSummaryEmbed(jda.retrieveUserById(targetId).complete(), actions);

        if (groupedActions.isEmpty()) {
            return new MessageBuilder(audit.build()).build();
        }

        groupedActions.get(pageNo - 1)
            .forEach(action -> audit.addField(actionToField(action, jda)));

        return new MessageBuilder(audit.setFooter("Page: " + pageNo + "/" + totalPage).build())
            .setActionRows(ActionRow.of(previousButton(guildId, targetId, pageNo),
                    nextButton(guildId, targetId, pageNo, totalPage)))
            .build();
    }

    private Button previousButton(long guildId, long targetId, int pageNo) {
        Button previousButton = Button.primary(generateComponentId(String.valueOf(guildId),
                String.valueOf(targetId), String.valueOf(pageNo), "-1"), "⬅");

        if (pageNo == 1) {
            previousButton = previousButton.asDisabled();
        }

        return previousButton;
    }

    private Button nextButton(long guildId, long targetId, int pageNo, int totalPage) {
        Button nextButton = Button.primary(generateComponentId(String.valueOf(guildId),
                String.valueOf(targetId), String.valueOf(pageNo), "1"), "➡");

        if (pageNo == totalPage) {
            nextButton = nextButton.asDisabled();
        }

        return nextButton;
    }

    @Override
    public void onButtonClick(@NotNull ButtonInteractionEvent event, @NotNull List<String> args) {
        event
            .editMessage(auditUser(Long.parseLong(args.get(0)), Long.parseLong(args.get(1)),
                    Integer.parseInt(args.get(2)) + Integer.parseInt(args.get(3)), event.getJDA()))
            .queue();
    }
}
