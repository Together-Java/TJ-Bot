package org.togetherjava.tjbot.logwatcher.util;

import org.togetherjava.tjbot.db.generated.tables.pojos.Logevents;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class LogUtils {

    public enum LogLevel {
        INFO,
        WARN,
        ERROR,
        DEBUG,
        TRACE;

        public static Set<LogLevel> getAll() {
            return EnumSet.allOf(LogLevel.class);
        }

        public static Set<String> getAllNames() {
            return Arrays.stream(values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());
        }
    }

    public static String logLevelToCssClass(final Logevents event) {
        return event.getLevel().toLowerCase(Locale.ENGLISH);
    }



    private LogUtils() {}
}
