package org.togetherjava.tjbot.command;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.togetherjava.tjbot.AbstractJDMock;
import org.togetherjava.tjbot.commands.Command;
import org.togetherjava.tjbot.commands.generic.PingCommand;

class PingCommandTest extends AbstractJDMock {

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

        command.onSlashCommand(createSlashCommand("ping"));

        Assertions.assertTrue(true);
    }

    @DisplayName("""
            Given an existing Ping Command implementation
            When a command is fired that does not use ping command
            Then no pong message is returned
            """)
    @Test
    void testWrongPingCommand() {
        Command command = new PingCommand();

        command.onSlashCommand(createSlashCommand("2pi2ng"));

        Assertions.assertTrue(true);
    }
}
