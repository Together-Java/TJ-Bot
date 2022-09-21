package org.togetherjava.tjbot.logging.discord;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The Jackson representation of Discords Webhook messages: <a href=
 * "https://discord.com/developers/docs/resources/webhook#execute-webhook-jsonform-params">API
 * Documentation</a>
 */
record DiscordLogMessage(List<DiscordLogMessageEmbed> embeds) {
    private static final Map<Level, Integer> LEVEL_TO_AMBIENT_COLOR =
            Map.of(Level.TRACE, 0x00B362, Level.DEBUG, 0x00A5CE, Level.INFO, 0xAC59FF, Level.WARN,
                    0xDFDF00, Level.ERROR, 0xBF2200, Level.FATAL, 0xFF8484);

    static DiscordLogMessage ofEvent(LogEvent event) {
        String authorName = event.getLoggerName();
        String title = event.getLevel().name();
        int colorDecimal = Objects.requireNonNull(LEVEL_TO_AMBIENT_COLOR.get(event.getLevel()));
        String description = event.getMessage().getFormattedMessage();
        String timestampIsoText =
                Instant.ofEpochMilli(event.getInstant().getEpochMillisecond()).toString();

        DiscordLogMessageEmbedAuthor author = new DiscordLogMessageEmbedAuthor(authorName);
        DiscordLogMessageEmbed embed = new DiscordLogMessageEmbed(author, title, description,
                colorDecimal, timestampIsoText);
        return new DiscordLogMessage(List.of(embed));
    }
}
