package org.togetherjava.tjbot.features.analytics;

import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;

import org.togetherjava.tjbot.features.MessageReceiverAdapter;

/**
 * Listener that tracks custom emoji usage across all channels for analytics purposes.
 * <p>
 * Counts custom emojis used in messages and reactions so admins can see which emojis are unused and
 * should be removed.
 * <p>
 * Custom emojis are tracked by their Discord ID (e.g. {@code emoji-custom-123456789}). Animated
 * custom emojis are tracked separately (e.g. {@code emoji-custom-animated-123456789}).
 */
public final class EmojiTrackerListener extends MessageReceiverAdapter {
    private final Metrics metrics;

    /**
     * Creates a new listener to track emoji usage across all channels.
     *
     * @param metrics to track emoji usage events
     */
    public EmojiTrackerListener(Metrics metrics) {
        super();

        this.metrics = metrics;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.isWebhookMessage()) {
            return;
        }

        event.getMessage().getMentions().getCustomEmojis().forEach(this::trackCustomEmoji);
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        EmojiUnion emoji = event.getEmoji();
        if (emoji.getType() != Emoji.Type.CUSTOM) {
            return;
        }

        trackCustomEmoji(emoji.asCustom());
    }

    private void trackCustomEmoji(CustomEmoji emoji) {
        String prefix = emoji.isAnimated() ? "emoji-custom-animated-" : "emoji-custom-";
        metrics.count(prefix + emoji.getIdLong());
    }
}
