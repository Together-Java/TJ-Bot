package org.togetherjava.tjbot.commands;

import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SelectionMenuEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface SlashCommand {

    @NotNull
    String getName();

    @NotNull
    String getDescription();

    SlashCommandVisibility getVisibility();

    @NotNull
    CommandData getData();

    void onSlashCommand(@NotNull SlashCommandEvent event);

    void onButtonClick(@NotNull ButtonClickEvent event, @NotNull List<String> args);

    void onSelectionMenu(@NotNull SelectionMenuEvent event, @NotNull List<String> args);
}
