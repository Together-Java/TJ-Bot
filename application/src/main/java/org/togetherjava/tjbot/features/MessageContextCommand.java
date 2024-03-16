package org.togetherjava.tjbot.features;

import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

import org.togetherjava.tjbot.features.componentids.ComponentIdGenerator;

import java.util.List;

/**
 * A message context-command is a command, accessible when right-clicking on a message.
 *
 * <p>
 * Represents a Discord message context-command. Mostly decorating
 * {@link net.dv8tion.jda.api.interactions.commands.Command}.
 * <p>
 * All message context-commands have to implement this interface. For convenience, there is a
 * {@link BotCommandAdapter} available that implemented most methods already. A new command can then
 * be registered by adding it to {@link Features}.
 * <p>
 * Context commands can either be visible globally in Discord or just to specific guilds. Minor
 * adjustments can be made via {@link CommandData}, which is then to be returned by
 * {@link #getData()} where the system will then pick it up from.
 * <p>
 * After registration, the system will notify a command whenever one of its corresponding message
 * context-commands ({@link #onMessageContext(MessageContextInteractionEvent)}), buttons
 * ({@link #onButtonClick(ButtonInteractionEvent, List)}) or menus
 * ({@link #onEntitySelectSelection(EntitySelectInteractionEvent, List)},
 * {@link #onStringSelectSelection(StringSelectInteractionEvent, List)}) have been triggered.
 * <p>
 * Some example commands are available in {@link org.togetherjava.tjbot.features.basic}.
 */
public interface MessageContextCommand extends BotCommand {
    /**
     * Triggered by the core system when a message context-command corresponding to this
     * implementation (based on {@link #getData()}) has been triggered.
     * <p>
     * This method may be called multithreaded. In particular, there are no guarantees that it will
     * be executed on the same thread repeatedly or on the same thread that other event methods have
     * been called on.
     * <p>
     * Details are available in the given event and the event also enables implementations to
     * respond to it.
     * <p>
     * Buttons or menus have to be created with a component ID (see
     * {@link ComponentInteraction#getComponentId()},
     * {@link net.dv8tion.jda.api.interactions.components.buttons.Button#of(ButtonStyle, String, String)})
     * in a very specific format, otherwise the core system will fail to identify the command that
     * corresponded to the button or menu click event and is unable to route it back.
     * <p>
     * See {@link #acceptComponentIdGenerator(ComponentIdGenerator)} for more info.
     *
     * @param event the event that triggered this
     */
    void onMessageContext(MessageContextInteractionEvent event);
}
