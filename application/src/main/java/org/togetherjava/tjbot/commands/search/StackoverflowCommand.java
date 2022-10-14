package org.togetherjava.tjbot.commands.search;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;

public class StackoverflowCommand extends SlashCommandAdapter {
    private final SearchStrategy searchStrategy = new StackOverflowSearchStrategy();

    protected StackoverflowCommand() {
        super("stackoverflow", "Searches Stackoverflow for your search term",
                CommandVisibility.GUILD);
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event) {



    }
}