package org.togetherjava.tjbot.features.tags;

/**
 * The style of a tag content.
 */
public enum TagContentStyle {
    /**
     * Content that will be interpreted by Discord, for example a message containing {@code **foo**}
     * will be displayed in <b>bold</b>.
     */
    INTERPRETED,
    /**
     * Content that will be displayed raw, not interpreted by Discord. For example a message
     * containing {@code **foo**} will be displayed as {@code **foo**} literally, by escaping the
     * special characters.
     */
    RAW
}
