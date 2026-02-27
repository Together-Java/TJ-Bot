package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.togetherjava.tjbot.features.SlashCommand;
import org.togetherjava.tjbot.jda.JdaTester;

import static org.mockito.Mockito.verify;

final class PingCommandTest {
    private JdaTester jdaTester;
    private SlashCommand command;

    private SlashCommandInteractionEvent triggerSlashCommand() {
        SlashCommandInteractionEvent event =
                jdaTester.createSlashCommandInteractionEvent(command).build();
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
        SlashCommandInteractionEvent event = triggerSlashCommand();

        // THEN the bot replies with pong
        verify(event).reply("Pong!");
    }
}
