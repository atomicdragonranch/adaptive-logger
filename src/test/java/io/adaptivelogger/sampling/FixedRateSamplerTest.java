package io.adaptivelogger.sampling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FixedRateSamplerTest {

    @Test
    void rate100PercentSamplesEverything() {
        FixedRateSampler sampler = new FixedRateSampler(1.0);
        for (int i = 0; i < 100; i++) {
            assertTrue(sampler.shouldSample("key"));
        }
    }

    @Test
    void rate0PercentSamplesNothing() {
        FixedRateSampler sampler = new FixedRateSampler(0.0);
        for (int i = 0; i < 100; i++) {
            assertFalse(sampler.shouldSample("key"));
        }
    }

    @Test
    void rate50PercentSamplesApproximatelyHalf() {
        FixedRateSampler sampler = new FixedRateSampler(0.5);
        int sampled = 0;
        int total = 10000;

        for (int i = 0; i < total; i++) {
            if (sampler.shouldSample("key")) {
                sampled++;
            }
        }

        double actualRate = (double) sampled / total;
        assertEquals(0.5, actualRate, 0.05);
    }

    @Test
    void rate10PercentSamplesApproximatelyTenPercent() {
        FixedRateSampler sampler = new FixedRateSampler(0.1);
        int sampled = 0;
        int total = 10000;

        for (int i = 0; i < total; i++) {
            if (sampler.shouldSample("key")) {
                sampled++;
            }
        }

        double actualRate = (double) sampled / total;
        assertEquals(0.1, actualRate, 0.03);
    }

    @Test
    void nullKeyAlwaysSamples() {
        FixedRateSampler sampler = new FixedRateSampler(0.0);
        assertTrue(sampler.shouldSample(null));
    }

    @Test
    void invalidRateThrows() {
        assertThrows(IllegalArgumentException.class, () -> new FixedRateSampler(-0.1));
        assertThrows(IllegalArgumentException.class, () -> new FixedRateSampler(1.1));
    }

    @Test
    void getRateReturnsConfiguredValue() {
        FixedRateSampler sampler = new FixedRateSampler(0.42);
        assertEquals(0.42, sampler.getRate(), 0.001);
    }

    @Test
    void suppressedCountTracking() {
        FixedRateSampler sampler = new FixedRateSampler(0.5);
        sampler.recordSuppressed("key");
        sampler.recordSuppressed("key");

        assertEquals(2, sampler.getSuppressedCount("key"));
        assertEquals(2, sampler.getTotalSuppressedCount());
    }

    @Test
    void suppressedCountForNullKeyIsZero() {
        FixedRateSampler sampler = new FixedRateSampler(0.5);
        assertEquals(0, sampler.getSuppressedCount(null));
    }

    @Test
    void recordSuppressedNullKeyIsNoOp() {
        FixedRateSampler sampler = new FixedRateSampler(0.5);
        assertDoesNotThrow(() -> sampler.recordSuppressed(null));
    }

    @Test
    void resetClearsSuppressedCounts() {
        FixedRateSampler sampler = new FixedRateSampler(0.5);
        sampler.recordSuppressed("key");
        sampler.recordSuppressed("key");

        sampler.reset();
        assertEquals(0, sampler.getSuppressedCount("key"));
    }

    @Test
    void trackedKeyCount() {
        FixedRateSampler sampler = new FixedRateSampler(0.5);
        sampler.recordSuppressed("a");
        sampler.recordSuppressed("b");

        assertEquals(2, sampler.getTrackedKeyCount());
    }

    @Test
    void summaryWithNoSuppressed() {
        FixedRateSampler sampler = new FixedRateSampler(0.5);
        assertEquals("No messages sampled out", sampler.getSuppressedSummary(false));
    }

    @Test
    void summaryWithSuppressed() {
        FixedRateSampler sampler = new FixedRateSampler(0.5);
        sampler.recordSuppressed("errors");

        String summary = sampler.getSuppressedSummary(false);
        assertTrue(summary.contains("errors"));
        assertTrue(summary.contains("sampled out"));
    }

    @Test
    void cleanupRemovesOldEntries() {
        FixedRateSampler sampler = new FixedRateSampler(0.5);
        sampler.recordSuppressed("old-key");

        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        sampler.recordSuppressed("new-key");
        int removed = sampler.cleanup(25);

        assertEquals(1, removed);
        assertEquals(1, sampler.getTrackedKeyCount());
    }

    @Test
    void cleanupWithInvalidMaxAgeReturnsZero() {
        FixedRateSampler sampler = new FixedRateSampler(0.5);
        sampler.shouldSample("key");
        assertEquals(0, sampler.cleanup(0));
        assertEquals(0, sampler.cleanup(-1));
    }

    @Test
    void differentKeysAreIndependent() {
        FixedRateSampler sampler = new FixedRateSampler(0.5);
        sampler.recordSuppressed("a");
        sampler.recordSuppressed("b");
        sampler.recordSuppressed("b");

        assertEquals(1, sampler.getSuppressedCount("a"));
        assertEquals(2, sampler.getSuppressedCount("b"));
        assertEquals(3, sampler.getTotalSuppressedCount());
    }
}
