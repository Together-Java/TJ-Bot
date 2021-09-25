package org.togetherjava.logwatcher.entities;

import org.springframework.data.jpa.domain.AbstractPersistable;
import org.togetherjava.logwatcher.constants.LogEventsConstants;

import javax.persistence.Entity;
import javax.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = LogEventsConstants.TABLE)
public class LogEvent extends AbstractPersistable<Integer> {

    private Instant instant;

    private String thread;

    private String level;

    private String loggerName;

    private String message;

    private Boolean endOfBatch;

    private String loggerFqcn;

    private Integer threadId;

    private Integer threadPriority;

    public LogEvent() { /* Empty constructor for serialisation */ }

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
        return "LogEvent{" + "instant=" + instant + ", thread='" + thread + '\'' + ", level='"
                + level + '\'' + ", loggerName='" + loggerName + '\'' + ", message='" + message
                + '\'' + ", endOfBatch=" + endOfBatch + ", loggerFqcn='" + loggerFqcn + '\''
                + ", threadId=" + threadId + ", threadPriority=" + threadPriority + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof LogEvent other))
            return false;

        return Objects.equals(instant, other.instant) && Objects.equals(thread, other.thread)
                && Objects.equals(level, other.level)
                && Objects.equals(loggerName, other.loggerName)
                && Objects.equals(message, other.message)
                && Objects.equals(endOfBatch, other.endOfBatch)
                && Objects.equals(loggerFqcn, other.loggerFqcn)
                && Objects.equals(threadId, other.threadId)
                && Objects.equals(threadPriority, other.threadPriority);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), instant, thread, level, loggerName, message,
                endOfBatch, loggerFqcn, threadId, threadPriority);
    }
}
