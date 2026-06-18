package io.adaptivelogger.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TimeBasedRateLimiterTest {

    @Test
    void firstMessageAlwaysAllowed() {
        TimeBasedRateLimiter limiter = new TimeBasedRateLimiter();
        assertTrue(limiter.shouldLog("key", Duration.ofSeconds(30)));
    }

    @Test
    void secondMessageWithinWindowIsSuppressed() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        TimeBasedRateLimiter limiter = new TimeBasedRateLimiter(fixedClock);

        assertTrue(limiter.shouldLog("key", Duration.ofSeconds(30)));
        assertFalse(limiter.shouldLog("key", Duration.ofSeconds(30)));
    }

    @Test
    void messageAllowedAfterWindowExpires() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");

        // First call at t=0
        Clock clock1 = Clock.fixed(start, ZoneOffset.UTC);
        TimeBasedRateLimiter limiter = new TimeBasedRateLimiter(clock1);
        assertTrue(limiter.shouldLog("key", Duration.ofSeconds(10)));

        // The limiter uses its clock reference, so we need a mutable clock approach
        // Since TimeBasedRateLimiter stores the clock, we can test with real time
        // Instead, test behavior with separate keys or accept the design
        // For a real time-based test, use short window:
        TimeBasedRateLimiter realLimiter = new TimeBasedRateLimiter();
        assertTrue(realLimiter.shouldLog("test-key", Duration.ofMillis(50)));
        assertFalse(realLimiter.shouldLog("test-key", Duration.ofMillis(50)));

        try { Thread.sleep(60); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        assertTrue(realLimiter.shouldLog("test-key", Duration.ofMillis(50)));
    }

    @Test
    void differentKeysAreIndependent() {
        TimeBasedRateLimiter limiter = new TimeBasedRateLimiter();

        assertTrue(limiter.shouldLog("key-a", Duration.ofSeconds(30)));
        assertTrue(limiter.shouldLog("key-b", Duration.ofSeconds(30)));

        assertFalse(limiter.shouldLog("key-a", Duration.ofSeconds(30)));
        assertFalse(limiter.shouldLog("key-b", Duration.ofSeconds(30)));
    }

    @Test
    void suppressedCountTracking() {
        TimeBasedRateLimiter limiter = new TimeBasedRateLimiter();

        assertEquals(0, limiter.getSuppressedCount("key"));
        limiter.shouldLog("key", Duration.ofSeconds(60));

        limiter.recordSuppressed("key");
        limiter.recordSuppressed("key");
        limiter.recordSuppressed("key");

        assertEquals(3, limiter.getSuppressedCount("key"));
        assertEquals(3, limiter.getTotalSuppressedCount());
    }

    @Test
    void suppressedCountForUnknownKeyIsZero() {
        TimeBasedRateLimiter limiter = new TimeBasedRateLimiter();
        assertEquals(0, limiter.getSuppressedCount("nonexistent"));
    }

    @Test
    void recordSuppressedForUnknownKeyIsNoOp() {
        TimeBasedRateLimiter limiter = new TimeBasedRateLimiter();
        assertDoesNotThrow(() -> limiter.recordSuppressed("nonexistent"));
    }

    @Test
    void resetClearsAllState() {
        TimeBasedRateLimiter limiter = new TimeBasedRateLimiter();
        limiter.shouldLog("key-a", Duration.ofSeconds(60));
        limiter.shouldLog("key-b", Duration.ofSeconds(60));
        limiter.recordSuppressed("key-a");

        limiter.reset();

        assertEquals(0, limiter.getTrackedKeyCount());
        assertEquals(0, limiter.getTotalSuppressedCount());
        assertTrue(limiter.shouldLog("key-a", Duration.ofSeconds(60)));
    }

    @Test
    void trackedKeyCount() {
        TimeBasedRateLimiter limiter = new TimeBasedRateLimiter();
        assertEquals(0, limiter.getTrackedKeyCount());

        limiter.shouldLog("a", Duration.ofSeconds(10));
        limiter.shouldLog("b", Duration.ofSeconds(10));
        limiter.shouldLog("c", Duration.ofSeconds(10));

        assertEquals(3, limiter.getTrackedKeyCount());
    }

    @Test
    void summaryWithNoSuppressedMessages() {
        TimeBasedRateLimiter limiter = new TimeBasedRateLimiter();
        assertEquals("No messages suppressed", limiter.getSuppressedSummary(false));
    }

    @Test
    void summaryWithSuppressedMessages() {
        TimeBasedRateLimiter limiter = new TimeBasedRateLimiter();
        limiter.shouldLog("errors", Duration.ofSeconds(60));
        limiter.recordSuppressed("errors");
        limiter.recordSuppressed("errors");

        String summary = limiter.getSuppressedSummary(false);
        assertTrue(summary.contains("errors=2 suppressed"));
    }

    @Test
    void summaryWithResetClearsCounts() {
        TimeBasedRateLimiter limiter = new TimeBasedRateLimiter();
        limiter.shouldLog("key", Duration.ofSeconds(60));
        limiter.recordSuppressed("key");

        String summary = limiter.getSuppressedSummary(true);
        assertTrue(summary.contains("key=1 suppressed"));

        assertEquals(0, limiter.getSuppressedCount("key"));
    }

    @Test
    void cleanupRemovesOldEntries() {
        TimeBasedRateLimiter limiter = new TimeBasedRateLimiter();
        limiter.shouldLog("old-key", Duration.ofMillis(1));

        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        limiter.shouldLog("new-key", Duration.ofSeconds(60));
        limiter.cleanupOldEntries(Duration.ofMillis(25));

        assertEquals(1, limiter.getTrackedKeyCount());
    }

    @Test
    void nullAndInvalidParametersAllowLogging() {
        TimeBasedRateLimiter limiter = new TimeBasedRateLimiter();
        assertTrue(limiter.shouldLog(null, Duration.ofSeconds(10)));
        assertTrue(limiter.shouldLog("key", null));
        assertTrue(limiter.shouldLog("key", Duration.ZERO));
        assertTrue(limiter.shouldLog("key", Duration.ofSeconds(-1)));
    }

    @Test
    void concurrentAccessDoesNotCorrupt() throws Exception {
        TimeBasedRateLimiter limiter = new TimeBasedRateLimiter();
        int threads = 8;
        int opsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger allowed = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                for (int i = 0; i < opsPerThread; i++) {
                    if (limiter.shouldLog("shared-key", Duration.ofMillis(1))) {
                        allowed.incrementAndGet();
                    }
                }
                latch.countDown();
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertTrue(allowed.get() >= 1);
        assertTrue(allowed.get() < threads * opsPerThread);
    }
}
