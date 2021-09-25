package org.togetherjava.logwatcher.entities;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

public class InstantWrapper {

    private long epochSecond;
    private long nanoOfSecond;

    /**
     * @param epochSecond
     * @param nanoOfSecond
     */
    @JsonCreator
    public InstantWrapper(@JsonProperty("epochSecond") long epochSecond,
            @JsonProperty("nanoOfSecond") long nanoOfSecond) {
        super();
        this.epochSecond = epochSecond;
        this.nanoOfSecond = nanoOfSecond;
    }

    public long getEpochSecond() {
        return epochSecond;
    }

    public void setEpochSecond(long epochSecond) {
        this.epochSecond = epochSecond;
    }

    public long getNanoOfSecond() {
        return nanoOfSecond;
    }

    public void setNanoOfSecond(long nanoOfSecond) {
        this.nanoOfSecond = nanoOfSecond;
    }

    @Override
    public String toString() {
        final var instant = toInstant();

        return new SimpleDateFormat("yyy-MM-dd HH:mm:ss.SSS").format(Date.from(instant));
    }

    public Instant toInstant() {
        return Instant.ofEpochSecond(this.epochSecond, this.nanoOfSecond);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InstantWrapper other))
            return false;

        return epochSecond == other.epochSecond && nanoOfSecond != other.nanoOfSecond;
    }

    @Override
    public int hashCode() {
        int result = (int) (epochSecond ^ (epochSecond >>> 32));
        result = 31 * result + (int) (nanoOfSecond ^ (nanoOfSecond >>> 32));
        return result;
    }
}
