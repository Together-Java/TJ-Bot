package org.togetherjava.tjbot.features.basic;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.requests.RestAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.togetherjava.tjbot.config.Config;
import org.togetherjava.tjbot.config.QuoteBoardConfig;
import org.togetherjava.tjbot.jda.JdaTester;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class QuoteBoardForwarderTest {
    private QuoteBoardForwarder forwarder;
    private JdaTester jdaTester;

    @BeforeEach
    void setUp() {
        jdaTester = new JdaTester();
        Config config = mock(Config.class);
        when(config.getQuoteBoardConfig()).thenReturn(new QuoteBoardConfig(2.0F, "quotes", "✅",
                1.0F, Map.of("⭐", 2.0F), List.of("general")));
        forwarder = new QuoteBoardForwarder(config, jdaTester.getJdaMock());
    }

    private long toSnowflake(Instant instant) {
        return (instant.toEpochMilli() - 1420070400000L) << 22;
    }

    @Test
    void testReactionOnNewMessageTriggers() {
        var newTime = Instant.now();
        var newMessageId = toSnowflake(newTime);
        var event1 = createMockEvent(newMessageId, 111L, "⭐");

        RestAction<Message> retrieveAction = mock(RestAction.class);
        when(event1.retrieveMessage()).thenReturn(retrieveAction);

        forwarder.onMessageReactionAdd(event1);

        verify(event1).retrieveMessage();
    }

    @Test
    void testReactionOnOldMessageDoesNotTrigger() {
        var oldTime = Instant.now().minus(8, ChronoUnit.DAYS);
        var oldMessageId = toSnowflake(oldTime);
        var event1 = createMockEvent(oldMessageId, 111L, "⭐");
        var event2 = createMockEvent(oldMessageId, 222L, "⭐");

        forwarder.onMessageReactionAdd(event1);
        forwarder.onMessageReactionAdd(event2);

        verify(event1, never()).retrieveMessage();
        verify(event2, never()).retrieveMessage();
    }

    @Test
    void testCleanupRoutine() throws Exception {
        var reactions = getReactionsMap();
        var oldTime = Instant.now().minus(8, ChronoUnit.DAYS);
        var oldMessageId = toSnowflake(oldTime);
        var newTime = Instant.now();
        var newMessageId = toSnowflake(newTime);

        reactions.put(oldMessageId, new java.util.HashMap<>());
        reactions.put(newMessageId, new java.util.HashMap<>());

        forwarder.runRoutine(jdaTester.getJdaMock());

        assertFalse(reactions.containsKey(oldMessageId));
        assertTrue(reactions.containsKey(newMessageId));
    }

    private Map<Long, Map<String, Set<Long>>> getReactionsMap() throws Exception {
        var field = QuoteBoardForwarder.class.getDeclaredField("reactions");
        field.setAccessible(true);
        return (Map<Long, Map<String, Set<Long>>>) field.get(forwarder);
    }

    private MessageReactionAddEvent createMockEvent(long messageId, long userId, String emojiCode) {
        var event = mock(MessageReactionAddEvent.class);
        var channel = mock(MessageChannelUnion.class);
        when(channel.getName()).thenReturn("general");
        when(channel.getId()).thenReturn("123");
        when(event.getChannel()).thenReturn(channel);
        when(event.getMessageIdLong()).thenReturn(messageId);
        when(event.getMessageId()).thenReturn(String.valueOf(messageId));

        Guild guild = mock(Guild.class);
        when(guild.getIdLong()).thenReturn(456L);
        when(event.getGuild()).thenReturn(guild);

        var jda = jdaTester.getJdaMock();
        when(event.getJDA()).thenReturn(jda);
        when(jda.getGuildById(456L)).thenReturn(guild);

        TextChannel boardChannel = mock(TextChannel.class);
        when(boardChannel.getName()).thenReturn("quotes");
        when(boardChannel.getId()).thenReturn("789");

        when(guild.getTextChannels()).thenReturn(List.of(boardChannel));

        MessageReaction reaction = mock(MessageReaction.class);
        EmojiUnion emoji = mock(EmojiUnion.class);
        when(emoji.getAsReactionCode()).thenReturn(emojiCode);
        when(reaction.getEmoji()).thenReturn(emoji);
        when(event.getReaction()).thenReturn(reaction);
        when(event.getUserIdLong()).thenReturn(userId);

        return event;
    }
}
