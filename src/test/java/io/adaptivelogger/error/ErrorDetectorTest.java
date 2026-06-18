package io.adaptivelogger.error;

import io.adaptivelogger.model.ErrorStatistics;
import io.adaptivelogger.model.LogEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ErrorDetectorTest {

    private ErrorDetectionConfig configWithThreshold(int threshold) {
        return ErrorDetectionConfig.builder()
            .errorThreshold(threshold)
            .timeWindow(Duration.ofSeconds(60))
            .escalationCooldown(Duration.ZERO)
            .immediateEscalationOnCritical(false)
            .build();
    }

    private LogEvent errorEvent(String message) {
        return LogEvent.builder()
            .timestamp(Instant.now())
            .level(Level.ERROR)
            .format(message)
            .build();
    }

    @Test
    void startsInNonErrorState() {
        ErrorDetector detector = new ErrorDetector(configWithThreshold(5));
        assertFalse(detector.isInErrorState());
        assertFalse(detector.shouldEscalate());
    }

    @Test
    void escalatesAfterThresholdReached() {
        ErrorDetector detector = new ErrorDetector(configWithThreshold(3));

        detector.recordError(errorEvent("error 1"));
        detector.recordError(errorEvent("error 2"));
        assertFalse(detector.isInErrorState());

        detector.recordError(errorEvent("error 3"));
        assertTrue(detector.isInErrorState());
        assertTrue(detector.shouldEscalate());
    }

    @Test
    void doesNotEscalateBelowThreshold() {
        ErrorDetector detector = new ErrorDetector(configWithThreshold(5));

        for (int i = 0; i < 4; i++) {
            detector.recordError(errorEvent("error " + i));
        }

        assertFalse(detector.isInErrorState());
        assertFalse(detector.shouldEscalate());
    }

    @Test
    void resetErrorStateClearsState() {
        ErrorDetector detector = new ErrorDetector(configWithThreshold(2));

        detector.recordError(errorEvent("e1"));
        detector.recordError(errorEvent("e2"));
        assertTrue(detector.isInErrorState());

        detector.resetErrorState();
        assertFalse(detector.isInErrorState());
    }

    @Test
    void cooldownPreventsRepeatedEscalation() {
        ErrorDetectionConfig config = ErrorDetectionConfig.builder()
            .errorThreshold(1)
            .timeWindow(Duration.ofSeconds(60))
            .escalationCooldown(Duration.ofHours(1))
            .immediateEscalationOnCritical(false)
            .build();

        ErrorDetector detector = new ErrorDetector(config);
        detector.recordError(errorEvent("error"));

        assertTrue(detector.shouldEscalate());
        assertFalse(detector.shouldEscalate());
    }

    @Test
    void criticalErrorTriggersImmediateEscalation() {
        ErrorDetectionConfig config = ErrorDetectionConfig.builder()
            .errorThreshold(100)
            .timeWindow(Duration.ofSeconds(60))
            .escalationCooldown(Duration.ZERO)
            .immediateEscalationOnCritical(true)
            .addCriticalPattern("(?i).*OutOfMemoryError.*")
            .build();

        ErrorDetector detector = new ErrorDetector(config);
        detector.recordError(errorEvent("java.lang.OutOfMemoryError: Java heap space"));

        assertTrue(detector.isInErrorState());
    }

    @Test
    void nonCriticalErrorDoesNotTriggerImmediate() {
        ErrorDetectionConfig config = ErrorDetectionConfig.builder()
            .errorThreshold(100)
            .timeWindow(Duration.ofSeconds(60))
            .escalationCooldown(Duration.ZERO)
            .immediateEscalationOnCritical(true)
            .addCriticalPattern("(?i).*OutOfMemoryError.*")
            .build();

        ErrorDetector detector = new ErrorDetector(config);
        detector.recordError(errorEvent("Connection timeout"));

        assertFalse(detector.isInErrorState());
    }

    @Test
    void nullEventDoesNotCrashCriticalCheck() {
        ErrorDetectionConfig config = ErrorDetectionConfig.builder()
            .errorThreshold(100)
            .timeWindow(Duration.ofSeconds(60))
            .escalationCooldown(Duration.ZERO)
            .immediateEscalationOnCritical(true)
            .addCriticalPattern("(?i).*OutOfMemoryError.*")
            .build();

        ErrorDetector detector = new ErrorDetector(config);
        assertDoesNotThrow(() -> detector.recordError(null));
    }

    @Test
    void throwableMessageMatchesCriticalPattern() {
        ErrorDetectionConfig config = ErrorDetectionConfig.builder()
            .errorThreshold(100)
            .timeWindow(Duration.ofSeconds(60))
            .escalationCooldown(Duration.ZERO)
            .immediateEscalationOnCritical(true)
            .addCriticalPattern("(?i).*OutOfMemoryError.*")
            .build();

        LogEvent event = LogEvent.builder()
            .timestamp(Instant.now())
            .level(Level.ERROR)
            .format("Processing failed")
            .throwable(new OutOfMemoryError("Java heap space"))
            .build();

        ErrorDetector detector = new ErrorDetector(config);
        detector.recordError(event);

        assertTrue(detector.isInErrorState());
    }

    @Test
    void statisticsReflectState() {
        ErrorDetector detector = new ErrorDetector(configWithThreshold(2));
        detector.recordError(errorEvent("e1"));
        detector.recordError(errorEvent("e2"));
        detector.shouldEscalate();

        ErrorStatistics stats = detector.getState();
        assertEquals(2, stats.getErrorCount());
        assertTrue(stats.isInErrorState());
        assertEquals(1, stats.getEscalationCount());
        assertNotNull(stats.getLastEscalation());
        assertNotNull(stats.getWindowStart());
        assertNotNull(stats.getWindowEnd());
    }

    @Test
    void thresholdOfOneEscalatesImmediately() {
        ErrorDetector detector = new ErrorDetector(configWithThreshold(1));
        detector.recordError(errorEvent("single error"));

        assertTrue(detector.isInErrorState());
        assertTrue(detector.shouldEscalate());
    }
}
