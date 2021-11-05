package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.util.Objects;
import java.util.Optional;

/**
 * This command can ban users and optionally remove their messages from the past days. Banning can
 * also be paired with a ban reason. The command will also try to DM the user to inform them about
 * the action and the reason.
 * <p>
 * The command fails if the user triggering it is lacking permissions to either ban other users or
 * to ban the specific given user (for example a moderator attempting to ban an admin).
 */
public final class BanCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(BanCommand.class);
    private static final String TARGET_OPTION = "user";
    private static final String DELETE_HISTORY_OPTION = "delete-history";
    private static final String REASON_OPTION = "reason";

    /**
     * Constructs an instance.
     */
    public BanCommand() {
        super("ban", "Bans the given user from the server", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, TARGET_OPTION, "The user who you want to ban", true)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be banned", true)
            .addOptions(new OptionData(OptionType.INTEGER, DELETE_HISTORY_OPTION,
                    "the amount of days of the message history to delete, none means no messages are deleted.",
                    true).addChoice("none", 0).addChoice("recent", 1).addChoice("all", 7));
    }

    private static RestAction<InteractionHook> handleAlreadyBanned(@NotNull Guild.Ban ban,
            @NotNull Interaction event) {
        String reason = ban.getReason();
        String reasonText =
                reason == null || reason.isBlank() ? "" : " (reason: %s)".formatted(reason);

        String message = "The user '%s' is already banned%s.".formatted(ban.getUser().getAsTag(),
                reasonText);
        return event.reply(message).setEphemeral(true);
    }

    @SuppressWarnings("MethodWithTooManyParameters")
    private static RestAction<InteractionHook> banUser(@NotNull User target, @NotNull Member author,
            @NotNull String reason, int deleteHistoryDays, @NotNull Guild guild,
            @NotNull SlashCommandEvent event) {
        return event.getJDA()
            .openPrivateChannelById(target.getIdLong())
            .flatMap(channel -> channel.sendMessage(
                    """
                            Hey there, sorry to tell you but unfortunately you have been banned from the server %s.
                            If you think this was a mistake, please contact a moderator or admin of the server.
                            The reason for the ban is: %s
                            """
                        .formatted(guild.getName(), reason)))
            .mapToResult()
            .flatMap(sendDmResult -> {
                logger.info(
                        "'{}' ({}) banned the user '{}' ({}) from guild '{}' and deleted their message history of the last {} days, for reason '{}'.",
                        author.getUser().getAsTag(), author.getId(), target.getAsTag(),
                        target.getId(), guild.getName(), deleteHistoryDays, reason);

                return guild.ban(target, deleteHistoryDays, reason)
                    .map(banResult -> sendDmResult.isSuccess());
            })
            .map(hasSentDm -> {
                String dmNotice =
                        Boolean.TRUE.equals(hasSentDm) ? "" : "(Unable to send them a DM.)";
                return ModerationUtils.createActionResponse(author.getUser(),
                        ModerationUtils.Action.BAN, target, dmNotice, reason);
            })
            .flatMap(event::replyEmbeds);
    }

    private static Optional<RestAction<InteractionHook>> handleNotAlreadyBannedResponse(
            @NotNull Throwable alreadyBannedFailure, @NotNull Interaction event,
            @NotNull Guild guild, @NotNull User target) {
        if (alreadyBannedFailure instanceof ErrorResponseException errorResponseException) {
            if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_BAN) {
                return Optional.empty();
            }

            if (errorResponseException.getErrorResponse() == ErrorResponse.MISSING_PERMISSIONS) {
                logger.error("The bot does not have the '{}' permission on the guild '{}'.",
                        Permission.BAN_MEMBERS, guild.getName());
                return Optional.of(event.reply(
                        "I can not ban users in this guild since I do not have the %s permission."
                            .formatted(Permission.BAN_MEMBERS))
                    .setEphemeral(true));
            }
        }
        logger.warn("Something unexpected went wrong while trying to ban the user '{}'.",
                target.getAsTag(), alreadyBannedFailure);
        return Optional.of(event.reply("Failed to ban the user due to an unexpected problem.")
            .setEphemeral(true));
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        OptionMapping targetOption =
                Objects.requireNonNull(event.getOption(TARGET_OPTION), "The target is null");
        Member targetMember = targetOption.getAsMember();
        User targetUser = targetOption.getAsUser();
        Member author = Objects.requireNonNull(event.getMember(), "The author is null");
        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION), "The reason is null")
            .getAsString();

        Guild guild = Objects.requireNonNull(event.getGuild());
        Member bot = guild.getSelfMember();

        // Member doesn't exist if attempting to ban a user who is not part of the guild.
        if (targetMember != null && !ModerationUtils.handleCanInteractWithTarget("ban", bot, author,
                targetMember, event)) {
            return;
        }
        if (!ModerationUtils.handleHasPermissions("ban", Permission.BAN_MEMBERS, bot, author, guild,
                event)) {
            return;
        }
        if (!ModerationUtils.handleReason(reason, event)) {
            return;
        }

        int deleteHistoryDays = Math
            .toIntExact(Objects.requireNonNull(event.getOption(DELETE_HISTORY_OPTION)).getAsLong());

        // Ban the user, but only if not already banned
        guild.retrieveBan(targetUser).mapToResult().flatMap(alreadyBanned -> {
            if (alreadyBanned.isSuccess()) {
                return handleAlreadyBanned(alreadyBanned.get(), event);
            }

            return handleNotAlreadyBannedResponse(Objects
                .requireNonNull(alreadyBanned.getFailure()), event, guild, targetUser).orElseGet(
                        () -> banUser(targetUser, author, reason, deleteHistoryDays, guild, event));
        }).queue();
    }
}
