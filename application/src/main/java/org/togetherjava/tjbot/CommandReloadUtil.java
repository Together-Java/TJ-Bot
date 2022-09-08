package org.togetherjava.tjbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.togetherjava.tjbot.commands.BotCommand;
import org.togetherjava.tjbot.commands.CommandVisibility;
import org.togetherjava.tjbot.commands.system.CommandProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Offers utility functions for reloading all commands.
 */
public class CommandReloadUtil {
    private static final Logger logger = LoggerFactory.getLogger(CommandReloadUtil.class);

    private CommandReloadUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Reloads all commands based on the given {@link CommandProvider}
     * @param jda the JDA to update commands on
     * @param commandProvider the {@link CommandProvider} to grab commands from
     */
    public static void reloadCommands(JDA jda, CommandProvider commandProvider) {
        logger.info("Reloading commands...");
        List<CommandListUpdateAction> actions = Collections.synchronizedList(new ArrayList<>());

        // Reload global commands
        actions.add(updateCommandsIf(command -> command.getVisibility() == CommandVisibility.GLOBAL,
                getGlobalUpdateAction(jda), commandProvider));

        // Reload guild commands (potentially many guilds)
        // NOTE Storing the guild actions in a list is potentially dangerous since the
        // bot might theoretically be part of so many guilds that it exceeds the max size of
        // list. However, correctly reducing RestActions in a stream is not trivial.
        getGuildUpdateActions(jda)
            .map(updateAction -> updateCommandsIf(
                    command -> command.getVisibility() == CommandVisibility.GUILD, updateAction,
                    commandProvider))
            .forEach(actions::add);
        logger.debug("Reloading commands over {} action-upstreams", actions.size());

        // Send message when all are done
        RestAction.allOf(actions).queue(a -> logger.debug("Commands successfully reloaded!"));
    }

    /**
     * Updates all commands given by the command provider which pass the given filter by pushing
     * through the given action upstream.
     *
     * @param commandFilter filter that matches commands that should be uploaded
     * @param updateAction the upstream to update commands
     * @return the given upstream for chaining
     */
    private static CommandListUpdateAction updateCommandsIf(
            Predicate<? super BotCommand> commandFilter, CommandListUpdateAction updateAction,
            CommandProvider commandProvider) {
        return commandProvider.getInteractors()
            .stream()
            .filter(BotCommand.class::isInstance)
            .map(BotCommand.class::cast)
            .filter(commandFilter)
            .map(BotCommand::getData)
            .reduce(updateAction, CommandListUpdateAction::addCommands, (x, y) -> x);
    }

    private static CommandListUpdateAction getGlobalUpdateAction(JDA jda) {
        return jda.updateCommands();
    }

    private static Stream<CommandListUpdateAction> getGuildUpdateActions(JDA jda) {
        return jda.getGuildCache().stream().map(Guild::updateCommands);
    }

}