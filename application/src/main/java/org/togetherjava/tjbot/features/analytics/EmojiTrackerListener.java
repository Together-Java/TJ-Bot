package org.togetherjava.tjbot.features.analytics;

import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;

import org.togetherjava.tjbot.features.MessageReceiverAdapter;

import java.util.regex.Pattern;

/**
 * Listener that tracks emoji usage across all channels for analytics purposes.
 * <p>
 * Counts emojis used in messages and reactions so admins can see which emojis are unused and should
 * be removed.
 * <p>
 * Custom emojis are tracked by their Discord ID (e.g. {@code emoji-custom-123456789}) rather than
 * by name, since emoji names are not unique and may change over time. Animated custom emojis are
 * tracked separately (e.g. {@code emoji-custom-animated-123456789}). Unicode emojis are tracked by
 * name (e.g. {@code emoji-unicode-thumbsup}).
 */
public final class EmojiTrackerListener extends MessageReceiverAdapter {
    private static final Pattern ALL_CHANNELS = Pattern.compile(".*");

    private final Metrics metrics;

    /**
     * Creates a new listener to track emoji usage across all channels.
     *
     * @param metrics to track emoji usage events
     */
    public EmojiTrackerListener(Metrics metrics) {
        super(ALL_CHANNELS);

        this.metrics = metrics;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }

        event.getMessage().getMentions().getCustomEmojis().forEach(this::trackCustomEmoji);
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (event.getUser() != null && event.getUser().isBot()) {
            return;
        }

        trackEmojiUnion(event.getEmoji());
    }

    private void trackEmojiUnion(EmojiUnion emoji) {
        if (emoji.getType() == Emoji.Type.CUSTOM) {
            trackCustomEmoji(emoji.asCustom());
        } else {
            metrics.count("emoji-unicode-" + emoji.asUnicode().getName());
        }
    }

    private void trackCustomEmoji(CustomEmoji emoji) {
        String prefix = emoji.isAnimated() ? "emoji-custom-animated-" : "emoji-custom-";
        metrics.count(prefix + emoji.getIdLong());
    }
}
