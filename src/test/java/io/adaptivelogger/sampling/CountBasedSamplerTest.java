package io.adaptivelogger.sampling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CountBasedSamplerTest {

    @Test
    void firstMessageAlwaysSampled() {
        CountBasedSampler sampler = new CountBasedSampler(10);
        assertTrue(sampler.shouldSample("key"));
    }

    @Test
    void everyNthMessageIsSampled() {
        CountBasedSampler sampler = new CountBasedSampler(5);
        // count=1: 1%5=1 -> true
        // count=2: 2%5=2 -> false
        // count=3: 3%5=3 -> false
        // count=4: 4%5=4 -> false
        // count=5: 5%5=0 -> false
        // count=6: 6%5=1 -> true

        assertTrue(sampler.shouldSample("key"));   // 1st
        assertFalse(sampler.shouldSample("key"));  // 2nd
        assertFalse(sampler.shouldSample("key"));  // 3rd
        assertFalse(sampler.shouldSample("key"));  // 4th
        assertFalse(sampler.shouldSample("key"));  // 5th
        assertTrue(sampler.shouldSample("key"));   // 6th (every 5th after first)
    }

    @Test
    void intervalOfOneLogsEverything() {
        CountBasedSampler sampler = new CountBasedSampler(1);
        for (int i = 0; i < 100; i++) {
            assertTrue(sampler.shouldSample("key"));
        }
    }

    @Test
    void differentKeysAreIndependent() {
        CountBasedSampler sampler = new CountBasedSampler(3);

        assertTrue(sampler.shouldSample("key-a"));   // a: count=1
        assertTrue(sampler.shouldSample("key-b"));   // b: count=1
        assertFalse(sampler.shouldSample("key-a"));  // a: count=2
        assertFalse(sampler.shouldSample("key-a"));  // a: count=3
        assertTrue(sampler.shouldSample("key-a"));   // a: count=4, 4%3=1
        assertFalse(sampler.shouldSample("key-b"));  // b: count=2
    }

    @Test
    void nullKeyAlwaysSamples() {
        CountBasedSampler sampler = new CountBasedSampler(10);
        for (int i = 0; i < 20; i++) {
            assertTrue(sampler.shouldSample(null));
        }
    }

    @Test
    void invalidIntervalThrows() {
        assertThrows(IllegalArgumentException.class, () -> new CountBasedSampler(0));
        assertThrows(IllegalArgumentException.class, () -> new CountBasedSampler(-1));
    }

    @Test
    void getIntervalReturnsConfiguredValue() {
        CountBasedSampler sampler = new CountBasedSampler(42);
        assertEquals(42, sampler.getInterval());
    }

    @Test
    void messageCountTracking() {
        CountBasedSampler sampler = new CountBasedSampler(10);
        assertEquals(0, sampler.getMessageCount("key"));

        for (int i = 0; i < 5; i++) {
            sampler.shouldSample("key");
        }

        assertEquals(5, sampler.getMessageCount("key"));
    }

    @Test
    void nullKeyMessageCountIsZero() {
        CountBasedSampler sampler = new CountBasedSampler(10);
        assertEquals(0, sampler.getMessageCount(null));
    }

    @Test
    void suppressedCountTracking() {
        CountBasedSampler sampler = new CountBasedSampler(10);
        sampler.recordSuppressed("key");
        sampler.recordSuppressed("key");
        sampler.recordSuppressed("key");

        assertEquals(3, sampler.getSuppressedCount("key"));
        assertEquals(3, sampler.getTotalSuppressedCount());
    }

    @Test
    void suppressedCountForNullKeyIsZero() {
        CountBasedSampler sampler = new CountBasedSampler(10);
        assertEquals(0, sampler.getSuppressedCount(null));
    }

    @Test
    void recordSuppressedNullKeyIsNoOp() {
        CountBasedSampler sampler = new CountBasedSampler(10);
        assertDoesNotThrow(() -> sampler.recordSuppressed(null));
    }

    @Test
    void resetClearsCountsButKeepsKeys() {
        CountBasedSampler sampler = new CountBasedSampler(5);
        sampler.shouldSample("key");
        sampler.recordSuppressed("key");

        sampler.reset();

        assertEquals(0, sampler.getMessageCount("key"));
        assertEquals(0, sampler.getSuppressedCount("key"));
    }

    @Test
    void trackedKeyCount() {
        CountBasedSampler sampler = new CountBasedSampler(10);
        assertEquals(0, sampler.getTrackedKeyCount());

        sampler.shouldSample("a");
        sampler.shouldSample("b");
        sampler.shouldSample("c");

        assertEquals(3, sampler.getTrackedKeyCount());
    }

    @Test
    void summaryWithNoSuppressed() {
        CountBasedSampler sampler = new CountBasedSampler(10);
        assertEquals("No messages sampled out", sampler.getSuppressedSummary(false));
    }

    @Test
    void summaryWithSuppressed() {
        CountBasedSampler sampler = new CountBasedSampler(10);
        sampler.shouldSample("errors");
        sampler.recordSuppressed("errors");
        sampler.recordSuppressed("errors");

        String summary = sampler.getSuppressedSummary(false);
        assertTrue(summary.contains("errors=2 sampled out"));
    }

    @Test
    void cleanupRemovesOldEntries() {
        CountBasedSampler sampler = new CountBasedSampler(10);
        sampler.shouldSample("old-key");

        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        sampler.shouldSample("new-key");
        int removed = sampler.cleanup(25);

        assertEquals(1, removed);
        assertEquals(1, sampler.getTrackedKeyCount());
    }

    @Test
    void cleanupWithInvalidMaxAgeReturnsZero() {
        CountBasedSampler sampler = new CountBasedSampler(10);
        sampler.shouldSample("key");
        assertEquals(0, sampler.cleanup(0));
        assertEquals(0, sampler.cleanup(-1));
    }

    @Test
    void determinismOverLargeRange() {
        CountBasedSampler sampler = new CountBasedSampler(100);
        int sampled = 0;
        int total = 10000;

        for (int i = 0; i < total; i++) {
            if (sampler.shouldSample("key")) {
                sampled++;
            }
        }

        assertEquals(100, sampled);
    }
}
