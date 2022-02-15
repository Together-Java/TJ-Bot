package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.TimeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.config.Config;

import java.time.ZoneOffset;
import java.util.*;
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
     */
    public AuditCommand(@NotNull ModerationActionsStore actionsStore) {
        super(COMMAND_NAME, "Lists all moderation actions that have been taken against a user",
                CommandVisibility.GUILD);

        getData().addOption(OptionType.USER, TARGET_OPTION, "The user who to retrieve actions for",
                true);

        hasRequiredRole = Pattern.compile(Config.getInstance().getHeavyModerationRolePattern())
            .asMatchPredicate();
        this.actionsStore = Objects.requireNonNull(actionsStore);
    }

    private static @NotNull MessageEmbed createSummaryMessage(@NotNull User user,
            @NotNull Collection<ActionRecord> actions) {
        return new EmbedBuilder().setTitle("Audit log of **%s**".formatted(user.getAsTag()))
            .setAuthor(user.getName(), null, user.getAvatarUrl())
            .setDescription(createSummaryMessageDescription(actions))
            .setColor(ModerationUtils.AMBIENT_COLOR)
            .build();
    }

    private static @NotNull String createSummaryMessageDescription(
            @NotNull Collection<ActionRecord> actions) {
        int actionAmount = actions.size();
        if (actionAmount == 0) {
            return "There are **no actions** against the user.";
        }

        String shortSummary = "There are **%d actions** against the user.".formatted(actionAmount);

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

    private static @NotNull RestAction<MessageEmbed> actionToMessage(@NotNull ActionRecord action,
            @NotNull JDA jda) {
        String footer = action.actionExpiresAt() == null ? null
                : "Temporary action, expires at %s".formatted(TimeUtil
                    .getDateTimeString(action.actionExpiresAt().atOffset(ZoneOffset.UTC)));

        return jda.retrieveUserById(action.authorId())
            .map(author -> new EmbedBuilder().setTitle(action.actionType().name())
                .setAuthor(author == null ? "(unknown user)" : author.getAsTag(), null,
                        author == null ? null : author.getAvatarUrl())
                .setDescription(action.reason())
                .setTimestamp(action.issuedAt())
                .setFooter(footer)
                .setColor(ModerationUtils.AMBIENT_COLOR)
                .build());
    }

    private static <E> @NotNull List<E> prependElement(@NotNull E element,
            @NotNull Collection<? extends E> elements) {
        List<E> allElements = new ArrayList<>(elements.size() + 1);
        allElements.add(element);
        allElements.addAll(elements);
        return allElements;
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        OptionMapping targetOption =
                Objects.requireNonNull(event.getOption(TARGET_OPTION), "The target is null");
        User target = targetOption.getAsUser();
        Member author = Objects.requireNonNull(event.getMember(), "The author is null");

        Guild guild = Objects.requireNonNull(event.getGuild());
        Member bot = guild.getSelfMember();

        if (!handleChecks(bot, author, targetOption.getAsMember(), guild, event)) {
            return;
        }

        auditUser(target, guild, event);
    }

    @SuppressWarnings("BooleanMethodNameMustStartWithQuestion")
    private boolean handleChecks(@NotNull Member bot, @NotNull Member author,
            @Nullable Member target, @NotNull Guild guild, @NotNull IReplyCallback event) {
        // Member doesn't exist if attempting to audit a user who is not part of the guild.
        if (target != null && !ModerationUtils.handleCanInteractWithTarget(ACTION_VERB, bot, author,
                target, event)) {
            return false;
        }
        return ModerationUtils.handleHasAuthorRole(ACTION_VERB, hasRequiredRole, author, event);
    }

    private void auditUser(@NotNull User user, @NotNull ISnowflake guild,
            @NotNull IReplyCallback event) {
        List<ActionRecord> actions =
                actionsStore.getActionsByTargetAscending(guild.getIdLong(), user.getIdLong());

        MessageEmbed summary = createSummaryMessage(user, actions);
        if (actions.isEmpty()) {
            event.replyEmbeds(summary).queue();
            return;
        }

        // Computing messages for actual actions is done deferred and might require asking the
        // Discord API
        event.deferReply().queue();
        JDA jda = event.getJDA();

        RestAction<List<MessageEmbed>> messagesTask = RestAction
            .allOf(actions.stream().map(action -> actionToMessage(action, jda)).toList());
        messagesTask.map(messages -> prependElement(summary, messages))
            .flatMap(messages -> event.getHook().sendMessageEmbeds(messages))
            .queue();
    }
}
