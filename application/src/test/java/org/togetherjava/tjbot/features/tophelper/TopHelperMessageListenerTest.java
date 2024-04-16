package org.togetherjava.tjbot.features.tophelper;

import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.HelpSystemConfig;
import org.togetherjava.tjbot.db.Database;
import org.togetherjava.tjbot.jda.JdaTester;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.togetherjava.tjbot.db.generated.tables.HelpChannelMessages.HELP_CHANNEL_MESSAGES;

final class TopHelperMessageListenerTest {

    private static final String HELP_FORUM_PATTERN = "questions";

    private static JdaTester jdaTester;
    private static TopHelpersMessageListener topHelpersListener;

    @BeforeAll
    static void setUp() {
        Database database = Database.createMemoryDatabase(HELP_CHANNEL_MESSAGES);
        Config config = mock(Config.class);
        HelpSystemConfig helpSystemConfig = mock(HelpSystemConfig.class);

        when(helpSystemConfig.getHelpForumPattern()).thenReturn(HELP_FORUM_PATTERN);

        when(config.getHelpSystem()).thenReturn(helpSystemConfig);

        jdaTester = new JdaTester();
        topHelpersListener = new TopHelpersMessageListener(database, config);
    }

    @Test
    void recognizesValidMessages() {
        // GIVEN a message by a human in a help channel
        MessageReceivedEvent event =
                createMessageReceivedEvent(false, false, true, HELP_FORUM_PATTERN);

        // WHEN checking if the message should be ignored
        boolean shouldBeIgnored = topHelpersListener.shouldIgnoreMessage(event);

        // THEN the message is not ignored
        assertFalse(shouldBeIgnored);
    }

    @Test
    void ignoresBots() {
        // GIVEN a message from a bot
        MessageReceivedEvent event =
                createMessageReceivedEvent(true, false, true, HELP_FORUM_PATTERN);

        // WHEN checking if the message should be ignored
        boolean shouldBeIgnored = topHelpersListener.shouldIgnoreMessage(event);

        // THEN the message is ignored
        assertTrue(shouldBeIgnored);
    }

    @Test
    void ignoresWebhooks() {
        // GIVEN a message from a webhook
        MessageReceivedEvent event =
                createMessageReceivedEvent(false, true, true, HELP_FORUM_PATTERN);

        // WHEN checking if the message should be ignored
        boolean shouldBeIgnored = topHelpersListener.shouldIgnoreMessage(event);

        // THEN the message is ignored
        assertTrue(shouldBeIgnored);
    }

    @Test
    void ignoresWrongChannels() {
        // GIVEN a message outside a help thread
        MessageReceivedEvent eventNotAThread =
                createMessageReceivedEvent(false, false, false, HELP_FORUM_PATTERN);
        MessageReceivedEvent eventWrongParentName =
                createMessageReceivedEvent(false, false, true, "memes");

        // WHEN checking if the message should be ignored
        boolean ignoresNonThreadChannels = topHelpersListener.shouldIgnoreMessage(eventNotAThread);
        boolean ignoresWrongParentNames =
                topHelpersListener.shouldIgnoreMessage(eventWrongParentName);

        // THEN the message is ignored
        assertTrue(ignoresNonThreadChannels, "Failed to ignore non-thread channels");
        assertTrue(ignoresWrongParentNames, "Failed to ignore wrong parent channel names");
    }


    MessageReceivedEvent createMessageReceivedEvent(boolean isBot, boolean isWebhook,
            boolean isThread, String parentChannelName) {
        try (MessageCreateData message = new MessageCreateBuilder().setContent("Any").build()) {
            MessageReceivedEvent event = jdaTester.createMessageReceiveEvent(message, List.of(),
                    isThread ? ChannelType.GUILD_PUBLIC_THREAD : ChannelType.TEXT);

            when(jdaTester.getMemberSpy().getUser().isBot()).thenReturn(isBot);
            when(event.getMessage().isWebhookMessage()).thenReturn(isWebhook);
            when(jdaTester.getTextChannelSpy().getName()).thenReturn(parentChannelName);

            return event;
        }
    }


    @ParameterizedTest
    @MethodSource("provideInvalidCharactersWithDescription")
    void excludesInvalidCharacters(String invalidChars, String description) {
        // GIVEN a string of invalid characters

        // WHEN counting the amount of valid characters
        long validCharacterCount = TopHelpersMessageListener.countValidCharacters(invalidChars);

        // THEN no characters are counted
        assertEquals(0, validCharacterCount,
                "Characters [%s] were not fully ignored".formatted(description));
    }


    @ParameterizedTest
    @MethodSource("provideValidCharacters")
    void countsValidCharacters(String validChars) {
        // GIVEN a string of valid characters

        // WHEN counting the amount of valid characters
        long validCharCount = TopHelpersMessageListener.countValidCharacters(validChars);

        // THEN all characters are counted
        assertEquals(validChars.length(), validCharCount,
                "Characters [%s] were not fully ignored".formatted(validChars));
    }


    private static Stream<Arguments> provideInvalidCharactersWithDescription() {
        return Stream.of( // Invalid characters
                Arguments.of("\u061C", "Arabic Letter Mark"),
                Arguments.of("\u0600", "Arabic Number Sign"),
                Arguments.of("\u180E", "Mongolian Vowel Separator"),
                Arguments.of("\u200B", "Zero Width Space"),
                Arguments.of("\u200C", "Zero Width Non-Joiner"),
                Arguments.of("\u200D", "Zero Width Joiner"),
                Arguments.of("\u200E", "Left-to-Right Mark"),
                Arguments.of("\u200F", "Right-to-Left Mark"));
    }


    private static List<String> provideValidCharacters() {
        return List.of( // Valid characters
                "a", "A", "b", "B", "c", "C", "x", "X,", "y", "Y", "z", "Z", // Latin alphabet
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", // Numbers
                "°", "!", "§", "§", "$", "%", "&", "/", "(", ")", "{", "}", "[", "]", "=", // Other
                "+", "*", "~", "-", "_", ".", ",", "?", ":", ";", "|", "<", ">", "@", "€", "µ", // Other
                "α", "Α", "β", "Β", "γ", "Γ", "χ", "Χ", "ψ", "Ψ", "ω", "Ω", // Greek alphabet
                "ä", "ö", "ü", "ß", // German
                "á", "è", "î", // French
                "天", "四", "永", // Chinese
                "😀", "😛", "❤️", "💚", "⛔" // Emojis
        );
    }

}
