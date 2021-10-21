package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class BanHelperMethods {
    private static final Logger logger = LoggerFactory.getLogger(BanCommand.class);
    private static final Integer DELETE_HISTORY_MIN_DAYS = 1;
    private static final Integer DELETE_HISTORY_MAX_DAYS = 7;

    public static void deleteMessageHistory(int days, @NotNull SlashCommandEvent event) {
        if (days < DELETE_HISTORY_MIN_DAYS || days > DELETE_HISTORY_MAX_DAYS) {
            event
                .reply("The amount of days of the message history to delete must be between "
                        + DELETE_HISTORY_MIN_DAYS + " and " + DELETE_HISTORY_MAX_DAYS
                        + " , but was " + days + ".")
                .setEphemeral(true)
                .queue();
        }
    }

    public static void banGuild(Member user, String reason, int days,
            @NotNull SlashCommandEvent event) {
        Member author = Objects.requireNonNull(event.getMember());
        event.getGuild()
            .ban(user, days, reason)
            .flatMap(v -> event.reply(user.getUser().getAsTag() + " was banned by "
                    + author.getUser().getAsTag() + " for: " + reason))
            .queue();
    }

    public static void openPrivateChannel(long userId, String reason,
            @NotNull SlashCommandEvent event) {
        event.getJDA()
            .openPrivateChannelById(userId)
            .flatMap(channel -> channel.sendMessage(
                    """
                    Hey there, sorry to tell you but unfortunately you have been banned from the guild 'Together Java'. 
                    If you think this was a mistake, please contact a moderator or admin of the guild.
                    The ban reason is:  %s
                    """.formatted(reason)))
            .queue();
    }

    public static void logger(long authorNameId, long userNameId, int days, String reason) {
        logger.info(
                " '{}' banned the user '{}' and deleted the message history of the last '{}' days. Reason was '{}'",
                authorNameId, userNameId, days, reason);
    }
}
