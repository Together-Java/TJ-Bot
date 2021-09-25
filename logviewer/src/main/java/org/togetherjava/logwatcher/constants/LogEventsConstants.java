package org.togetherjava.logwatcher.constants;

/**
 * Constant Class for Logevents {@link org.togetherjava.logwatcher.entities.LogEvent}
 */
public final class LogEventsConstants {

    public static final String FIELD_INSTANT = "instant";
    public static final String FIELD_END_OF_BATCH = "endOfBatch";
    public static final String FIELD_LOGGER_NAME = "loggerName";
    public static final String FIELD_LOGGER_LEVEL = "level";
    public static final String FIELD_LOGGER_FQCN = "loggerFqcn";
    public static final String FIELD_MESSAGE = "message";
    public static final String FIELD_THREAD = "thread";
    public static final String FIELD_THREAD_ID = "threadId";
    public static final String FIELD_THREAD_PRIORITY = "threadPriority";

    public static final String TABLE = "APP_LOGS";


    /**
     * Contestants class, nothing to instantiate
     */
    private LogEventsConstants() {}
}
