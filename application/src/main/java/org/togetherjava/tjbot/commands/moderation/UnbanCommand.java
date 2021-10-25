package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
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
 * This command can unban users.
 * <p>
 * The command fails if the user triggering it is lacking permissions to either unban other users.
 */
public final class UnbanCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(UnbanCommand.class);
    private static final String USER_ID = "user_id";

    /**
     * Creates an instance of the ban command.
     */
    public UnbanCommand() {
        super("unban", "Unbans a given user", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.STRING, USER_ID,
                "The user id of the user which you want to unban", true);

    }

    /**
     * When triggered with {@code /unban user_id}}, the bot will respond will check if the user has
     * perms. Then it will check if itself has perms to unban. If it does then it will check if the
     * user is on the ban list. If the user is on the ban list it will unban the user.
     *
     * @param event the corresponding event
     */
    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        final String userId = Objects.requireNonNull(event.getOption(USER_ID)).getAsString();

        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.BAN_MEMBERS)) {
            event.reply(
                    "I can not unban users in this guild since I do not have the BAN_MEMBERS permission.")
                .setEphemeral(true)
                .queue();
            return;
        }

        if (!(Objects.requireNonNull(event.getGuild())).getSelfMember()
            .hasPermission(Permission.BAN_MEMBERS)) {
            event.reply(
                    "I can not unban users in this guild since I do not have the BAN_MEMBERS permission.")
                .setEphemeral(true)
                .queue();

            logger.error("The bot does not have BAN_MEMBERS permission on the server '{}' ",
                    event.getGuild().getId());
            return;
        }

        event.getGuild().unban(userId).queue(v -> {
            event.reply("Unbanned the user").queue();
            User user = event.getUser();
            logger.info(" {} ({}) unbanned user id '{}' ", user.getAsTag(), user.getIdLong(),
                    userId);
        }, throwable -> {
            if (throwable instanceof ErrorResponseException errorResponseException
                    && errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {

                event.reply("the specified user doesn't exist").queue();
                logger.debug("The user '{}' does not exist", userId);
            } else {
                event.reply("Something went wrong, check the logs or contact a staff member")
                    .queue();

                logger.error("Something went wrong during the process of unbanning the user ",
                        throwable);
            }
        });
    }
}
