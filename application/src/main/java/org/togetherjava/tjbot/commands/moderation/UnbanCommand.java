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
 * This command allows you to unban a user. The unban command requires the user to input the user
 * who is subject to get unbanned. Unbanning can also be paired with a reason for unbanning the
 * user. The command fails if the user is currently not banned already.
 */
public final class UnbanCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(UnbanCommand.class);
    private static final String USER_OPTION = "user";
    private static final String REASON_OPTION = "reason";

    /**
     * Constructs an instance.
     */
    public UnbanCommand() {
        super("unban", "Unbans a given user", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, USER_OPTION, "The user who you want to unban", true)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be unbanned", true);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        User target = Objects.requireNonNull(event.getOption(USER_OPTION), "The user is null")
            .getAsUser();
        Member author = Objects.requireNonNull(event.getMember(), "The author is null");

        if (!author.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply(
                    "You can not unban users in this guild since you do not have the BAN_MEMBERS permission.")
                .setEphemeral(true)
                .queue();
            return;
        }

        Member bot = Objects.requireNonNull(event.getGuild(), "The guild is null").getSelfMember();
        if (!bot.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply(
                    "I can not unban users in this guild since I do not have the BAN_MEMBERS permission.")
                .setEphemeral(true)
                .queue();

            logger.error("The bot does not have BAN_MEMBERS permission on the server '{}' ",
                    event.getGuild().getName());
            return;
        }

        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION), "The reason is null")
            .getAsString();

        if (!ModerationUtils.handleReason(reason, event)) {
            return;
        }

        unban(target, reason, author, event);
    }

    private static void unban(@NotNull User target, @NotNull String reason, @NotNull Member author,
            @NotNull SlashCommandEvent event) {
        event.getGuild().unban(target).reason(reason).queue(v -> {
            event
                .reply("The user " + author.getUser().getAsTag() + " unbanned the user "
                        + target.getAsTag() + " for: " + reason)
                .queue();

            logger.info(" {} ({}) unbanned the user '{}' for: '{}'", author.getUser().getAsTag(),
                    author.getIdLong(), target.getAsTag(), reason);
        }, throwable -> {
            if (throwable instanceof ErrorResponseException errorResponseException
                    && errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {

                event.reply("The specified user doesn't exist").setEphemeral(true).queue();

                logger.debug("I could not unban the user '{}' because they do not exist",
                        target.getAsTag());
            } else {
                event.reply("Sorry, but something went wrong.").setEphemeral(true).queue();

                logger.warn("Something went wrong during the process of unbanning the user ",
                        throwable);
            }
        });
    }
}
