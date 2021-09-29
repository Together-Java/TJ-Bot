package org.togetherjava.tjbot.command;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.togetherjava.tjbot.AbstractJdaMock;
import org.togetherjava.tjbot.commands.Command;
import org.togetherjava.tjbot.commands.generic.PingCommand;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PingCommandTest extends AbstractJdaMock {

    public PingCommandTest() {
        super(2, 2);
    }

    @DisplayName("""
            Given an existing Ping Command implementation
            When a command is fired that uses the ping command
            Then a pong message is returned
            """)
    @Test
    void testPingCommand() {
        Command command = new PingCommand();

        SlashCommandEvent slashCommandEvent = createSlashCommand("ping");

        command.onSlashCommand(slashCommandEvent);

        // Assert and validate that our mock private channel calls the sendMessage event exactly
        // once.
        verify(slashCommandEvent, atLeastOnce()).reply("Pong!");
    }
}
