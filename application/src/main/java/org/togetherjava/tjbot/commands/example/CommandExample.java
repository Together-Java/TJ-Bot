package org.togetherjava.tjbot.commands.example;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.api.interactions.components.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.Component;
import net.dv8tion.jda.api.requests.restaction.CommandCreateAction;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Example command
 *
 */
public class CommandExample extends AbstractCommand {

    /**
     * Parses the command name and the description to the super's constructor, both as a
     * {@link String} <br>
     * Also includes the isGuildOnly check, so this is also parsed to the constructor, as a
     * {@link Boolean}
     */
    public CommandExample() {
        super("example", "Example command! Allows you to spam ping someone!", true);
    }

    /**
     * Adds the "User" and "Amount of pings" option. <br>
     * This is for the GUI, so the user sees what options it has.
     *
     * @param commandData The thing we will add our options to!
     * @return A {@link CommandCreateAction} with the required options for this command
     */
    @Override
    public @NotNull CommandData addOptions(@NotNull CommandData commandData) {
        return commandData.addOptions(
                // * add the first option
                new OptionData(OptionType.USER, "user", "User that will be pinged!", true),
                // * adds the second option
                new OptionData(OptionType.STRING, "times-to-ping",
                        "Amount of times the user will be pinged, default 1")
                            .addChoices(
                                    new Command.Choice(
                                            "Once", "1"),
                                    new Command.Choice(
                                            "Twice", "2"))
                            .setRequired(true));
    }

    /**
     * The execute method! <br>
     * Here it loads the options the users made. <br>
     * After that it sends a reply with a {@link Button} asking the user, or they're sure <br>
     * It generates a component ID using one of the {@link org.togetherjava.tjbot.commands.Command} methods, this makes sure that
     * the {@link #onButtonClick(ButtonClickEvent, List)} runs.
     *
     * @param event A {@link SlashCommandEvent} to respond to.
     */
    @Override
    public void onSlashCommand(SlashCommandEvent event) {
        // * gets the option as a user
        User userToMention = event.getOption("user").getAsUser();
        int timesToPing = Integer.parseInt(event.getOption("times-to-ping").getAsString());

        event.reply("You sure you want to spam them, you don't have feelings?")
            .addActionRow(Button.of(ButtonStyle.DANGER,
                    generateComponentId(event.getUser().getId()), "Affirmative!"))
            .queue();
    }

    /**
     * After we created the button in the {@link #onSlashCommand(SlashCommandEvent)} method, the
     * button gets pressed. <br>
     * This gets handled here, it checks or the right user is pressing and or the right button was
     * pressed. <br>
     * After that it replies that spam pinging is bad!
     *
     * @param event The relating {@link ButtonClickEvent}
     * @param idArgs All the arguments stored in the ID ({@link Component#getId()
     *        Component#getId()}) have been converted to a {@link List} <br>
     *        The commands name and current time in MS have been removed from this, example:
     */
    @Override
    public void onButtonClick(ButtonClickEvent event, List<String> idArgs) {
        String userId = idArgs.get(0);
        if (event.getUser().getId().equals(userId)
                && event.getButton().getStyle() == ButtonStyle.DANGER) {
            event.reply("No! I'm not going to spam ping! You should behave!")
                .setEphemeral(true)
                .queue();

            event.getMessage()
                .editMessageComponents(ActionRow.of(event.getButton().asDisabled()))
                .queue();
        }
    }
}
