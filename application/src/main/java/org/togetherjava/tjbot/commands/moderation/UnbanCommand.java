package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.util.Objects;

/**
 * This command can unban users. This command requires the user to input the id of the user they
 * want to unban.
 * <p>
 * The command fails if the user is not banned or the incorrect id was inputted.
 */
public final class UnbanCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(UnbanCommand.class);
    private static final String USER_ID = "user_id";

    /**
     * Creates an instance of the unban command.
     */
    public UnbanCommand() {
        super("unban", "Unbans a given user", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.STRING, USER_ID, "The id of the user who you want to unban",
                true);

    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        String userId = Objects.requireNonNull(event.getOption(USER_ID)).getAsString();
        Member author = Objects.requireNonNull(event.getMember());

        if (!author.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply(
                    "You can not unban users in this guild since you do not have the BAN_MEMBERS permission.")
                .setEphemeral(true)
                .queue();
            return;
        }

        Member bot = Objects.requireNonNull(event.getGuild()).getSelfMember();
        if (!bot.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply(
                    "I can not unban users in this guild since I do not have the BAN_MEMBERS permission.")
                .setEphemeral(true)
                .queue();

            logger.error("The bot does not have BAN_MEMBERS permission on the server '{}' ",
                    event.getGuild().getId());
            return;
        }

        event.getGuild().unban(userId).queue(v -> {
            User user = event.getUser();
            event
                .reply("The user " + author.getUser().getAsTag() + " unbanned the user "
                        + user.getAsTag())
                .queue();
            logger.info(" {} ({}) unbanned user id '{}' ", user.getAsTag(), user.getIdLong(),
                    userId);
        }, throwable -> {
            if (throwable instanceof ErrorResponseException errorResponseException
                    && errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {

                event.reply("The specified user doesn't exist").queue();
                logger.debug("I could not unban the user '{}' because they do not exist", userId);
            } else {
                event.reply(
                        "Sorry, but something went wrong, please check the logs or contact a staff member")
                    .setEphemeral(true)
                    .queue();

                logger.warn("Something went wrong during the process of unbanning the user ",
                        throwable);
            }
        });
    }
}
