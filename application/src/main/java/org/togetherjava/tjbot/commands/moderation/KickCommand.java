package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import java.util.Objects;


/**
 * This command can kicks users. Kicking can also be paired with a kick reason. The command will
 * also try to DM the user to inform them about the action and the reason.
 * <p>
 * The command fails if the user triggering it is lacking permissions to either kick other users or
 * to kick the specific given user (for example a moderator attempting to kick an admin).
 *
 */
public final class KickCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(KickCommand.class);
    private static final String TARGET_OPTION = "user";
    private static final String REASON_OPTION = "reason";
    private static final String COMMAND_NAME = "kick";
    private static final String ACTION_VERB = "kick";

    /**
     * Constructs an instance.
     */
    public KickCommand() {
        super(COMMAND_NAME, "Kicks the given user from the server", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, TARGET_OPTION, "The user who you want to kick", true)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be kicked", true);
    }

    private static void handleAbsentTarget(@NotNull Interaction event) {
        event.reply("I can not kick the given user since they are not part of the guild anymore.")
            .setEphemeral(true)
            .queue();
    }

    private static void kickUser(@NotNull Member target, @NotNull Member author,
            @NotNull String reason, @NotNull Guild guild, @NotNull SlashCommandEvent event) {
        User targetUser = target.getUser();
        event.getJDA()
            .openPrivateChannelById(targetUser.getId())
            .flatMap(channel -> channel.sendMessage(
                    """
                            Hey there, sorry to tell you but unfortunately you have been kicked from the server %s.
                            If you think this was a mistake, please contact a moderator or admin of the server.
                            The reason for the kick is: %s
                            """
                        .formatted(guild.getName(), reason)))
            .mapToResult()
            .flatMap(sendDmResult -> {
                logger.info("'{}' ({}) kicked the user '{}' ({}) from guild '{}' for reason '{}'.",
                        author.getUser().getAsTag(), author.getId(), targetUser.getAsTag(),
                        targetUser.getId(), guild.getName(), reason);

                return guild.kick(target, reason)
                    .reason(reason)
                    .map(kickResult -> sendDmResult.isSuccess());
            })
            .map(hasSentDm -> {
                String dmNotice =
                        Boolean.TRUE.equals(hasSentDm) ? "" : "(Unable to send them a DM.)";
                return ModerationUtils.createActionResponse(author.getUser(),
                        ModerationUtils.Action.KICK, targetUser, dmNotice, reason);
            })
            .flatMap(event::replyEmbeds)
            .queue();
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        Member target = Objects.requireNonNull(event.getOption(TARGET_OPTION), "The target is null")
            .getAsMember();
        Member author = Objects.requireNonNull(event.getMember(), "The author is null");
        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION), "The reason is null")
            .getAsString();

        Guild guild = Objects.requireNonNull(event.getGuild());
        Member bot = guild.getSelfMember();

        // Member doesn't exist if attempting to kick a user who is not part of the guild anymore.
        if (target == null) {
            handleAbsentTarget(event);
            return;
        }
        if (!ModerationUtils.handleCanInteractWithTarget(ACTION_VERB, bot, author, target, event)) {
            return;
        }
        if (!ModerationUtils.handleHasPermissions(ACTION_VERB, Permission.KICK_MEMBERS, bot, author,
                guild, event)) {
            return;
        }
        if (!ModerationUtils.handleReason(reason, event)) {
            return;
        }

        kickUser(target, author, reason, guild, event);
    }
}
