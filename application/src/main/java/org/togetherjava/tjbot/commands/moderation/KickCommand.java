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


/**
 * <p>
 * The implemented command is {@code /kick @user reason}, upon which the bot will kick the user.
 */
public class KickCommand extends SlashCommandAdapter {
    //the logger
    private static final Logger logger = LoggerFactory.getLogger(KickCommand.class);
    /**
     * Creates an instance of the kick command.
     */
    public KickCommand() {
        super("Kick", "Use this command to kick a user", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, "user", "The user which you want to kick", true)
                .addOption(OptionType.STRING, "reason", "The reason of the kick", true);
    }

    /**
     * When triggered with {@code /kick @user reason}, the bot will respond will check if the user
     * has perms. Then it will check if itself has perms to kick. If it does it will check if the user is the user
     * is too powerful or not. If the user is not then bot will kick the user and reply with {@code Kicked User!}.
     *
     * @param event the corresponding event
     */
    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        // Used to get the member
        final Member member = event.getOption("user").getAsMember();

        //Used for the reason
        final String reason = event.getOption("reason").getAsString();

        // Checks if the author has perms
        if (!event.getMember().hasPermission(Permission.KICK_MEMBERS)) {
            event.reply(
                    "You do not have the required permissions to kick users from this server.")
                .queue();
            return;
        }

        // Checks if the bot has perms
        final Member selfMember = event.getGuild().getSelfMember();
        if (!selfMember.hasPermission(Permission.KICK_MEMBERS)) {
            event.reply(
                    "I don't have the required permissions to kick users from this server.")
                .queue();
            return;
        }

        // Check if the user can be kicked
        if (member != null && !selfMember.canInteract(member)) {
            event.reply("This user is too powerful for me to kick.").queue();
            return;
        }

        // Kicks the user and send a success response
        event.getGuild()
            .kick(member, reason)
            .flatMap(v -> event.reply("Kicked the user" + member.getUser().getAsTag()))
            .queue();
    }
}
