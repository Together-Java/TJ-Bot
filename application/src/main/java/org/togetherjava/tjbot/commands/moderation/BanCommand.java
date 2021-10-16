package org.togetherjava.tjbot.commands.moderation;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

/**
 * @author RealYusufIsmail
 */
public class BanCommand extends SlashCommandAdapter {
    public BanCommand() {
        super("ban", "Use this command to ban a user", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, "ban", "The user which you want to ban", true)
                .addOption(OptionType.INTEGER, "del_days", "The delete message histroy", false);

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
        if (!event.getMember().hasPermission(Permission.BAN_MEMBERS))
        {
            hook.sendMessage("You do not have the required permissions to ban users from this server.").queue();
            return;
        }

        // Checks if the bot has perms
        Member selfMember = event.getGuild().getSelfMember();
        if (!selfMember.hasPermission(Permission.BAN_MEMBERS))
        {
            hook.sendMessage("I don't have the required permissions to ban users from this server.").queue();
            return;
        }


        // Check if the user can be banned
        if (member != null && !selfMember.canInteract(member))
        {
            hook.sendMessage("This user is too powerful for me to ban.").queue();
            return;
        }

        //Used to delete message history
        int delDays = 0;
        OptionMapping option = event.getOption("del_days");
        if (option != null) // null = not provided
            delDays = (int) Math.max(0, Math.min(7, option.getAsLong()));
        // Ban the user and send a success response
        event.getGuild().ban(member, delDays)
                .flatMap(v -> hook.sendMessage("Banned user " + member.getUser()))
                .queue();
    }
}
