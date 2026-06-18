package io.adaptivelogger.model;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class LoggingStatisticsTest {

    @Test
    void builderPopulatesAllFields() {
        ErrorStatistics errorStats = new ErrorStatistics(
            3, Instant.now(), Instant.now().plusSeconds(60),
            true, 1, Instant.now()
        );

        LoggingStatistics stats = LoggingStatistics.builder()
            .loggerName("com.example.Test")
            .currentLevel(Level.DEBUG)
            .totalLogs(1000)
            .errorCount(5)
            .bufferSize(42)
            .bufferCapacity(100)
            .errorDetectorState(errorStats)
            .build();

        assertEquals("com.example.Test", stats.getLoggerName());
        assertEquals(Level.DEBUG, stats.getCurrentLevel());
        assertEquals(1000, stats.getTotalLogs());
        assertEquals(5, stats.getErrorCount());
        assertEquals(42, stats.getBufferSize());
        assertEquals(100, stats.getBufferCapacity());
        assertSame(errorStats, stats.getErrorDetectorState());
    }

    @Test
    void bufferUtilizationCalculation() {
        LoggingStatistics stats = LoggingStatistics.builder()
            .bufferSize(75)
            .bufferCapacity(100)
            .build();

        assertEquals(0.75, stats.getBufferUtilization(), 0.001);
    }

    @Test
    void bufferUtilizationZeroCapacity() {
        LoggingStatistics stats = LoggingStatistics.builder()
            .bufferSize(0)
            .bufferCapacity(0)
            .build();

        assertEquals(0.0, stats.getBufferUtilization(), 0.001);
    }

    @Test
    void bufferUtilizationFull() {
        LoggingStatistics stats = LoggingStatistics.builder()
            .bufferSize(100)
            .bufferCapacity(100)
            .build();

        assertEquals(1.0, stats.getBufferUtilization(), 0.001);
    }

    @Test
    void toStringContainsFields() {
        LoggingStatistics stats = LoggingStatistics.builder()
            .loggerName("test")
            .currentLevel(Level.INFO)
            .totalLogs(50)
            .errorCount(2)
            .bufferSize(10)
            .bufferCapacity(100)
            .build();

        String str = stats.toString();
        assertTrue(str.contains("test"));
        assertTrue(str.contains("INFO"));
        assertTrue(str.contains("50"));
    }
}
