package org.togetherjava.tjbot.commands.basic;

import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.togetherjava.tjbot.commands.SlashCommand;
import org.togetherjava.tjbot.jda.JdaTester;

import static org.mockito.Mockito.verify;

final class PingCommandTest {
    private JdaTester jdaTester;
    private SlashCommand command;

    private @NotNull SlashCommandEvent triggerSlashCommand() {
        SlashCommandEvent event = jdaTester.createSlashCommandEvent(command).build();
        command.onSlashCommand(event);
        return event;
    }

    @BeforeEach
    void setUp() {
        jdaTester = new JdaTester();
        command = jdaTester.spySlashCommand(new PingCommand());
    }

    @Test
    @DisplayName("'/ping' responds with pong")
    void pingRespondsWithPong() {
        // GIVEN
        // WHEN using '/ping'
        SlashCommandEvent event = triggerSlashCommand();

        // THEN the bot replies with pong
        verify(event).reply("Pong!");
    }
}
