package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
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
    private static final String USER_OPTION = "user";
    private static final String REASON_OPTION = "reason";
    /**
     * As stated in {@link Guild#ban(User, int, String)} The reason can be only 512 characters.
     */
    private static final Integer REASON_MAX_LENGTH = 512;

    /**
     * Creates an instance of the kick command.
     */
    public KickCommand() {
        super("kick", "Kicks the given user from the server", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, USER_OPTION, "The user who you want to kick", true)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be kicked", true);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        Member targetMember =
                Objects.requireNonNull(event.getOption(USER_OPTION), "The member is null")
                    .getAsMember();
        Member author = Objects.requireNonNull(event.getMember(), "Author is null");
        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION), "The reason is null")
            .getAsString();
        Member bot = Objects.requireNonNull(event.getGuild(), "The bot is null").getSelfMember();

        if (!author.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply(
                    "You can not kick users in this guild since you do not have the KICK_MEMBERS permission.")
                .setEphemeral(true)
                .queue();
            return;
        }

        String userTag = targetMember.getUser().getAsTag();
        if (!author.canInteract(targetMember)) {
            event.reply("The user " + userTag + " is too powerful for you to kick.")
                .setEphemeral(true)
                .queue();
            return;
        }

        Guild guild = event.getGuild();
        if (!bot.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply(
                    "I can not kick users in this guild since I do not have the KICK_MEMBERS permission.")
                .setEphemeral(true)
                .queue();

            logger.error("The bot does not have KICK_MEMBERS permission on the server '{}' ",
                    Objects.requireNonNull(guild).getName());
            return;
        }

        if (!bot.canInteract(targetMember)) {
            event.reply("The user " + userTag + " is too powerful for me to kick.")
                .setEphemeral(true)
                .queue();
            return;
        }

        if (reason.length() > REASON_MAX_LENGTH) {
            event.reply("The reason can not be over " + REASON_MAX_LENGTH + " characters")
                .setEphemeral(true)
                .queue();
            return;
        }

        kickUser(targetMember, author, reason, guild, event);
    }

    private static void kickUser(@NotNull Member member, @NotNull Member author,
            @NotNull String reason, @NotNull Guild guild, @NotNull SlashCommandEvent event) {
        String guildName = guild.getName();
        event.getJDA()
            .openPrivateChannelById(member.getUser().getId())
            .flatMap(channel -> channel.sendMessage(
                    """
                            Hey there, sorry to tell you but unfortunately you have been kicked from the server %s.
                            If you think this was a mistake, please contact a moderator or admin of the server.
                            The reason for the kick is: %s
                            """
                        .formatted(guildName, reason)))
            .mapToResult()
            .flatMap(result -> guild.kick(member, reason).reason(reason))
            .flatMap(v -> event.reply(member.getUser().getAsTag() + " was kicked by "
                    + author.getUser().getAsTag() + " for: " + reason))
            .queue();

        logger.info(" '{} ({})' kicked the user '{} ({})' due to reason being '{}'",
                author.getUser().getAsTag(), author.getIdLong(), member.getUser().getAsTag(),
                member.getUser().getId(), reason);
    }
}
