package org.togetherjava.tjbot.commands;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.example.CommandExample;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CommandHandler extends ListenerAdapter {
    private final static Logger logger = LoggerFactory.getLogger(CommandHandler.class);

    List<Command> commandList = List.of(
            new CommandExample()
            );

    Map<String, Command> commandMap;

    public CommandHandler() {
      commandMap = commandList.stream()
              .collect(
                      Collectors.toMap(Command::getCommandName, command -> command)
              );
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        Command command = commandMap.get(event.getName());

        if (command != null) {
            command.onSlashCommand(event);
        } else {
            logger.error("Command '{}' doesn't exist?", event.getName());
        }
    }

    // TODO: onButtonClick
    // TODO: onSelectionMenu
}
