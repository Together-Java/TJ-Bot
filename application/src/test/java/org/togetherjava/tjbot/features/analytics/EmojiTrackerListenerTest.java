package org.togetherjava.tjbot.features.analytics;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

final class EmojiTrackerListenerTest {
    private Metrics metrics;
    private EmojiTrackerListener listener;

    @BeforeEach
    void setUp() {
        metrics = mock(Metrics.class);
        listener = new EmojiTrackerListener(metrics);
    }

    @Test
    void tracksOnlyGuildEmojisUsedInMessages() {
        Guild guild = mock(Guild.class);
        CustomEmoji localEmoji = customEmoji(1L, false, "local");
        CustomEmoji externalEmoji = customEmoji(2L, true, "external");

        when(guild.getEmojiById(1L)).thenReturn(mock(RichCustomEmoji.class));
        when(guild.getEmojiById(2L)).thenReturn(null);

        listener.onMessageReceived(messageEvent(true, false, guild, localEmoji, externalEmoji));

        verify(metrics).count("emoji",
                Map.of("type", "message", "id", 1L, "animated", false, "name", "local"));
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void tracksOnlyGuildEmojisUsedAsReactions() {
        Guild guild = mock(Guild.class);
        CustomEmoji localEmoji = customEmoji(1L, false, "local");
        CustomEmoji externalEmoji = customEmoji(2L, true, "external");

        when(guild.getEmojiById(1L)).thenReturn(mock(RichCustomEmoji.class));
        when(guild.getEmojiById(2L)).thenReturn(null);

        listener.onMessageReactionAdd(reactionEvent(true, guild, Emoji.Type.CUSTOM, localEmoji));
        listener.onMessageReactionAdd(reactionEvent(true, guild, Emoji.Type.CUSTOM, externalEmoji));

        verify(metrics).count("emoji",
                Map.of("type", "reaction", "id", 1L, "animated", false, "name", "local"));
        verifyNoMoreInteractions(metrics);
    }

    @Test
    void ignoresUnicodeReactions() {
        listener.onMessageReactionAdd(reactionEvent(true, mock(Guild.class), Emoji.Type.UNICODE,
                null));

        verifyNoInteractions(metrics);
    }

    @Test
    void ignoresMessagesFromOutsideGuilds() {
        listener.onMessageReceived(messageEvent(false, false, null,
                customEmoji(1L, false, "external")));

        verifyNoInteractions(metrics);
    }

    @Test
    void ignoresWebhookMessages() {
        listener.onMessageReceived(messageEvent(true, true, mock(Guild.class),
                customEmoji(1L, false, "local")));

        verifyNoInteractions(metrics);
    }

    @Test
    void ignoresReactionsFromOutsideGuilds() {
        listener.onMessageReactionAdd(reactionEvent(false, null, Emoji.Type.CUSTOM,
                customEmoji(1L, false, "external")));

        verifyNoInteractions(metrics);
    }

    private static CustomEmoji customEmoji(long id, boolean animated, String name) {
        CustomEmoji emoji = mock(CustomEmoji.class);
        when(emoji.getIdLong()).thenReturn(id);
        when(emoji.isAnimated()).thenReturn(animated);
        when(emoji.getName()).thenReturn(name);
        return emoji;
    }

    private static MessageReceivedEvent messageEvent(boolean fromGuild, boolean webhook,
            Guild guild, CustomEmoji... emojis) {
        MessageReceivedEvent event = mock(MessageReceivedEvent.class);
        Message message = mock(Message.class);
        Mentions mentions = mock(Mentions.class);

        when(event.isFromGuild()).thenReturn(fromGuild);
        when(event.isWebhookMessage()).thenReturn(webhook);
        when(event.getMessage()).thenReturn(message);
        when(message.getMentions()).thenReturn(mentions);
        when(mentions.getCustomEmojis()).thenReturn(List.of(emojis));
        if (fromGuild) {
            when(event.getGuild()).thenReturn(guild);
        }

        return event;
    }

    private static MessageReactionAddEvent reactionEvent(boolean fromGuild, Guild guild,
            Emoji.Type type, CustomEmoji customEmoji) {
        MessageReactionAddEvent event = mock(MessageReactionAddEvent.class);
        EmojiUnion emoji = mock(EmojiUnion.class);

        when(event.isFromGuild()).thenReturn(fromGuild);
        when(emoji.getType()).thenReturn(type);
        if (type == Emoji.Type.CUSTOM) {
            when(emoji.asCustom()).thenReturn(customEmoji);
        }
        when(event.getEmoji()).thenReturn(emoji);
        if (fromGuild) {
            when(event.getGuild()).thenReturn(guild);
        }

        return event;
    }
}
