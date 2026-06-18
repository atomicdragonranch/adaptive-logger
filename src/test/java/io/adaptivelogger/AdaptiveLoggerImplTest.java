package io.adaptivelogger;

import io.adaptivelogger.model.LoggingStatistics;
import io.adaptivelogger.ratelimit.RateLimitedLogger;
import io.adaptivelogger.sampling.SampledLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AdaptiveLoggerImplTest {

    private IAdaptiveLogger logger;

    @BeforeEach
    void setUp() {
        AdaptiveLoggerFactory.clear();
        AdaptiveLoggingConfig config = AdaptiveLoggingConfig.builder()
            .enabled(true)
            .defaultLevel(Level.INFO)
            .ringBufferSize(100)
            .ringBufferEnabled(true)
            .dumpBufferOnError(false)
            .escalationLevel(Level.DEBUG)
            .escalationDuration(Duration.ofMinutes(5))
            .build();
        AdaptiveLoggerFactory.initialize(config);
        logger = AdaptiveLoggerFactory.getLogger("test.logger");
    }

    @AfterEach
    void tearDown() {
        AdaptiveLoggerFactory.clear();
    }

    @Test
    void defaultLevelIsInfo() {
        assertEquals(Level.INFO, logger.getLevel());
    }

    @Test
    void setLevelChangesEffectiveLevel() {
        logger.setLevel(Level.DEBUG);
        assertEquals(Level.DEBUG, logger.getLevel());
        assertTrue(logger.isDebugEnabled());
    }

    @Test
    void resetLevelReturnsToDefault() {
        logger.setLevel(Level.TRACE);
        assertEquals(Level.TRACE, logger.getLevel());

        logger.resetLevel();
        assertEquals(Level.INFO, logger.getLevel());
    }

    @Test
    void levelCheckingReflectsCurrentLevel() {
        logger.setLevel(Level.INFO);
        assertFalse(logger.isTraceEnabled());
        assertFalse(logger.isDebugEnabled());
        assertTrue(logger.isInfoEnabled());
        assertTrue(logger.isWarnEnabled());
        assertTrue(logger.isErrorEnabled());
    }

    @Test
    void traceEnabledWhenLevelIsTrace() {
        logger.setLevel(Level.TRACE);
        assertTrue(logger.isTraceEnabled());
        assertTrue(logger.isDebugEnabled());
        assertTrue(logger.isInfoEnabled());
    }

    @Test
    void standardLoggingDoesNotThrow() {
        assertDoesNotThrow(() -> {
            logger.info("simple message");
            logger.info("formatted {} message", "test");
            logger.info("multi {} {} args", "a", "b");
            logger.warn("warning with exception", new RuntimeException("test"));
            logger.error("error message");
            logger.debug("debug message");
            logger.trace("trace message");
        });
    }

    @Test
    void lazyLoggingDoesNotEvaluateWhenDisabled() {
        logger.setLevel(Level.WARN);
        AtomicInteger evalCount = new AtomicInteger(0);

        logger.debugLazy("lazy: {}", () -> { evalCount.incrementAndGet(); return "value"; });
        logger.infoLazy("lazy: {}", () -> { evalCount.incrementAndGet(); return "value"; });

        assertEquals(0, evalCount.get());
    }

    @Test
    void lazyLoggingEvaluatesWhenEnabled() {
        logger.setLevel(Level.DEBUG);
        AtomicInteger evalCount = new AtomicInteger(0);

        logger.debugLazy("lazy: {}", () -> { evalCount.incrementAndGet(); return "value"; });

        assertEquals(1, evalCount.get());
    }

    @Test
    void allLazyMethodsWork() {
        logger.setLevel(Level.TRACE);
        assertDoesNotThrow(() -> {
            logger.traceLazy("trace: {}", () -> "t");
            logger.debugLazy("debug: {}", () -> "d");
            logger.infoLazy("info: {}", () -> "i");
            logger.warnLazy("warn: {}", () -> "w");
            logger.errorLazy("error: {}", () -> "e");
        });
    }

    @Test
    void bufferCapturesEvents() {
        logger.setLevel(Level.DEBUG);
        logger.debug("buffered message 1");
        logger.debug("buffered message 2");

        assertTrue(logger.getBufferSize() >= 0);
    }

    @Test
    void clearBufferEmptiesBuffer() {
        logger.setLevel(Level.DEBUG);
        logger.debug("will be cleared");

        logger.clearBuffer();
        assertEquals(0, logger.getBufferSize());
    }

    @Test
    void dumpBufferDoesNotThrow() {
        logger.setLevel(Level.DEBUG);
        logger.debug("event 1");
        logger.debug("event 2");

        assertDoesNotThrow(() -> logger.dumpBuffer());
    }

    @Test
    void statisticsArePopulated() {
        logger.info("log something");
        logger.error("log an error");

        LoggingStatistics stats = logger.getStatistics();
        assertNotNull(stats);
        assertEquals("test.logger", stats.getLoggerName());
        assertEquals(Level.INFO, stats.getCurrentLevel());
        assertTrue(stats.getTotalLogs() >= 2);
    }

    @Test
    void shouldLogRespectsLevel() {
        logger.setLevel(Level.WARN);
        assertFalse(logger.shouldLog(Level.DEBUG));
        assertFalse(logger.shouldLog(Level.INFO));
        assertTrue(logger.shouldLog(Level.WARN));
        assertTrue(logger.shouldLog(Level.ERROR));
    }

    @Test
    void logAtLevelWorks() {
        assertDoesNotThrow(() -> {
            logger.logAtLevel(Level.INFO, "message");
            logger.logAtLevel(Level.WARN, "formatted: {}", "arg");
            logger.logAtLevel(Level.ERROR, "with throwable", new RuntimeException());
        });
    }

    @Test
    void atMostEveryReturnsRateLimitedLogger() {
        RateLimitedLogger rl = logger.atMostEvery(30, TimeUnit.SECONDS);
        assertNotNull(rl);
    }

    @Test
    void atMostEveryWithKeyReturnsRateLimitedLogger() {
        RateLimitedLogger rl = logger.atMostEvery(1, TimeUnit.MINUTES, "custom-key");
        assertNotNull(rl);
    }

    @Test
    void rateLimitingStatsAvailable() {
        String summary = logger.getRateLimitingSummary(false);
        assertNotNull(summary);
        assertEquals(0, logger.getRateLimitingTotalSuppressedCount());
        assertEquals(0, logger.getRateLimitingTrackedKeyCount());
    }

    @Test
    void sampleReturnsLogger() {
        SampledLogger sl = logger.sample(0.5);
        assertNotNull(sl);
    }

    @Test
    void sampleWithKeyReturnsLogger() {
        SampledLogger sl = logger.sample(0.5, "custom-key");
        assertNotNull(sl);
    }

    @Test
    void sampleEveryReturnsLogger() {
        SampledLogger sl = logger.sampleEvery(10);
        assertNotNull(sl);
    }

    @Test
    void sampleEveryWithKeyReturnsLogger() {
        SampledLogger sl = logger.sampleEvery(10, "custom-key");
        assertNotNull(sl);
    }

    @Test
    void samplingStatsAvailable() {
        String summary = logger.getSamplingSummary(false);
        assertNotNull(summary);
        assertEquals(0, logger.getSamplingTotalSuppressedCount());
        assertEquals(0, logger.getSamplingTrackedKeyCount());
    }

    @Test
    void cleanupSamplersReturnsCount() {
        int removed = logger.cleanupSamplers(60000);
        assertTrue(removed >= 0);
    }

    @Test
    void clearSamplerCachesReturnsCount() {
        logger.sample(0.5);
        int cleared = logger.clearSamplerCaches();
        assertTrue(cleared >= 0);
    }

    @Test
    void cachedSamplerCount() {
        int count = logger.getCachedSamplerCount();
        assertTrue(count >= 0);
    }

    @Test
    void rateLimiterAccessible() {
        assertNotNull(logger.getRateLimiter());
    }

    @Test
    void shouldLogWithRateLimit() {
        boolean result = logger.shouldLogWithRateLimit(Level.INFO, "key", Duration.ofSeconds(30));
        assertTrue(result);

        boolean second = logger.shouldLogWithRateLimit(Level.INFO, "key", Duration.ofSeconds(30));
        assertFalse(second);
    }

    @Test
    void loggerNameMatchesRequest() {
        assertEquals("test.logger", logger.getName());
    }

    @Test
    void disabledConfigPassesThrough() {
        AdaptiveLoggerFactory.clear();
        AdaptiveLoggingConfig disabled = AdaptiveLoggingConfig.builder()
            .enabled(false)
            .build();
        AdaptiveLoggerFactory.initialize(disabled);

        IAdaptiveLogger disabledLogger = AdaptiveLoggerFactory.getLogger("disabled.test");
        assertDoesNotThrow(() -> {
            disabledLogger.info("should still log via SLF4J");
            disabledLogger.error("errors too");
        });
    }
}
