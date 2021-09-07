package org.togetherjava.tjbot.commands.example;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandCreateAction;
import org.togetherjava.tjbot.commands.AbstractCommand;

/**
 * Example command
 */
public class ExampleCommand extends AbstractCommand {

    /**
     * The command name is example, so it returns example.
     * @return
     *  "Example" as a {@link String}
     */
    @Override
    public String getCommandName() {
        return "example";
    }

    /**
     * The description
     * @return
     *  "Example command! Allows you to spam ping someone!" as a {@link String}
     */
    @Override
    public String getDescription() {
        return "Example command! Allows you to spam ping someone!";
    }

    /**
     * You can make commands guild only by overriding this method.
     * You can only ping people in a guild, so this command is guild only.
     * @return
     *  "true" as a {@link Boolean}
     */
    @Override
    public boolean isGuildOnly() {
        return true;
    }

    /**
     * Adds the "User" and "Amount of pings" option.
     * This is for the GUI, so the user sees what options it may give.
     * @param commandCreateAction
     *  The thing we will add our options to!
     * @return
     *  A {@link CommandCreateAction} with the required options for this command
     */
    @Override
    public CommandCreateAction addOptions(CommandCreateAction commandCreateAction) {
        return commandCreateAction.addOptions(
                new OptionData(OptionType.USER, "User", "User that will be pinged!", true),
                new OptionData(OptionType.STRING, "Amount of pings", "Amount of times the user will be pinged, default 1").addChoices(
                        new Command.Choice("Once", "1"),
                        new Command.Choice("Twice", "2")
                ).setRequired(true)
        );
    }

    /**
     * The execute method.
     * Here it loads the options the users made.
     * After that it pings the user ... times.
     * And it calls the person who ran the command out, spammer they are!
     * @param event
     *  A {@link SlashCommandEvent} to respond to.
     */
    @Override
    public void execute(SlashCommandEvent event) {

        User userToMention = event.getOption("User").getAsUser();
        int timesToPing = Integer.parseInt(event.getOption("Amount of pings").getAsString());

        event.reply("It'll only ping once, you spammer!").setEphemeral(true).queue();
        event.getChannel().sendMessage(userToMention.getAsMention().repeat(timesToPing)).queue();
    }
}
