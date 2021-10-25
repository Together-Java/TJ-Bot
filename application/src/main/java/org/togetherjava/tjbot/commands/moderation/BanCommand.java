package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.util.Objects;

/**
 * This command can ban users and optionally remove their messages from the past days. Banning can
 * also be paired with a ban reason. The command will also try to DM the user to inform them about
 * the action and the reason.
 * <p>
 * The command fails if the user triggering it is lacking permissions to either ban other users or
 * to ban the specific given user (for example a moderator attempting to ban an admin).
 *
 */
public final class BanCommand extends SlashCommandAdapter {
    private static final String USER_OPTION = "user";
    private static final String DELETE_HISTORY_OPTION = "delete-history";
    private static final String REASON_OPTION = "reason";
    /**
     * As stated in {@link Guild#ban(User, int, String)} The reason can be only 512 characters.
     */
    private static final Integer REASON_MAX_LENGTH = 512;
    private static final Logger logger = LoggerFactory.getLogger(BanCommand.class);

    /**
     * Creates an instance of the ban command.
     */
    public BanCommand() {
        super("ban", "Bans the given user from the server", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, USER_OPTION, "The user who you want to ban", true)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be banned", true)
            .addOptions(new OptionData(OptionType.INTEGER, DELETE_HISTORY_OPTION,
                    "the amount of days of the message history to delete, none means no messages are deleted.",
                    true).addChoice("none", 0).addChoice("recent", 1).addChoice("all", 7));
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        Member user = Objects.requireNonNull(event.getOption(USER_OPTION)).getAsMember();
        Member author = Objects.requireNonNull(event.getMember());
        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION)).getAsString();
        Member bot = Objects.requireNonNull(event.getGuild()).getSelfMember();

        if (!author.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply(
                    "You can not ban users in this guild since you do not have the BAN_MEMBERS permission.")
                .setEphemeral(true)
                .queue();
            return;
        }

        long userId = user.getIdLong();
        if (!author.canInteract(user)) {
            event.reply("The user " + userid + " is too powerful for you to ban.")
                .setEphemeral(true)
                .queue();
            return;
        }

        if (!bot.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply(
                    "I can not ban users in this guild since I do not have the BAN_MEMBERS permission.")
                .setEphemeral(true)
                .queue();

            logger.error("The bot does not have BAN_MEMBERS permission on the server '{}' ",
                    Objects.requireNonNull(event.getGuild()));
            return;
        }
        if (!bot.canInteract(Objects.requireNonNull(user))) {
            event.reply("The user" + user.getUser().getAsTag() + "is too powerful for me to ban.")
                .setEphemeral(true)
                .queue();
            return;
        }

        int days = Math
            .toIntExact(Objects.requireNonNull(event.getOption(DELETE_HISTORY_OPTION)).getAsLong());

        if (reason.length() > BanCommand.REASON_MAX_LENGTH) {
            event.reply("The reason can not be over 512 characters").setEphemeral(true).queue();
            return;
        }

        banUser(user, author, reason, days, userId, author.getIdLong(), event);
    }

    private static void banUser(@NotNull Member member, @NotNull Member author,
            @NotNull String reason, int days, long userId, long authorId,
            @NotNull SlashCommandEvent event) {

        String guildName = event.getGuild().getName();
        event.getJDA()
            .openPrivateChannelById(userId)
            .flatMap(channel -> channel.sendMessage(
                    """
                            Hey there, sorry to tell you but unfortunately you have been banned from the guild %s.
                            If you think this was a mistake, please contact a moderator or admin of the guild.
                            The reason for the ban is: %s
                            """
                        .formatted(guildName, reason)))
            .queue(null,
                    throwable -> logger.info(
                            "I could not dm the user '{}' to inform them that they were banned.",
                            userId));

        event.getGuild()
            .ban(member, days, reason)
            .flatMap(v -> event.reply(member.getUser().getAsTag() + " was banned by "
                    + author.getUser().getAsTag() + " for: " + reason))
            .queue();

        logger.info(
                " '{}' banned the user '{}' and deleted their message history of the last '{}' days. Reason was '{}'",
                authorId, userId, days, reason);
    }
}
