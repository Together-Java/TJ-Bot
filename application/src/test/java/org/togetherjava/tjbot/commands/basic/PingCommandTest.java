package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
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

        SlashCommandEvent event = jdaTester.createSlashCommandEvent(command).build();
        command.onSlashCommand(event);

        verify(event, times(1)).reply("Pong!");
    }
}
