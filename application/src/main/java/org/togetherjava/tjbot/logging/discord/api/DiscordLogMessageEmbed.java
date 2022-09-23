package org.togetherjava.tjbot.logging.discord.api;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * The Jackson representation of Discords Webhook embeds: <a href=
 * "https://discord.com/developers/docs/resources/webhook#execute-webhook-jsonform-params">API
 * Documentation</a>
 */
public record DiscordLogMessageEmbed(DiscordLogMessageEmbedAuthor author, String title,
        String description, int color, String timestamp) {

    /**
     * Has to be small enough for fitting all embeds in {@link DiscordLogBatch} into the total
     * character length of ~6000.
     */
    private static final int MAX_EMBED_DESCRIPTION = 1_000;
    private static final Map<Level, Integer> LEVEL_TO_AMBIENT_COLOR =
            Map.of(Level.TRACE, 0x00B362, Level.DEBUG, 0x00A5CE, Level.INFO, 0xAC59FF, Level.WARN,
                    0xDFDF00, Level.ERROR, 0xBF2200, Level.FATAL, 0xFF8484);

    /**
     * Creates an instance representing the information from the given log event.
     *
     * @param event the event to extract log info from
     * @return an instance representing the given log
     */
    public static DiscordLogMessageEmbed ofEvent(LogEvent event) {
        String authorName = event.getLoggerName();
        String title = event.getLevel().name();
        int colorDecimal = Objects.requireNonNull(LEVEL_TO_AMBIENT_COLOR.get(event.getLevel()));
        String description =
                abbreviate(event.getMessage().getFormattedMessage(), MAX_EMBED_DESCRIPTION);
        String timestampIsoText =
                Instant.ofEpochMilli(event.getInstant().getEpochMillisecond()).toString();

        DiscordLogMessageEmbedAuthor author = new DiscordLogMessageEmbedAuthor(authorName);

        return new DiscordLogMessageEmbed(author, title, description, colorDecimal,
                timestampIsoText);
    }

    private static String abbreviate(String text, int maxLength) {
        if (text.length() < maxLength) {
            return text;
        }

        if (maxLength < 3) {
            return text.substring(0, maxLength);
        }

        return text.substring(0, maxLength - 3) + "...";
    }
}
