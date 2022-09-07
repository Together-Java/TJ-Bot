package org.togetherjava.tjbot.commands;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a Discord command.
 * <p>
 * All commands have to implement this interface. For convenience, there is a
 * {@link BotCommandAdapter} available that implemented most methods already. A new command can then
 * be registered by adding it to {@link Features}.
 * <p>
 * <p>
 * Commands can either be visible globally in Discord or just to specific guilds. Some
 * configurations can be made via {@link CommandData}, which is then to be returned by
 * {@link #getData()} where the system will then pick it up from.
 * <p>
 * After registration, the system will notify a command whenever one of its corresponding command
 * method, buttons ({@link #onButtonClick(ButtonInteractionEvent, List)}) or menus
 * ({@link #onSelectionMenu(SelectMenuInteractionEvent, List)}) have been triggered.
 * <p>
 * <p>
 * Some example commands are available in {@link org.togetherjava.tjbot.commands.basic}.
 */
public interface BotCommand extends UserInteractor {

    /**
     * Gets the type of this command.
     * <p>
     * After registration of the command, the type must not change anymore.
     *
     * @return the type of the command
     */
    @NotNull
    Command.Type getType();


    /**
     * Gets the visibility of this command.
     * <p>
     * After registration of the command, the visibility must not change anymore.
     *
     * @return the visibility of the command
     */
    @NotNull
    CommandVisibility getVisibility();

    /**
     * Gets the command data belonging to this command.
     * <p>
     * See {@link net.dv8tion.jda.api.interactions.commands.build.Commands Commands} for details on
     * how to create and configure instances of it.
     * <p>
     * <p>
     * This method may be called multiple times, implementations must not create new data each time
     * but instead configure it once beforehand. The core system will automatically call this method
     * to register the command to Discord.
     *
     * @return the command data of this command
     */
    @NotNull
    CommandData getData();
}
