package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import org.togetherjava.tjbot.features.MessageReceiver;
import org.togetherjava.tjbot.jda.JdaTester;

import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

final class SlashCommandEducatorTest {
    private JdaTester jdaTester;
    private MessageReceiver messageReceiver;

    @BeforeEach
    void setUp() {
        jdaTester = new JdaTester();
        messageReceiver = new SlashCommandEducator();
    }

    private MessageReceivedEvent sendMessage(String content) {
        MessageCreateData message = new MessageCreateBuilder().setContent(content).build();
        MessageReceivedEvent event =
                jdaTester.createMessageReceiveEvent(message, List.of(), ChannelType.TEXT);

        messageReceiver.onMessageReceived(event);

        return event;
    }

    @ParameterizedTest
    @MethodSource("provideMessageCommands")
    void sendsAdviceOnMessageCommand(String message) {
        // GIVEN a message containing a message command
        // WHEN the message is sent
        MessageReceivedEvent event = sendMessage(message);

        // THEN the system replies to it with an advice
        verify(event.getMessage(), times(1)).replyEmbeds(any(MessageEmbed.class));
    }

    @ParameterizedTest
    @MethodSource("provideOtherMessages")
    void ignoresOtherMessages(String message) {
        // GIVEN a message that is not a message command
        // WHEN the message is sent
        MessageReceivedEvent event = sendMessage(message);

        // THEN the system ignores the message and does not reply to it
        verify(event.getMessage(), never()).replyEmbeds(any(MessageEmbed.class));
    }

    private static Stream<String> provideMessageCommands() {
        return Stream.of("!foo", ".foo", "?foo", ".test", "!whatever", "!this is a test");
    }

    private static Stream<String> provideOtherMessages() {
        return Stream.of("  a  ", "foo", "#foo", "/foo", "!!!", "?!?!?", "?", ".,-", "!f", "! foo",
                "thisIsAWordWhichLengthIsMoreThanThirtyLetterSoItShouldNotReply",
                ".isLetter and .isNumber are available", ".toString()", ".toString();",
                "this is a test;");
    }
}
