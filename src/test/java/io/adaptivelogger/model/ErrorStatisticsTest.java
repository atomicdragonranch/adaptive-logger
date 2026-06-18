package io.adaptivelogger.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ErrorStatisticsTest {

    @Test
    void constructorPopulatesAllFields() {
        Instant windowStart = Instant.parse("2026-01-01T00:00:00Z");
        Instant windowEnd = Instant.parse("2026-01-01T00:01:00Z");
        Instant lastEsc = Instant.parse("2026-01-01T00:00:30Z");

        ErrorStatistics stats = new ErrorStatistics(
            10, windowStart, windowEnd, true, 2, lastEsc
        );

        assertEquals(10, stats.getErrorCount());
        assertEquals(windowStart, stats.getWindowStart());
        assertEquals(windowEnd, stats.getWindowEnd());
        assertTrue(stats.isInErrorState());
        assertEquals(2, stats.getEscalationCount());
        assertEquals(lastEsc, stats.getLastEscalation());
    }

    @Test
    void notInErrorState() {
        ErrorStatistics stats = new ErrorStatistics(
            0, Instant.now(), Instant.now().plusSeconds(60),
            false, 0, null
        );

        assertFalse(stats.isInErrorState());
        assertEquals(0, stats.getEscalationCount());
        assertNull(stats.getLastEscalation());
    }

    @Test
    void toStringContainsFields() {
        ErrorStatistics stats = new ErrorStatistics(
            5, Instant.now(), Instant.now().plusSeconds(60),
            true, 1, Instant.now()
        );

        String str = stats.toString();
        assertTrue(str.contains("errorCount=5"));
        assertTrue(str.contains("inErrorState=true"));
        assertTrue(str.contains("escalationCount=1"));
    }
}
