package io.adaptivelogger.model;

import org.slf4j.event.Level;
import java.io.Serializable;

/**
 * Statistics about adaptive logger behavior.
 * Provides insights into logging activity and buffer usage.
 */
public class LoggingStatistics implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String loggerName;
    private final Level currentLevel;
    private final int totalLogs;
    private final int errorCount;
    private final int bufferSize;
    private final int bufferCapacity;
    private final ErrorStatistics errorDetectorState;

    private LoggingStatistics(Builder builder) {
        this.loggerName = builder.loggerName;
        this.currentLevel = builder.currentLevel;
        this.totalLogs = builder.totalLogs;
        this.errorCount = builder.errorCount;
        this.bufferSize = builder.bufferSize;
        this.bufferCapacity = builder.bufferCapacity;
        this.errorDetectorState = builder.errorDetectorState;
    }

    public String getLoggerName() {
        return loggerName;
    }

    public Level getCurrentLevel() {
        return currentLevel;
    }

    public int getTotalLogs() {
        return totalLogs;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public int getBufferCapacity() {
        return bufferCapacity;
    }

    public double getBufferUtilization() {
        return bufferCapacity > 0 ? (double) bufferSize / bufferCapacity : 0.0;
    }

    public ErrorStatistics getErrorDetectorState() {
        return errorDetectorState;
    }

    @Override
    public String toString() {
        return "LoggingStatistics{" +
               "loggerName='" + loggerName + '\'' +
               ", currentLevel=" + currentLevel +
               ", totalLogs=" + totalLogs +
               ", errorCount=" + errorCount +
               ", bufferSize=" + bufferSize +
               ", bufferCapacity=" + bufferCapacity +
               ", bufferUtilization=" + String.format("%.2f", getBufferUtilization()) +
               ", errorDetectorState=" + errorDetectorState +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String loggerName;
        private Level currentLevel;
        private int totalLogs;
        private int errorCount;
        private int bufferSize;
        private int bufferCapacity;
        private ErrorStatistics errorDetectorState;

        public Builder loggerName(String loggerName) {
            this.loggerName = loggerName;
            return this;
        }

        public Builder currentLevel(Level currentLevel) {
            this.currentLevel = currentLevel;
            return this;
        }

        public Builder totalLogs(int totalLogs) {
            this.totalLogs = totalLogs;
            return this;
        }

        public Builder errorCount(int errorCount) {
            this.errorCount = errorCount;
            return this;
        }

        public Builder bufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
            return this;
        }

        public Builder bufferCapacity(int bufferCapacity) {
            this.bufferCapacity = bufferCapacity;
            return this;
        }

        public Builder errorDetectorState(ErrorStatistics errorDetectorState) {
            this.errorDetectorState = errorDetectorState;
            return this;
        }

        public LoggingStatistics build() {
            return new LoggingStatistics(this);
        }
    }
}
