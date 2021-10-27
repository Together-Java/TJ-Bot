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
 * The command fails if the user is not banned or the incorrect id was input.
 */
public final class UnbanCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(UnbanCommand.class);
    private static final String USER = "user";
    private static final String REASON = "reason";

    /**
     * Creates an instance of the unban command.
     */
    public UnbanCommand() {
        super("unban", "Unbans a given user", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, USER, "The user who you want to unban", true)
            .addOption(OptionType.STRING, REASON, "Why the user should be unbanned", true);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        User user = event.getOption(USER).getAsUser();
        Member author = Objects.requireNonNull(event.getMember(), "Member is null);

        if (author != null && !author.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply(
                            "You can not unban users in this guild since you do not have the BAN_MEMBERS permission.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Member bot = event.getGuild().getSelfMember();
        if (!bot.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply(
                    "I can not unban users in this guild since I do not have the BAN_MEMBERS permission.")
                .setEphemeral(true)
                .queue();

            logger.error("The bot does not have BAN_MEMBERS permission on the server '{}' ",
                    Objects.requireNonNull(event.getGuild()).getName());
            return;
        }

        String reason = event.getOption(REASON).getAsString();
        event.getGuild().unban(user).queue(v -> {
            event
                .reply("The user " + author.getUser().getAsTag() + " unbanned the user "
                        + user.getAsTag() + " for: " + reason)
                .queue();
            logger.info(" {} ({}) unbanned the user '{}' for: '{}'", author.getUser().getAsTag(),
                    author.getIdLong(), user.getAsTag(), reason);
        }, throwable -> {
            if (throwable instanceof ErrorResponseException errorResponseException
                    && errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {

                event.reply("The specified user doesn't exist").setEphemeral(true).queue();

                logger.debug("I could not unban the user '{}' because they do not exist",
                        user.getAsTag());
            } else {
                event.reply("Sorry, but something went wrong.").setEphemeral(true).queue();

                logger.warn("Something went wrong during the process of unbanning the user ",
                        throwable);
            }
        });
    }
}
