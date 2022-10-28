package org.togetherjava.tjbot.commands.tophelper;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.internal.JDAImpl;
import net.dv8tion.jda.internal.entities.UserImpl;
import net.dv8tion.jda.internal.entities.channel.concrete.ThreadChannelImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.togetherjava.tjbot.db.generated.tables.HelpChannelMessages.HELP_CHANNEL_MESSAGES;

final class TopHelperMessageListenerTest {

    private static final String STAGING_CHANNEL_PATTERN = "ask_here";
    private static final String OVERVIEW_CHANNEL_PATTERN = "active_questions";

    private static JdaTester jdaTester;
    private static TopHelpersMessageListener topHelpersListener;

    @BeforeAll
    static void setUp() {
        Database database = Database.createMemoryDatabase(HELP_CHANNEL_MESSAGES);
        Config config = mock(Config.class);
        HelpSystemConfig helpSystemConfig = mock(HelpSystemConfig.class);

        when(helpSystemConfig.getStagingChannelPattern()).thenReturn(STAGING_CHANNEL_PATTERN);
        when(helpSystemConfig.getOverviewChannelPattern()).thenReturn(OVERVIEW_CHANNEL_PATTERN);

        when(config.getHelpSystem()).thenReturn(helpSystemConfig);

        jdaTester = new JdaTester();
        topHelpersListener = new TopHelpersMessageListener(database, config);
    }

    @Test
    @DisplayName("Recognizes valid messages")
    void recognizesValidMessages() {
        // GIVEN a message by a human in a help channel
        MessageReceivedEvent event =
                createFakeMessageReceivedEvent(false, false, true, OVERVIEW_CHANNEL_PATTERN);

        // WHEN checking if the message should be ignored
        boolean shouldBeIgnored = topHelpersListener.shouldIgnoreMessage(event);

        // THEN the message is not ignored
        assertFalse(shouldBeIgnored);
    }

    @Test
    @DisplayName("Ignores bots")
    void ignoresBots() {
        // GIVEN a message from a bot
        MessageReceivedEvent event =
                createFakeMessageReceivedEvent(true, false, true, OVERVIEW_CHANNEL_PATTERN);

        // WHEN checking if the message should be ignored
        boolean shouldBeIgnored = topHelpersListener.shouldIgnoreMessage(event);

        // THEN the message is ignored
        assertTrue(shouldBeIgnored);
    }

    @Test
    @DisplayName("Ignores webhooks")
    void ignoresWebhooks() {
        // GIVEN a message from a webhook
        MessageReceivedEvent event =
                createFakeMessageReceivedEvent(false, true, true, OVERVIEW_CHANNEL_PATTERN);

        // WHEN checking if the message should be ignored
        boolean shouldBeIgnored = topHelpersListener.shouldIgnoreMessage(event);

        // THEN the message is ignored
        assertTrue(shouldBeIgnored);
    }

    @Test
    @DisplayName("Ignores wrong channels")
    void ignoresWrongChannels() {
        // GIVEN a message outside a help thread
        MessageReceivedEvent eventNotAThread =
                createFakeMessageReceivedEvent(false, false, false, OVERVIEW_CHANNEL_PATTERN);
        MessageReceivedEvent eventWrongParentName =
                createFakeMessageReceivedEvent(false, false, true, "memes");

        // WHEN checking if the message should be ignored
        boolean ignoresNonThreadChannels =
                topHelpersListener.shouldIgnoreMessage(eventNotAThread);
        boolean ignoresWrongParentNames =
                topHelpersListener.shouldIgnoreMessage(eventWrongParentName);

        // THEN the message is ignored
        assertTrue(ignoresNonThreadChannels, "Failed to ignore non-thread channels");
        assertTrue(ignoresWrongParentNames, "Failed to ignore wrong parent channel names");
    }


    MessageReceivedEvent createFakeMessageReceivedEvent(boolean isBot, boolean isWebhook,
            boolean isThread, String parentChannelName) {
        Message messageMock = mock(Message.class);
        ThreadChannelImpl threadMock = mock(ThreadChannelImpl.class, RETURNS_DEEP_STUBS);

        User user = new UserImpl(123456789, (JDAImpl) jdaTester.getJdaMock()).setName("John Doe")
            .setDiscriminator("1234")
            .setBot(isBot);

        when(threadMock.getType())
            .thenReturn(isThread ? ChannelType.GUILD_PUBLIC_THREAD : ChannelType.TEXT);
        when(threadMock.getParentChannel().getName()).thenReturn(parentChannelName);
        when(threadMock.asThreadChannel()).thenReturn(threadMock);

        when(messageMock.isWebhookMessage()).thenReturn(isWebhook);
        when(messageMock.getAuthor()).thenReturn(user);
        when(messageMock.getChannel()).thenReturn(threadMock);

        return new MessageReceivedEvent(jdaTester.getJdaMock(), 0, messageMock);
    }


    @ParameterizedTest
    @MethodSource("provideInvalidCharactersWithDescription")
    @DisplayName("Does ignore invalid characters")
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
    @DisplayName("Does count valid characters")
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
