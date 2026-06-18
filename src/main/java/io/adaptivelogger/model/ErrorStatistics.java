package io.adaptivelogger.model;

import java.io.Serializable;
import java.time.Instant;

/**
 * Statistics about error detection and escalation.
 */
public class ErrorStatistics implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int errorCount;
    private final Instant windowStart;
    private final Instant windowEnd;
    private final boolean inErrorState;
    private final int escalationCount;
    private final Instant lastEscalation;

    public ErrorStatistics(
            int errorCount,
            Instant windowStart,
            Instant windowEnd,
            boolean inErrorState,
            int escalationCount,
            Instant lastEscalation) {
        this.errorCount = errorCount;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.inErrorState = inErrorState;
        this.escalationCount = escalationCount;
        this.lastEscalation = lastEscalation;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public Instant getWindowStart() {
        return windowStart;
    }

    public Instant getWindowEnd() {
        return windowEnd;
    }

    public boolean isInErrorState() {
        return inErrorState;
    }

    public int getEscalationCount() {
        return escalationCount;
    }

    public Instant getLastEscalation() {
        return lastEscalation;
    }

    @Override
    public String toString() {
        return "ErrorStatistics{" +
               "errorCount=" + errorCount +
               ", windowStart=" + windowStart +
               ", windowEnd=" + windowEnd +
               ", inErrorState=" + inErrorState +
               ", escalationCount=" + escalationCount +
               ", lastEscalation=" + lastEscalation +
               '}';
    }
}
