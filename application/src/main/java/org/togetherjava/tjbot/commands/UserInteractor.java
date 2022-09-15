package org.togetherjava.tjbot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.togetherjava.tjbot.commands.componentids.ComponentId;
import org.togetherjava.tjbot.commands.componentids.ComponentIdGenerator;
import org.togetherjava.tjbot.commands.componentids.Lifespan;

import java.util.List;

/**
 * Represents a feature that can interact with users. The most used implementation is
 * {@link SlashCommand}, {@link UserContextCommand} and {@link MessageContextCommand}.
 * <p>
 * An interactor must have a unique name and can react to button clicks and selection menu actions.
 */
public interface UserInteractor extends Feature {

    /**
     * Gets the name of the interactor.
     * <p>
     * It is not advised to start the name with {@code s-}, {@code mc-} or {@code uc-}
     *
     * <p>
     * After registration of the interactor, the name must not change anymore.
     *
     * @return the name of the interactor
     */
    String getName();

    /**
     * Triggered by the core system when a button corresponding to this implementation (based on
     * {@link #getName()}) has been clicked.
     * <p>
     * This method may be called multithreaded. In particular, there are no guarantees that it will
     * be executed on the same thread repeatedly or on the same thread that other event methods have
     * been called on.
     * <p>
     * Details are available in the given event and the event also enables implementations to
     * respond to it.
     *
     * @param event the event that triggered this
     * @param args the arguments transported with the button, see
     *        {@link SlashCommand#onSlashCommand(SlashCommandInteractionEvent)} for details on how
     *        these are created
     */
    void onButtonClick(ButtonInteractionEvent event, List<String> args);

    /**
     * Triggered by the core system when a selection menu corresponding to this implementation
     * (based on {@link #getName()}) has been clicked.
     * <p>
     * This method may be called multithreaded. In particular, there are no guarantees that it will
     * be executed on the same thread repeatedly or on the same thread that other event methods have
     * been called on.
     * <p>
     * Details are available in the given event and the event also enables implementations to
     * respond to it.
     *
     * @param event the event that triggered this
     * @param args the arguments transported with the selection menu, see
     *        {@link SlashCommand#onSlashCommand(SlashCommandInteractionEvent)} for details on how
     *        these are created
     */
    void onSelectionMenu(SelectMenuInteractionEvent event, List<String> args);

    /**
     * Triggered by the core system during its setup phase. It will provide the command a component
     * id generator through this method, which can be used to generate component ids, as used for
     * button or selection menus.
     *
     * <p>
     * The component ID has to be a UUID-string (see {@link java.util.UUID}), which is associated to
     * a specific database entry, containing meta information about the command being executed. Such
     * a database entry can be created and a UUID be obtained by using
     * {@link ComponentIdGenerator#generate(ComponentId, Lifespan)}, as provided by the instance
     * given to this method during system setup. The required {@link ComponentId} instance accepts
     * optional extra arguments, which, if provided, can be picked up during the corresponding event
     * (see {@link #onButtonClick(ButtonInteractionEvent, List)},
     * {@link #onSelectionMenu(SelectMenuInteractionEvent, List)}).
     * <p>
     * Alternatively, if {@link BotCommandAdapter} has been extended, it also offers a handy
     * {@link BotCommandAdapter#generateComponentId(String...)} method to ease the flow.
     * <p>
     * See <a href="https://github.com/Together-Java/TJ-Bot/wiki/Component-IDs">Component-IDs</a> on
     * our Wiki for more details and examples of how to use component IDs.
     *
     * @param generator the provided component id generator
     */
    void acceptComponentIdGenerator(ComponentIdGenerator generator);
}
