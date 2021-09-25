package org.togetherjava.logwatcher.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.togetherjava.logwatcher.constants.LogEventsConstants;

import java.time.Instant;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class LogDTO {

    private Instant instant;

    private String thread;

    private String level;

    private String loggerName;

    private String message;

    private Boolean endOfBatch;

    private String loggerFqcn;

    private Integer threadId;

    private Integer threadPriority;

    @JsonCreator
    public LogDTO(@JsonProperty(LogEventsConstants.FIELD_INSTANT) InstantWrapper wrapper,
            @JsonProperty(LogEventsConstants.FIELD_THREAD) String thread,
            @JsonProperty(LogEventsConstants.FIELD_LOGGER_LEVEL) String level,
            @JsonProperty(LogEventsConstants.FIELD_LOGGER_NAME) String loggerName,
            @JsonProperty(LogEventsConstants.FIELD_MESSAGE) String message,
            @JsonProperty(LogEventsConstants.FIELD_END_OF_BATCH) Boolean endOfBatch,
            @JsonProperty(LogEventsConstants.FIELD_LOGGER_FQCN) String loggerFqcn,
            @JsonProperty(LogEventsConstants.FIELD_THREAD_ID) Integer threadId,
            @JsonProperty(LogEventsConstants.FIELD_THREAD_PRIORITY) Integer threadPriority) {
        this.instant = wrapper.toInstant();
        this.thread = thread;
        this.level = level;
        this.loggerName = loggerName;
        this.message = message;
        this.endOfBatch = endOfBatch;
        this.loggerFqcn = loggerFqcn;
        this.threadId = threadId;
        this.threadPriority = threadPriority;
    }

    public LogEvent toLogEvent() {
        final LogEvent logEvent = new LogEvent();

        logEvent.setInstant(getInstant());
        logEvent.setThread(getThread());
        logEvent.setLevel(getLevel());
        logEvent.setLoggerName(getLoggerName());
        logEvent.setMessage(getMessage());
        logEvent.setEndOfBatch(getEndOfBatch());
        logEvent.setLoggerFqcn(getLoggerFqcn());
        logEvent.setThreadId(getThreadId());
        logEvent.setThreadPriority(getThreadPriority());

        return logEvent;
    }


    public Instant getInstant() {
        return instant;
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    public String getThread() {
        return thread;
    }

    public void setThread(String thread) {
        this.thread = thread;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getLoggerName() {
        return loggerName;
    }

    public void setLoggerName(String loggerName) {
        this.loggerName = loggerName;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Boolean getEndOfBatch() {
        return endOfBatch;
    }

    public void setEndOfBatch(Boolean endOfBatch) {
        this.endOfBatch = endOfBatch;
    }

    public String getLoggerFqcn() {
        return loggerFqcn;
    }

    public void setLoggerFqcn(String loggerFqcn) {
        this.loggerFqcn = loggerFqcn;
    }

    public Integer getThreadId() {
        return threadId;
    }

    public void setThreadId(Integer threadId) {
        this.threadId = threadId;
    }

    public Integer getThreadPriority() {
        return threadPriority;
    }

    public void setThreadPriority(Integer threadPriority) {
        this.threadPriority = threadPriority;
    }

    @Override
    public String toString() {
        return "LogDTO{" + "instant=" + instant + ", thread='" + thread + '\'' + ", level='" + level
                + '\'' + ", loggerName='" + loggerName + '\'' + ", message='" + message + '\''
                + ", endOfBatch=" + endOfBatch + ", loggerFqcn='" + loggerFqcn + '\''
                + ", threadId=" + threadId + ", threadPriority=" + threadPriority + '}';
    }

}
