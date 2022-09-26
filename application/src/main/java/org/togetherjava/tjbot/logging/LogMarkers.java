package org.togetherjava.tjbot.logging;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Markers to be used for tagging logs. Loggers can then be filter based on them.
 */
public final class LogMarkers {
    /**
     * Log should not be forwarded to Discord.
     */
    public static final Marker NO_DISCORD = MarkerFactory.getMarker("NO_DISCORD");
    /**
     * The log contains sensitive information that only moderators and people with similar authority
     * should be allowed to view.
     */
    public static final Marker SENSITIVE = MarkerFactory.getMarker("SENSITIVE");

    private LogMarkers() {
        throw new UnsupportedOperationException("Utility class");
    }
}
