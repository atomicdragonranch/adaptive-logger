package io.adaptivelogger;

import io.adaptivelogger.sampling.CountBasedSampler;
import io.adaptivelogger.sampling.FixedRateSampler;
import io.adaptivelogger.sampling.SampledLogger;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages sampling configuration and caches for an adaptive logger instance.
 * Encapsulates fixed-rate and count-based sampler caches, along with
 * metrics, summary reporting, and cleanup operations.
 */
class SamplingManager {

    // Sampler caches to avoid creating new instances per call:
    // Using separate maps with primitive wrappers (Double/Integer) as keys avoids
    // creating SamplerKey objects on every sample() call in high-throughput scenarios:
    private final ConcurrentHashMap<Double, FixedRateSampler> fixedRateSamplerCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CountBasedSampler> countBasedSamplerCache = new ConcurrentHashMap<>();

    /**
     * Create a sampled logger that logs a percentage of messages.
     *
     * @param logger the parent logger for delegation
     * @param rate sampling rate from 0.0 (no messages) to 1.0 (all messages)
     * @return a SampledLogger for fluent API usage
     */
    SampledLogger sample(IAdaptiveLogger logger, double rate) {
        FixedRateSampler sampler = fixedRateSamplerCache.computeIfAbsent(
                rate, r -> new FixedRateSampler(r));
        return new SampledLogger(logger, sampler, null);
    }

    /**
     * Create a sampled logger with a specific message key.
     *
     * @param logger the parent logger for delegation
     * @param rate sampling rate from 0.0 to 1.0
     * @param messageKey key for grouping similar messages
     * @return a SampledLogger for fluent API usage
     */
    SampledLogger sample(IAdaptiveLogger logger, double rate, String messageKey) {
        FixedRateSampler sampler = fixedRateSamplerCache.computeIfAbsent(
                rate, r -> new FixedRateSampler(r));
        return new SampledLogger(logger, sampler, messageKey);
    }

    /**
     * Create a sampled logger that logs every Nth message.
     *
     * @param logger the parent logger for delegation
     * @param n log every Nth message (first message always logged)
     * @return a SampledLogger for fluent API usage
     */
    SampledLogger sampleEvery(IAdaptiveLogger logger, int n) {
        CountBasedSampler sampler = countBasedSamplerCache.computeIfAbsent(
                n, interval -> new CountBasedSampler(interval));
        return new SampledLogger(logger, sampler, null);
    }

    /**
     * Create a sampled logger that logs every Nth message with a specific key.
     *
     * @param logger the parent logger for delegation
     * @param n log every Nth message
     * @param messageKey key for grouping similar messages
     * @return a SampledLogger for fluent API usage
     */
    SampledLogger sampleEvery(IAdaptiveLogger logger, int n, String messageKey) {
        CountBasedSampler sampler = countBasedSamplerCache.computeIfAbsent(
                n, interval -> new CountBasedSampler(interval));
        return new SampledLogger(logger, sampler, messageKey);
    }

    /**
     * Get the total count of all sampled-out messages across all samplers.
     *
     * @return total number of sampled-out messages
     */
    long getTotalSuppressedCount() {
        long total = 0;
        for (FixedRateSampler sampler : fixedRateSamplerCache.values()) {
            total += sampler.getTotalSuppressedCount();
        }
        for (CountBasedSampler sampler : countBasedSamplerCache.values()) {
            total += sampler.getTotalSuppressedCount();
        }
        return total;
    }

    /**
     * Get the total number of unique keys being tracked across all samplers.
     *
     * @return total number of tracked keys
     */
    int getTrackedKeyCount() {
        int total = 0;
        for (FixedRateSampler sampler : fixedRateSamplerCache.values()) {
            total += sampler.getTrackedKeyCount();
        }
        for (CountBasedSampler sampler : countBasedSamplerCache.values()) {
            total += sampler.getTrackedKeyCount();
        }
        return total;
    }

    /**
     * Get a summary of sampling activity across all samplers.
     *
     * @param resetCounts whether to reset counts after getting the summary
     * @return summary of sampling activity
     */
    String getSummary(boolean resetCounts) {
        if (fixedRateSamplerCache.isEmpty() && countBasedSamplerCache.isEmpty()) {
            return "No sampling activity";
        }

        StringBuilder summary = new StringBuilder();

        // Process fixed-rate samplers:
        fixedRateSamplerCache.forEach((rate, sampler) -> {
            String samplerSummary = sampler.getSuppressedSummary(resetCounts);
            if (!samplerSummary.equals("No messages sampled out")) {
                if (summary.length() > 0) {
                    summary.append("; ");
                }
                summary.append("sample(").append(rate).append("): ").append(samplerSummary);
            }
        });

        // Process count-based samplers:
        countBasedSamplerCache.forEach((interval, sampler) -> {
            String samplerSummary = sampler.getSuppressedSummary(resetCounts);
            if (!samplerSummary.equals("No messages sampled out")) {
                if (summary.length() > 0) {
                    summary.append("; ");
                }
                summary.append("sampleEvery(").append(interval).append("): ").append(samplerSummary);
            }
        });

        return summary.length() > 0 ? summary.toString() : "No messages sampled out";
    }

    /**
     * Cleanup stale entries from all cached samplers.
     *
     * @param maxAgeMillis entries older than this will be removed
     * @return total number of entries removed
     */
    int cleanupSamplers(long maxAgeMillis) {
        int totalRemoved = 0;
        for (FixedRateSampler sampler : fixedRateSamplerCache.values()) {
            totalRemoved += sampler.cleanup(maxAgeMillis);
        }
        for (CountBasedSampler sampler : countBasedSamplerCache.values()) {
            totalRemoved += sampler.cleanup(maxAgeMillis);
        }
        return totalRemoved;
    }

    /**
     * Clear all cached sampler instances.
     *
     * @return total number of cached samplers that were cleared
     */
    int clearSamplerCaches() {
        int fixedRateCount = fixedRateSamplerCache.size();
        int countBasedCount = countBasedSamplerCache.size();

        fixedRateSamplerCache.clear();
        countBasedSamplerCache.clear();

        return fixedRateCount + countBasedCount;
    }

    /**
     * Get the number of cached sampler instances.
     *
     * @return total number of cached samplers (fixed-rate + count-based)
     */
    int getCachedSamplerCount() {
        return fixedRateSamplerCache.size() + countBasedSamplerCache.size();
    }
}
