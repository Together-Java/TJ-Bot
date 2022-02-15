package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.CommandVisibility;

/**
 * Implementation of an example command to illustrate how to respond to a user.
 * <p>
 * The implemented command is {@code /ping}, upon which the bot will respond with {@code Pong!}.
 */
public final class PingCommand extends SlashCommandAdapter {
    /**
     * Creates an instance of the ping pong command.
     */
    public PingCommand() {
        super("ping", "Bot responds with 'Pong!'", CommandVisibility.GUILD);
    }

    /**
     * When triggered with {@code /ping}, the bot will respond with {@code Pong!}.
     *
     * @param event the corresponding event
     */
    @Override
    public void onSlashCommand(@NotNull SlashCommandInteractionEvent event) {
        event.reply("Pong!").queue();
    }
}
