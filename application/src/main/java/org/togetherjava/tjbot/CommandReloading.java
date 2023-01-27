package org.togetherjava.tjbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.togetherjava.tjbot.features.BotCommand;
import org.togetherjava.tjbot.features.CommandVisibility;
import org.togetherjava.tjbot.features.system.CommandProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Offers utility functions for reloading all commands.
 */
public class CommandReloading {
    private static final Logger logger = LoggerFactory.getLogger(CommandReloading.class);

    /**
     * According to <a href=
     * "https://discord.com/developers/docs/interactions/application-commands#registering-a-command">Discord
     * docs</a>, there can be a maximum of 110 commands, 100 slash, and 5 context each.
     * <p>
     * Because this bot most of the time operates in only 1 server, chances of exceeding this limit
     * is low.
     */
    public static final int MAX_COMMAND_COUNT = 110;

    private CommandReloading() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Reloads all commands based on the given {@link CommandProvider}.
     *
     * @param jda the JDA to update commands on
     * @param commandProvider the {@link CommandProvider} to grab commands from
     */
    public static void reloadCommands(final JDA jda, final CommandProvider commandProvider) {
        logger.info("Reloading commands...");
        List<CommandListUpdateAction> actions =
                Collections.synchronizedList(new ArrayList<>(MAX_COMMAND_COUNT));

        // Reload global commands
        actions.add(updateCommandsIf(command -> CommandVisibility.GLOBAL == command.getVisibility(),
                getGlobalUpdateAction(jda), commandProvider));

        // Reload guild commands (potentially many guilds)
        // NOTE Storing the guild actions in a list is potentially dangerous since the
        // bot might theoretically be part of so many guilds that it exceeds the max size of
        // list. However, correctly reducing RestActions in a stream is not trivial.
        getGuildUpdateActions(jda)
            .map(updateAction -> updateCommandsIf(
                    command -> CommandVisibility.GUILD == command.getVisibility(), updateAction,
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
    @Contract("_, _, _ -> param2")
    private static CommandListUpdateAction updateCommandsIf(
            final Predicate<? super BotCommand> commandFilter,
            final CommandListUpdateAction updateAction, final CommandProvider commandProvider) {
        commandProvider.getInteractors()
            .stream()
            .filter(BotCommand.class::isInstance)
            .map(BotCommand.class::cast)
            .filter(commandFilter)
            .map(BotCommand::getData)
            .forEach(updateAction::addCommands);

        return updateAction;
    }

    private static CommandListUpdateAction getGlobalUpdateAction(final JDA jda) {
        return jda.updateCommands();
    }

    private static Stream<CommandListUpdateAction> getGuildUpdateActions(final JDA jda) {
        return jda.getGuildCache().stream().map(Guild::updateCommands);
    }

}
