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
 * <p>
 * The implemented command is {@code /unban user_id}, upon which the bot will unban the user.
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
     * perms. Then it will check if itself has perms to unban. If it does it will check if the user
     * is the user is too powerful or not. If the user is not then unban will ban the user.
     *
     * @param event the corresponding event
     */
    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        final String userId = Objects.requireNonNull(event.getOption(USER_ID)).getAsString();

        Member author = Objects.requireNonNull(event.getMember());

        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.BAN_MEMBERS)) {
            event.reply(
                    "You do not have the BAN_MEMBERS permission to urban users from this server.")
                .setEphemeral(true)
                .queue();
            return;
        }

        Member bot = Objects.requireNonNull(event.getGuild()).getSelfMember();
        if (!bot.hasPermission(Permission.BAN_MEMBERS)) {
            event.reply(
                    "I don't have the BAN_MEMBERS permission to unban the user from this server.")
                .setEphemeral(true)
                .queue();
            return;
        }

        logger.error("The user '{}' does not exist", userId);
        event.getGuild().unban(userId).flatMap(v -> event.reply("Unbanned the user")).queue();

        logger.info(" '{}' unbanned user id '{}' ", author.getIdLong(), userId);
    }
}
