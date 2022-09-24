package org.togetherjava.tjbot.logging;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

public final class LogMarkers {
    /**
     * Log should not be forwarded to Discord.
     */
    public static Marker NO_DISCORD = MarkerFactory.getMarker("NO_DISCORD");
    /**
     * The log contains sensitive information that only moderators and people with similar authority
     * should be allowed to view.
     */
    public static Marker SENSITIVE = MarkerFactory.getMarker("SENSITIVE");

    private LogMarkers() {
        throw new UnsupportedOperationException("Utility class");
    }
}
