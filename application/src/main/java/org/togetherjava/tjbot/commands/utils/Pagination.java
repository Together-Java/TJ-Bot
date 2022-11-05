package org.togetherjava.tjbot.commands.utils;

import net.dv8tion.jda.api.entities.emoji.Emoji;

/**
 * Utility for pagination.
 */
public class Pagination {
    public static final Emoji PREVIOUS_BUTTON_EMOJI = Emoji.fromUnicode("⬅");
    public static final Emoji NEXT_BUTTON_EMOJI = Emoji.fromUnicode("➡");

    private Pagination() {
        throw new UnsupportedOperationException("Utility class, construction not supported");
    }

    /**
     * If the number is less than min value it'll return the min value, if the number is greater
     * than max value it'll return the max value else it'll return the value.
     *
     * @param min minimum value (inclusive)
     * @param value the value to verify
     * @param max maximum value (inclusive)
     * @return a number between min and max inclusive
     */
    public static int clamp(int min, int value, int max) {
        return Math.min(Math.max(min, value), max);
    }
}
