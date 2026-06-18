package io.adaptivelogger.model;

import org.slf4j.event.Level;
import java.io.Serializable;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Represents a log event with all its context.
 * Supports both eager and lazy argument evaluation for performance optimization.
 *
 * Eager evaluation (default): Arguments are evaluated immediately when event is created.
 * Lazy evaluation: Arguments are stored as Suppliers and evaluated only when needed
 * (e.g., during buffer dump after escalation). Suppliers are evaluated once and cached
 * to prevent duplicate evaluation.
 *
 * MDC Context Behavior:
 * The MDC context is captured at event creation time and preserved with the event.
 * This ensures buffered events maintain the correct contextual information from when
 * the log was created, even if MDC values change before the event is dumped.
 * Example: A buffered debug event logged while processing Market A will show Market A's
 * context even if dumped while processing Market B.
 *
 * Immutable and thread-safe (except for lazy evaluation cache).
 */
public class LogEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Instant timestamp;
    private final Level level;
    private final String loggerName;
    private final String threadName;
    private final String format;
    private final Object[] arguments;
    private final transient Supplier<?>[] lazyArguments;  // Not serializable, for performance:
    private transient Object[] evaluatedCache;  // Cache for lazy evaluation results:
    private final boolean isLazy;
    private final Throwable throwable;
    private final MDContext mdContext;

    private LogEvent(Builder builder) {
        this.timestamp = builder.timestamp;
        this.level = builder.level;
        this.loggerName = builder.loggerName;
        this.threadName = builder.threadName;
        this.format = builder.format;
        this.arguments = builder.arguments;
        this.lazyArguments = builder.lazyArguments;
        this.isLazy = builder.isLazy;
        this.throwable = builder.throwable;
        this.mdContext = builder.mdContext;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public Level getLevel() {
        return level;
    }

    public String getLoggerName() {
        return loggerName;
    }

    public String getThreadName() {
        return threadName;
    }

    public String getFormat() {
        return format;
    }

    public Object[] getArguments() {
        return arguments;
    }

    /**
     * Returns evaluated arguments, evaluating lazy suppliers if needed.
     * Use this method when formatting messages for buffer dump or logging.
     *
     * For eager events: Returns arguments directly (no evaluation needed)
     * For lazy events: Evaluates all suppliers ONCE and caches results
     *
     * This method is idempotent - calling it multiple times will only
     * evaluate suppliers once, preventing duplicate evaluation during
     * both immediate logging and buffer dump.
     *
     * @return Evaluated arguments array
     */
    public Object[] getEvaluatedArguments() {
        if (!isLazy) {
            return arguments;
        }

        if (lazyArguments == null) {
            return null;
        }

        // Return cached results if already evaluated:
        if (evaluatedCache != null) {
            return evaluatedCache;
        }

        // Evaluate all suppliers and cache:
        Object[] evaluated = new Object[lazyArguments.length];
        for (int i = 0; i < lazyArguments.length; i++) {
            try {
                evaluated[i] = lazyArguments[i] != null ? lazyArguments[i].get() : null;
            } catch (Exception e) {
                // If supplier throws, replace with error message:
                evaluated[i] = "[Error evaluating argument: " + e.getMessage() + "]";
            }
        }

        // Cache the results:
        evaluatedCache = evaluated;
        return evaluated;
    }

    /**
     * Returns true if this event uses lazy evaluation (arguments are Suppliers).
     *
     * @return true if lazy, false if eager
     */
    public boolean isLazy() {
        return isLazy;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public MDContext getMdContext() {
        return mdContext;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LogEvent logEvent = (LogEvent) o;
        return Objects.equals(timestamp, logEvent.timestamp) &&
               level == logEvent.level &&
               Objects.equals(loggerName, logEvent.loggerName) &&
               Objects.equals(threadName, logEvent.threadName) &&
               Objects.equals(format, logEvent.format) &&
               Arrays.equals(arguments, logEvent.arguments) &&
               Objects.equals(throwable, logEvent.throwable) &&
               Objects.equals(mdContext, logEvent.mdContext);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(timestamp, level, loggerName, threadName, format, throwable, mdContext);
        result = 31 * result + Arrays.hashCode(arguments);
        return result;
    }

    @Override
    public String toString() {
        return "LogEvent{" +
               "timestamp=" + timestamp +
               ", level=" + level +
               ", loggerName='" + loggerName + '\'' +
               ", threadName='" + threadName + '\'' +
               ", format='" + format + '\'' +
               ", hasThrowable=" + (throwable != null) +
               ", isLazy=" + isLazy +
               ", mdContext=" + mdContext +
               '}';
    }

    /**
     * Custom serialization to handle transient lazy arguments.
     * Evaluates lazy suppliers before serialization to preserve debug context
     * during Flink checkpoint/restore cycles.
     *
     * This ensures that buffered lazy events don't lose their argument values
     * when the application is restored from a checkpoint.
     *
     * @param out Object output stream
     * @throws IOException if serialization fails
     */
    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
        // For lazy events, evaluate suppliers before serialization:
        if (isLazy && lazyArguments != null) {
            // Evaluate suppliers to get actual values:
            Object[] evaluatedArgs = getEvaluatedArguments();

            // Temporarily store in a field that will be serialized:
            // We'll serialize the event with eager arguments instead:
            LogEvent eagerVersion = LogEvent.builder()
                .timestamp(timestamp)
                .level(level)
                .loggerName(loggerName)
                .threadName(threadName)
                .format(format)
                .arguments(evaluatedArgs)  // Use evaluated arguments:
                .throwable(throwable)
                .mdContext(mdContext)
                .build();

            // Write the eager version:
            out.writeObject(eagerVersion.timestamp);
            out.writeObject(eagerVersion.level);
            out.writeObject(eagerVersion.loggerName);
            out.writeObject(eagerVersion.threadName);
            out.writeObject(eagerVersion.format);
            out.writeObject(eagerVersion.arguments);
            out.writeBoolean(false);  // isLazy = false after evaluation
            out.writeObject(eagerVersion.throwable);
            out.writeObject(eagerVersion.mdContext);
        } else {
            // Standard serialization for eager events:
            out.defaultWriteObject();
        }
    }

    /**
     * Custom deserialization matching writeObject.
     *
     * @param in Object input stream
     * @throws IOException if deserialization fails
     * @throws ClassNotFoundException if class not found during deserialization
     */
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        // lazyArguments will be null after deserialization (transient)
        // isLazy flag preserved, but treated as eager since suppliers are gone
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Instant timestamp;
        private Level level;
        private String loggerName;
        private String threadName;
        private String format;
        private Object[] arguments;
        private Supplier<?>[] lazyArguments;
        private boolean isLazy = false;
        private Throwable throwable;
        private MDContext mdContext;

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder level(Level level) {
            this.level = level;
            return this;
        }

        public Builder loggerName(String loggerName) {
            this.loggerName = loggerName;
            return this;
        }

        public Builder threadName(String threadName) {
            this.threadName = threadName;
            return this;
        }

        public Builder format(String format) {
            this.format = format;
            return this;
        }

        public Builder arguments(Object[] arguments) {
            this.arguments = arguments;
            this.isLazy = false;
            return this;
        }

        /**
         * Sets lazy arguments (Suppliers) for deferred evaluation:
         * Arguments will only be evaluated when getEvaluatedArguments() is called:
         *
         * @param lazyArguments Supplier array for lazy evaluation
         * @return Builder
         */
        public Builder lazyArguments(Supplier<?>[] lazyArguments) {
            this.lazyArguments = lazyArguments;
            this.isLazy = true;
            return this;
        }

        public Builder throwable(Throwable throwable) {
            this.throwable = throwable;
            return this;
        }

        public Builder mdContext(MDContext mdContext) {
            this.mdContext = mdContext;
            return this;
        }

        public LogEvent build() {
            return new LogEvent(this);
        }
    }
}
