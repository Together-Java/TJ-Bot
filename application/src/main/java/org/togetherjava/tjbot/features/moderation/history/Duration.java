package org.togetherjava.tjbot.features.moderation.history;

/**
 * Holds duration constants used for both purge history command and message history routine.
 */
public enum Duration {
    // All new/existing options for duration in PurgeHistoryCommand should be within max and min
    // duration
    PURGE_HISTORY_MAX_DURATION(24),
    PURGE_HISTORY_MIN_DURATION(1),
    PURGE_HISTORY_THREE_HOURS(3),
    PURGE_HISTORY_SIX_HOURS(6),
    PURGE_HISTORY_TWELVE_HOURS(12),
    PURGE_HISTORY_TWENTY_FOUR_HOURS(24);

    private final int hours;

    Duration(int hours) {
        this.hours = hours;
    }

    public int getHours() {
        return hours;
    }
}
