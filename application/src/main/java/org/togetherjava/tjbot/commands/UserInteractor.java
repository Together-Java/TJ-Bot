package org.togetherjava.tjbot.commands;

import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.componentids.ComponentIdGenerator;

import java.util.List;

/**
 * Represents a feature that can interact with users. The most important implementation is
 * {@link SlashCommand}.
 * <p>
 * An interactor must have a unique name and can react to button clicks and selection menu actions.
 */
public interface UserInteractor extends Feature {

    /**
     * Gets the name of the interactor.
     * <p>
     * Requirements for this are documented in {@link CommandData#CommandData(String, String)}.
     * <p>
     * <p>
     * After registration of the interactor, the name must not change anymore.
     *
     * @return the name of the interactor
     */
    @NotNull
    String getName();

    /**
     * Triggered by the core system when a button corresponding to this implementation (based on
     * {@link #getName()}) has been clicked.
     * <p>
     * This method may be called multi-threaded. In particular, there are no guarantees that it will
     * be executed on the same thread repeatedly or on the same thread that other event methods have
     * been called on.
     * <p>
     * Details are available in the given event and the event also enables implementations to
     * respond to it.
     * <p>
     * This method will be called in a multi-threaded context and the event may not be hold valid
     * forever.
     *
     * @param event the event that triggered this
     * @param args the arguments transported with the button, see
     *        {@link SlashCommand#onSlashCommand(SlashCommandEvent)} for details on how these are
     *        created
     */
    void onButtonClick(@NotNull ButtonClickEvent event, @NotNull List<String> args);

    /**
     * Triggered by the core system when a selection menu corresponding to this implementation
     * (based on {@link #getName()}) has been clicked.
     * <p>
     * This method may be called multi-threaded. In particular, there are no guarantees that it will
     * be executed on the same thread repeatedly or on the same thread that other event methods have
     * been called on.
     * <p>
     * Details are available in the given event and the event also enables implementations to
     * respond to it.
     * <p>
     * This method will be called in a multi-threaded context and the event may not be hold valid
     * forever.
     *
     * @param event the event that triggered this
     * @param args the arguments transported with the selection menu, see
     *        {@link SlashCommand#onSlashCommand(SlashCommandEvent)} for details on how these are
     *        created
     */
    void onSelectionMenu(@NotNull SelectionMenuEvent event, @NotNull List<String> args);

    /**
     * Triggered by the core system during its setup phase. It will provide the interactor a
     * component id generator through this method, which can be used to generate component ids, as
     * used for button or selection menus. See
     * {@link SlashCommand#onSlashCommand(SlashCommandEvent)} for details on how to use this.
     *
     * @param generator the provided component id generator
     */
    void acceptComponentIdGenerator(@NotNull ComponentIdGenerator generator);
}
