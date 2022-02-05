package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import org.junit.jupiter.api.Test;
import org.togetherjava.tjbot.commands.SlashCommand;
import org.togetherjava.tjbot.jda.JdaTester;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

final class PingCommandTest {
    @Test
    void pingCommand() {
        SlashCommand command = new PingCommand();
        JdaTester jdaTester = new JdaTester();

        SlashCommandInteraction event = jdaTester.createSlashCommandInteraction(command).build();
        command.onSlashCommand(event);

        verify(event, times(1)).reply("Pong!");
    }
}