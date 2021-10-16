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


public class BanCommand extends SlashCommandAdapter {
    /**
     * Creates an instance of the ban command.
     */
    public BanCommand() {
        super("ban", "Use this command to ban a user", SlashCommandVisibility.GUILD);

        getData().addOption(OptionType.USER, "user", "The user which you want to ban", true)
            .addOption(OptionType.INTEGER, "del_days", "The delete message history", true)
                .addOption(OptionType.STRING, "reason", "The reason of the ban", true);


    }

    /**
     * When triggered with {@code /ban del_days @user reason}, the bot will respond will check if the user
     * has perms. Then it will check if itself has perms to ban. If it does it will check if the user is the user
     * is too powerful or not. If the user is not then bot will ban the user.
     *
     * @param event the corresponding event
     */
    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        // Used to get the member
        Member member = event.getOption("user").getAsMember();

        //Used for the reason of the ban
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
        if (!event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
            hook.sendMessage(
                    "You do not have the required permissions to ban users from this server.")
                .queue();
            return;
        }

        // Checks if the bot has perms
        Member selfMember = event.getGuild().getSelfMember();
        if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
            hook.sendMessage("I don't have the required permissions to ban users from this server.")
                .queue();
            return;
        }


        // Check if the user can be banned
        if (member != null && !selfMember.canInteract(member)) {
            hook.sendMessage("This user is too powerful for me to ban.").queue();
            return;
        }

        // Used to delete message history
        int delDays = 0;

        OptionMapping option = event.getOption("del_days");

        // null = not provided
        if (option != null) {
            hook.sendMessage("You have not provided a number");
            delDays = (int) Math.max(0, Math.min(7, option.getAsLong()));
        }

        // Ban the user and send a success response
        event.getGuild()
            .ban(member, delDays, reason)
            .flatMap(v -> event.reply("Banned the user " + member.getUser().getAsTag()))
            .queue();
    }
}
