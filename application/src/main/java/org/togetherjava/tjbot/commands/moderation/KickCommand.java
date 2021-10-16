package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

/**
 * @author RealYusufIsmail
 */
public class KickCommand extends SlashCommandAdapter {
    public KickCommand() {
        super("Kick", "Use this command to kick a user", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, "user", "The user which you want to kick", true);
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        // Used to get the member
        final Member member = event.getOption("user").getAsMember();

        event.deferReply(true).queue(); // Let the user know we received the command before doing
                                        // anything else
        InteractionHook hook = event.getHook(); // This is a special webhook that allows you to send
                                                // messages without having permissions in the
                                                // channel and also allows ephemeral messages
        hook.setEphemeral(true); // All messages here will now be ephemeral implicitly

        // Checks if the author has perms
        if (!event.getMember().hasPermission(Permission.KICK_MEMBERS)) {
            hook.sendMessage(
                    "You do not have the required permissions to kick users from this server.")
                .queue();
            return;
        }

        // Checks if the bot has perms
        Member selfMember = event.getGuild().getSelfMember();
        if (!selfMember.hasPermission(Permission.KICK_MEMBERS)) {
            hook.sendMessage(
                    "I don't have the required permissions to kick users from this server.")
                .queue();
            return;
        }

        // Check if the user can be kicked
        if (member != null && !selfMember.canInteract(member)) {
            hook.sendMessage("This user is too powerful for me to kick.").queue();
            return;
        }

        // Kicks the user and send a success response
        event.getGuild()
            .kick(member)
            .flatMap(v -> hook.sendMessage("Kicked the user" + member.getUser()))
            .queue();
    }
}
