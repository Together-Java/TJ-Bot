package org.togetherjava.logwatcher.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class LogEvent {

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
    public LogEvent(@JsonProperty("instant") InstantWrapper wrapper,
            @JsonProperty("thread") String thread, @JsonProperty("level") String level,
            @JsonProperty("loggerName") String loggerName, @JsonProperty("message") String message,
            @JsonProperty("endOfBatch") Boolean endOfBatch,
            @JsonProperty("loggerFqcn") String loggerFqcn,
            @JsonProperty("threadId") Integer threadId,
            @JsonProperty("threadPriority") Integer threadPriority) {
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
