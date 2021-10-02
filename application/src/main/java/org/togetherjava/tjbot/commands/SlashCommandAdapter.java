package org.togetherjava.tjbot.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.system.ComponentIds;

import java.util.Arrays;
import java.util.List;

public abstract class SlashCommandAdapter implements SlashCommand {
    private final String name;
    private final String description;
    private final SlashCommandVisibility visibility;
    private final CommandData data;

    protected SlashCommandAdapter(@NotNull String name, @NotNull String description,
            SlashCommandVisibility visibility) {
        this.name = name;
        this.description = description;
        this.visibility = visibility;

        data = new CommandData(name, description);
    }

    public final @NotNull String generateComponentId(@NotNull String... args) {
        try {
            return ComponentIds.generate(getName(), Arrays.asList(args));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public final @NotNull String getName() {
        return name;
    }

    @Override
    public final @NotNull String getDescription() {
        return description;
    }

    @Override
    public final SlashCommandVisibility getVisibility() {
        return visibility;
    }

    @Override
    public final @NotNull CommandData getData() {
        return data;
    }

    @SuppressWarnings("NoopMethodInAbstractClass")
    @Override
    public void onButtonClick(@NotNull ButtonClickEvent event, @NotNull List<String> args) {
        // Adapter does not react by default, subclasses may change this behavior
    }

    @SuppressWarnings("NoopMethodInAbstractClass")
    @Override
    public void onSelectionMenu(@NotNull SelectionMenuEvent event, @NotNull List<String> args) {
        // Adapter does not react by default, subclasses may change this behavior
    }
}
