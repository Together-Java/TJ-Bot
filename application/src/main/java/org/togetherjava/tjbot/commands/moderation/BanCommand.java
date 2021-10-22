package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
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
 * also be paired with a ban reason. The command will also try to DM the user to inform him about
 * the action and the reason.
 * <p>
 * The command fails if the user triggering it is lacking permissions to either ban other users or
 * to ban the specific given user (for example a moderator attempting to ban an admin).
 *
 */
public final class BanCommand extends SlashCommandAdapter {
    private static final String USER_OPTION = "user";
    private static final String DELETE_MESSAGE_HISTORY_DAYS_OPTION = "delete-message-history-days";
    private static final String REASON_OPTION = "reason";
    private static final Logger logger = LoggerFactory.getLogger(BanCommand.class);

    /**
     * Creates an instance of the ban command.
     */
    public BanCommand() {
        super("ban", "Bans a given user", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, USER_OPTION, "The user who you want to ban", true)
            .addOption(OptionType.STRING, REASON_OPTION, "why the user should be banned", true)
            .addOptions(new OptionData(OptionType.INTEGER, DELETE_MESSAGE_HISTORY_DAYS_OPTION,
                    "the amount of days of the message history to delete, otherwise no messages are deleted.",
                    true).addChoice("none", 0)
                        .addChoice("1", 1)
                        .addChoice("2", 2)
                        .addChoice("3", 3)
                        .addChoice("4", 4)
                        .addChoice("5", 5)
                        .addChoice("6", 6)
                        .addChoice("7", 7));
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        Member user = Objects.requireNonNull(event.getOption(USER_OPTION)).getAsMember();
        Member author = Objects.requireNonNull(event.getMember());
        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION)).getAsString();

        long userId = user.getUser().getIdLong();

        if (!author.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("You do not have the BAN_MEMBERS permission to ban users from this server.")
                .setEphemeral(true)
                .queue();
            return;
        }

        if (!author.canInteract(Objects.requireNonNull(user))) {
            event.reply("This user is too powerful for you to ban.").setEphemeral(true).queue();
            return;
        }

        Member bot = Objects.requireNonNull(event.getGuild()).getSelfMember();
        if (!bot.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("I don't have the BAN_MEMBERS permission to ban users from this server.")
                .setEphemeral(true)
                .queue();
            return;
        }

        if (!bot.canInteract(Objects.requireNonNull(user))) {
            event.reply("This user is too powerful for me to ban.").setEphemeral(true).queue();
            return;
        }

        long authorId = author.getIdLong();
        // TODO Implement the same delete message structure for the purge message.
        int days = Math
            .toIntExact(Objects.requireNonNull(event.getOption(DELETE_MESSAGE_HISTORY_DAYS_OPTION))
                .getAsLong());

        banUser(userId, authorId, user, reason, days, event);
    }

    private static void banUser(long userId, long authorNameId, Member user, String reason,
            int days, @NotNull SlashCommandEvent event) {
        event.getJDA()
            .openPrivateChannelById(userId)
            .flatMap(channel -> channel.sendMessage(
                    """
                            Hey there, sorry to tell you but unfortunately you have been banned from the guild 'Together Java'.\040
                            If you think this was a mistake, please contact a moderator or admin of the guild.
                            The ban reason is:  %s
                            """
                        .formatted(reason)))
            .queue();

        Member author = Objects.requireNonNull(event.getMember());

        event.getGuild()
            .ban(user, days, reason)
            .flatMap(v -> event.reply(user.getUser().getAsTag() + " was banned by "
                    + author.getUser().getAsTag() + " for: " + reason))
            .queue();

        logger.info(
                " '{}' banned the user '{}' and deleted the message history of the last '{}' days. Reason was '{}'",
                authorNameId, userId, days, reason);
    }
}
