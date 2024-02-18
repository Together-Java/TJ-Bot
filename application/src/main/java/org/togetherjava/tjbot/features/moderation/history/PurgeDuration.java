package org.togetherjava.tjbot.features.moderation.history;

/**
 * Holds duration constants used for both purge history command and message history routine.
 */
public enum PurgeDuration {
    // All new/existing options for duration in PurgeHistoryCommand should be within max and min
    // duration
    MAX_DURATION(24),
    MIN_DURATION(1),
    THREE_HOURS(3),
    SIX_HOURS(6),
    TWELVE_HOURS(12),
    TWENTY_FOUR_HOURS(24);

    private final int hours;

    PurgeDuration(int hours) {
        this.hours = hours;
    }

    public int getHours() {
        return hours;
    }
}
