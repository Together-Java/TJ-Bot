package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;
import java.util.Objects;


/**
 * When triggered with {@code /kick @user reason}, the bot will check if the user has perms. Then it
 * will check if itself has perms to kick. If it does it will check if the user is too powerful or
 * not. If the user is not then bot will kick the user and reply with {@code Kicked User!}.
 */
public final class KickCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(KickCommand.class);
    private static final String USER_OPTION = "user";
    private static final String REASON_OPTION = "reason";

    /**
     * Creates an instance of the kick command.
     */
    public KickCommand() {
        super("kick", "Kicks the given user from the user", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, USER_OPTION, "The user who you want to kick", true)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be kicked", true);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        Member user = Objects.requireNonNull(event.getOption(USER_OPTION)).getAsMember();
        Member author = Objects.requireNonNull(event.getMember());
        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION)).getAsString();
        Member bot = Objects.requireNonNull(event.getGuild()).getSelfMember();

        if (!author.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply(
                    "You can not kick users in this guild since you do not have the KICK_MEMBERS permission.")
                .setEphemeral(true)
                .queue();
            return;
        }

        if (!author.canInteract(Objects.requireNonNull(user))) {
            event.reply("The user" + user + "is too powerful for you to kick.")
                .setEphemeral(true)
                .queue();
            return;
        }

        if (!bot.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply(
                    "I can not kick users in this guild since I do not have the KICK_MEMBERS permission.")
                .setEphemeral(true)
                .queue();

            logger.error("The bot does not have KICK_MEMBERS permission on the server '{}' ",
                    Objects.requireNonNull(event.getGuild().getId()));
            return;
        }

        if (!bot.canInteract(user)) {
            event.reply("The user" + user + "is too powerful for me to kick.")
                .setEphemeral(true)
                .queue();

            logger.info("The bot does not have enough permissions to kick '{}'",
                    user.getUser().getAsTag());
            return;
        }

        kickUser(user, reason, user.getUser().getIdLong(), event);
    }

    public static void kickUser(@NotNull Member user, @NotNull String reason, long userId,
            @NotNull SlashCommandEvent event) {
        String guildName = event.getGuild().getName();

        event.getJDA()
            .openPrivateChannelById(userId)
            .flatMap(channel -> channel.sendMessage(
                    """
                            Hey there, sorry to tell you but unfortunately you have been kicked from the guild %s.
                            If you think this was a mistake, please contact a moderator or admin of the guild.
                            The kick reason is: %s
                                    """.formatted(guildName , reason))
            .queue();

        event.getGuild()
            .kick(user, reason)
            .flatMap(v -> event.reply(user.getUser().getAsTag() + " was kicked by "
                    + user.getUser().getAsTag() + " for: " + reason))
            .queue();

        logger.info(" '{} ({})' kicked the user '{} ({})' due to reason being '{}'",
                user.getUser().getAsTag(), user.getIdLong(), user.getUser().getAsTag(), userId,
                reason);
    }
}
