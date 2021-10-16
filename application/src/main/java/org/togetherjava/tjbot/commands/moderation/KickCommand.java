package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;


public class KickCommand extends SlashCommandAdapter {
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
     * is too powerful or not. If the user is not then bot will kick the user.
     *
     * @param event the corresponding event
     */
    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        // Used to get the member
        Member member = event.getOption("user").getAsMember();

        //Used for the reason
        String reason = event.getOption("reason").getAsString();

        // Let the user know we received the command before doing anything else
        event.deferReply(true).queue();

        // This is a special webhook that allows you to send
        // messages without having permissions in the
        // channel and also allows ephemeral messages
        InteractionHook hook = event.getHook();

        // All messages here will now be ephemeral implicitly
        hook.setEphemeral(true);


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
            .kick(member, reason)
            .flatMap(v -> event.reply("Kicked the user" + member.getUser().getAsTag()))
            .queue();
    }
}
