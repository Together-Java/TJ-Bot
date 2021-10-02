package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.jetbrains.annotations.NotNull;
import org.togetherjava.tjbot.commands.SlashCommandAdapter;
import org.togetherjava.tjbot.commands.SlashCommandVisibility;

/**
 * Ping Pong command.
 */
public final class PingCommand extends SlashCommandAdapter {
    public PingCommand() {
        super("ping", "Bot responds with 'Pong!'", SlashCommandVisibility.GUILD);
    }

    /**
     * When the slash command is `/ping`, then the bot returns with the value `Pong!`
     *
     * @param event The relating {@link SlashCommandEvent}
     */
    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        event.reply("Pong!").queue();
    }
}
