package org.togetherjava.tjbot.features.utils;

import java.awt.Color;

/**
 * Provides ambient colors used to visually group Discord embeds by feature.
 */
public final class AmbientColors {
    private AmbientColors() {
        throw new UnsupportedOperationException();
    }

    public static final Color BOOKMARK_SUCCESS = Color.decode("#A6DA95");
    public static final Color BOOKMARK_WARNING = Color.decode("#F5A97F");
    public static final Color BOOKMARK_FAILURE = Color.decode("#EE99A0");
    public static final Color CHAT_GPT_PROGRESS = Color.GRAY;
    public static final Color CODE = Color.decode("#FDFD96");
    public static final Color HELP = Color.decode("#FFFFA5");
    public static final Color HELP_CHAT_GPT_RESPONSE = Color.PINK;
    public static final Color JSHELL_RENDER_FAILURE = Color.RED;
    public static final Color MEDIA_WARNING = Color.ORANGE;
    public static final Color MESSAGE_MANAGEMENT = Color.decode("#186DDD");
    public static final Color MODERATION = Color.decode("#895FE8");
    public static final Color MODERATION_AUDIT_ACTION = Color.decode("#4FC3F7");
    public static final Color MODERATION_AUDIT_LOG = Color.decode("#3788AC");
    public static final Color MODERATION_SCAM = Color.decode("#CFBFF5");
    public static final Color MODMAIL = Color.BLACK;
    public static final Color PURGE_CONFIRMATION = Color.RED;
    public static final Color QUESTION_TRANSFER = Color.decode("#32A4A8");
    public static final Color REMINDER = Color.decode("#F7F492");
    public static final Color ROLE_MANAGEMENT = Color.decode("#18DD88");
    public static final Color TAGS = Color.decode("#FA8072");
    public static final Color WOLFRAM_ALPHA = Color.decode("#4290F5");

    // Discord webhook embeds expect raw RGB integers instead of Color instances.
    public static final int LOG_RAW_TRACE = 0x00B362;
    public static final int LOG_RAW_DEBUG = 0x00A5CE;
    public static final int LOG_RAW_INFO = 0xAC59FF;
    public static final int LOG_RAW_WARN = 0xDFDF00;
    public static final int LOG_RAW_ERROR = 0xBF2200;
    public static final int LOG_RAW_FATAL = 0xFF8484;
}
