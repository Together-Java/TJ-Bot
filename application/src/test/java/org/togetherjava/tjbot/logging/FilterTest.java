package org.togetherjava.tjbot.logging;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.togetherjava.tjbot.feature.logging.FlaggedFilter;

final class FilterTest {

    private FlaggedFilter filter;
    private LogEvent event;

    @BeforeEach
    void setUp() {
        this.filter = FlaggedFilter.createFilter(Filter.Result.NEUTRAL, Filter.Result.DENY);
        this.event = Log4jLogEvent.newBuilder().build();
    }

    @Test
    void shouldPassFilter() {
        FlaggedFilter spy = Mockito.spy(this.filter);
        Mockito.when(spy.isLoggingEnabled()).thenReturn(true);
        Assertions.assertEquals(Filter.Result.NEUTRAL, spy.filter(this.event));
    }


    @Test
    void shouldNotPassFilter() {
        FlaggedFilter spy = Mockito.spy(this.filter);
        Mockito.when(spy.isLoggingEnabled()).thenReturn(false);
        Assertions.assertEquals(Filter.Result.DENY, spy.filter(this.event));
    }
}
