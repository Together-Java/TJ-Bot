package org.togetherjava.tjbot.commands.example;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandCreateAction;
import org.togetherjava.tjbot.commands.ICommand;

/**
 * Example command
 *
 */
public class CommandExample implements ICommand {

    /**
     * The command name is example, so it returns example.
     *
     * @return
     *  {@link String} with the command's name
     */
    @Override
    public String getCommandName() {
        return "example";
    }

    /**
     * The description
     *
     * @return
     *  A {@link String} with the description
     */
    @Override
    public String getDescription() {
        return "Example command! Allows you to spam ping someone!";
    }

    /**
     * You can make commands guild only by overriding this method.
     * <br> You can only ping people in a guild, so this command is guild only.
     *
     * @return
     *  {@link Boolean} of {@code true}
     */
    @Override
    public boolean isGuildOnly() {
        return true;
    }

    /**
     * Adds the "User" and "Amount of pings" option.
     * <br> This is for the GUI, so the user sees what options it has.
     *
     * @param commandData
     *  The thing we will add our options to!
     * @return
     *  A {@link CommandCreateAction} with the required options for this command
     */
    @Override
    public CommandData addOptions(CommandData commandData) {
        return commandData.addOptions(
                // * add the first option
                new OptionData(OptionType.USER, "user", "User that will be pinged!", true),
                // * adds the second option
                new OptionData(OptionType.STRING, "times-to-ping", "Amount of times the user will be pinged, default 1").addChoices(
                        new net.dv8tion.jda.api.interactions.commands.Command.Choice("Once", "1"),
                        new net.dv8tion.jda.api.interactions.commands.Command.Choice("Twice", "2")
                ).setRequired(true)
        );
    }

    /**
     * The execute method!
     * <br> Here it loads the options the users made.
     * <br> After that it pings the user ... times.
     * <br> And it calls the person who ran the command out, spammer they are!
     *
     * @param event
     *  A {@link SlashCommandEvent} to respond to.
     */
    @Override
    public void onSlashCommand(SlashCommandEvent event) {

        // * gets the option as a user
        User userToMention = event.getOption("user").getAsUser();
        int timesToPing = Integer.parseInt(event.getOption("times-to-ping").getAsString());

        event.reply("It'll only ping once, you spammer!").setEphemeral(true).queue();
        event.getChannel().sendMessage(userToMention.getAsMention().repeat(timesToPing)).queue();
    }
}
