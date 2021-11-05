package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

import java.util.Objects;

/**
 * Unbans a given user. Unbanning can also be paired with a reason. The command fails if the user is
 * currently not banned.
 */
public final class UnbanCommand extends SlashCommandAdapter {
    private static final Logger logger = LoggerFactory.getLogger(UnbanCommand.class);
    private static final String TARGET_OPTION = "user";
    private static final String REASON_OPTION = "reason";

    /**
     * Constructs an instance.
     */
    public UnbanCommand() {
        super("unban", "Unbans the given user from the server", SlashCommandVisibility.GUILD);

        getData()
            .addOption(OptionType.USER, TARGET_OPTION, "The banned user who you want to unban",
                    true)
            .addOption(OptionType.STRING, REASON_OPTION, "Why the user should be unbanned", true);
    }

    private static void unban(@NotNull User target, @NotNull Member author, @NotNull String reason,
            @NotNull Guild guild, @NotNull Interaction event) {
        String targetTag = target.getAsTag();

        guild.unban(target).reason(reason).queue(result -> {
            MessageEmbed message = ModerationUtils.createActionResponse(author.getUser(),
                    ModerationUtils.Action.UNBAN, target, null, reason);
            event.replyEmbeds(message).queue();

            logger.info("'{}' ({}) unbanned the user '{}' ({}) from guild '{}' for reason '{}'.",
                    author.getUser().getAsTag(), author.getId(), targetTag, target.getId(),
                    guild.getName(), reason);
        }, unbanFailure -> {
            if (unbanFailure instanceof ErrorResponseException errorResponseException) {
                if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_USER) {
                    event.reply("The specified user does not exist.").setEphemeral(true).queue();
                    logger.debug("Unable to unban the user '{}' because they do not exist.",
                            targetTag);
                    return;
                }
                if (errorResponseException.getErrorResponse() == ErrorResponse.UNKNOWN_BAN) {
                    event.reply("The specified user is not banned.").setEphemeral(true).queue();
                    logger.debug("Unable to unban the user '{}' because they are not banned.",
                            targetTag);
                    return;
                }
            }
            event.reply("Sorry, but something went wrong.").setEphemeral(true).queue();
            logger.warn("Something unexpected went wrong while trying to unban the user '{}'.",
                    targetTag, unbanFailure);
        });
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        User target = Objects.requireNonNull(event.getOption(TARGET_OPTION), "The target is null")
            .getAsUser();
        Member author = Objects.requireNonNull(event.getMember(), "The author is null");
        String reason = Objects.requireNonNull(event.getOption(REASON_OPTION), "The reason is null")
            .getAsString();

        Guild guild = Objects.requireNonNull(event.getGuild());
        Member bot = guild.getSelfMember();

        if (!ModerationUtils.handleHasPermissions("unban", Permission.BAN_MEMBERS, bot, author,
                guild, event)) {
            return;
        }
        if (!ModerationUtils.handleReason(reason, event)) {
            return;
        }

        unban(target, author, reason, guild, event);
    }
}
