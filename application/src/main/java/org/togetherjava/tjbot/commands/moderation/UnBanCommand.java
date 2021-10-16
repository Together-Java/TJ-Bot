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
 * The implemented command is {@code /unban user_id}, upon which the
 * bot will unban the user.
 */
public class UnBanCommand extends SlashCommandAdapter {
    // the logger
    private static final Logger logger = LoggerFactory.getLogger(BanCommand.class);

    /**
     * Creates an instance of the ban command.
     */
    public UnBanCommand() {
        super("unban", "Use this command to unban a user", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.STRING, "user_id", "The user if of the user which you want to unban",
                true);

    }

    /**
     * When triggered with {@code /unban user_id}}, the bot will respond will check if
     * the user has perms. Then it will check if itself has perms to unban. If it does it will check
     * if the user is the user is too powerful or not. If the user is not then unban will ban the
     * user.
     *
     * @param event the corresponding event
     */
    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        final String userId = Objects.requireNonNull(event.getOption("user_id")).getAsString();

        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.BAN_MEMBERS))
        {
            event.reply("You do not have the required permissions to ban users from this server.").queue();
            return;
        }

        Member author = Objects.requireNonNull(event.getGuild()).getSelfMember();
        if (!author.hasPermission(Permission.BAN_MEMBERS))
        {
            event.reply("I don't have the required permissions to unban the user from this server.").queue();
            return;
        }

        // Add this to audit log
        logger.info("User '{}' unbanned user id '{}'", author, userId);

        //Unbans the user
        event.getGuild().unban(userId)
                .flatMap(v -> event.reply("Unbanned the user"))
                .queue();
    }
}
