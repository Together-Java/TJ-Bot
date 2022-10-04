package org.togetherjava.tjbot.commands.componentids;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.togetherjava.tjbot.commands.SlashCommand;
import org.togetherjava.tjbot.commands.UserInteractionType;

import java.util.Arrays;
import java.util.Objects;

/**
 * Delegate class for interacting with component IDs. Provides methods to easily generate valid
 * component IDs as accepted by the system.
 * <p>
 * The method {@link #acceptComponentIdGenerator(ComponentIdGenerator)} must be used before
 * attempting to generate IDs.
 * <p>
 * Mostly used by classes implementing {@link org.togetherjava.tjbot.commands.UserInteractor}, to
 * simplify handling with the IDs.
 */
public final class ComponentIdInteractor {
    private final String name;
    private final UserInteractionType userInteractionType;
    private ComponentIdGenerator generator;

    /**
     * Creates a new instance.
     *
     * @param userInteractionType The type of interaction component IDs are used by
     * @param name The unique name of the interactor. Requirements for this are documented in
     *        {@link net.dv8tion.jda.api.interactions.commands.build.Commands#slash(String, String)}.
     *        After registration of the interactor, the name must not change anymore.
     */
    public ComponentIdInteractor(UserInteractionType userInteractionType, String name) {
        this.name = Objects.requireNonNull(name);
        this.userInteractionType = Objects.requireNonNull(userInteractionType);
    }

    /**
     * Must be used before generating component IDs with {@link #generateComponentId(String...)} and
     * similar.
     * <p>
     * It will provide the interactor a component ID generator through this method, which can be
     * used to generate component IDs, as used for button or selection menus. See
     * {@link SlashCommand#onSlashCommand(SlashCommandInteractionEvent)} for details on how to use
     * this.
     *
     * @param generator the provided component ID generator
     */
    public void acceptComponentIdGenerator(ComponentIdGenerator generator) {
        this.generator = generator;
    }

    /**
     * Generates component IDs that are considered valid per
     * {@link SlashCommand#onSlashCommand(SlashCommandInteractionEvent)}.
     * <p>
     * They can be used to create buttons or selection menus and transport additional data
     * throughout the event (e.g., the user ID who created the button dialog).
     * <p>
     * IDs generated by this method have a regular lifespan, meaning they might get evicted and
     * expire after not being used for a long time. Use
     * {@link #generateComponentId(Lifespan, String...)} to set other lifespans, if desired.
     *
     * @param args the extra arguments that should be part of the ID
     * @return the generated component ID
     */
    @SuppressWarnings("OverloadedVarargsMethod")
    public String generateComponentId(String... args) {
        return generateComponentId(Lifespan.REGULAR, args);
    }

    /**
     * Generates component IDs that are considered valid per
     * {@link SlashCommand#onSlashCommand(SlashCommandInteractionEvent)}.
     * <p>
     * They can be used to create buttons or selection menus and transport additional data
     * throughout the event (e.g., the user ID who created the button dialog).
     *
     * @param lifespan the lifespan of the component ID, controls when an ID that was not used for a
     *        long time might be evicted and expire
     * @param args the extra arguments that should be part of the ID
     * @return the generated component ID
     */
    @SuppressWarnings("OverloadedVarargsMethod")
    public String generateComponentId(Lifespan lifespan, String... args) {
        return generator.generate(
                new ComponentId(userInteractionType.getPrefixedName(name), Arrays.asList(args)),
                lifespan);
    }
}
