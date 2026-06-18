package io.adaptivelogger.model;

import org.slf4j.MDC;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Thread-safe wrapper for MDC (Mapped Diagnostic Context) that provides:
 * - Immutable context snapshots
 * - Automatic context application/cleanup
 * - Fluent builder API
 */
public class MDContext implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, String> contextMap;

    private MDContext(Map<String, String> contextMap) {
        this.contextMap = Collections.unmodifiableMap(new HashMap<>(contextMap));
    }

    /**
     * Returns an immutable copy of the context map.
     */
    public Map<String, String> getContextMap() {
        return contextMap;
    }

    /**
     * Gets a value from the context.
     */
    public String get(String key) {
        return contextMap.get(key);
    }

    /**
     * Checks if the context contains a key.
     */
    public boolean containsKey(String key) {
        return contextMap.containsKey(key);
    }

    /**
     * Returns the number of entries in the context.
     */
    public int size() {
        return contextMap.size();
    }

    /**
     * Checks if the context is empty.
     */
    public boolean isEmpty() {
        return contextMap.isEmpty();
    }

    /**
     * Applies this context to the current thread's MDC and executes the action.
     * Automatically restores the previous MDC state after execution.
     */
    public static void apply(MDContext context, Runnable action) {
        if (context == null || context.isEmpty()) {
            action.run();
            return;
        }

        // Save current MDC state:
        Map<String, String> previousContext = MDC.getCopyOfContextMap();

        try {
            // Apply new context:
            context.contextMap.forEach(MDC::put);

            // Execute action with new context:
            action.run();
        } finally {
            // Restore previous context:
            MDC.clear();
            if (previousContext != null) {
                MDC.setContextMap(previousContext);
            }
        }
    }

    /**
     * Creates a new context from the current thread's MDC.
     */
    public static MDContext current() {
        Map<String, String> currentMap = MDC.getCopyOfContextMap();
        if (currentMap == null || currentMap.isEmpty()) {
            return empty();
        }
        return new MDContext(currentMap);
    }

    /**
     * Returns an empty context.
     */
    public static MDContext empty() {
        return new MDContext(Collections.emptyMap());
    }

    /**
     * Creates a builder for constructing a new context.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder initialized with the current MDC state.
     */
    public static Builder builderWithCurrent() {
        return new Builder(MDC.getCopyOfContextMap());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MDContext that = (MDContext) o;
        return Objects.equals(contextMap, that.contextMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contextMap);
    }

    @Override
    public String toString() {
        return "MDContext" + contextMap;
    }

    /**
     * Builder for creating MDContext instances.
     */
    public static class Builder {
        private final Map<String, String> contextMap;

        public Builder() {
            this.contextMap = new HashMap<>();
        }

        public Builder(Map<String, String> initialMap) {
            this.contextMap = new HashMap<>(initialMap != null ? initialMap : Collections.emptyMap());
        }

        /**
         * Adds a key-value pair to the context.
         */
        public Builder add(String key, String value) {
            if (key != null && value != null) {
                contextMap.put(key, value);
            }
            return this;
        }

        /**
         * Adds all entries from the given map to the context.
         */
        public Builder addAll(Map<String, String> map) {
            if (map != null) {
                map.forEach(this::add);
            }
            return this;
        }

        /**
         * Adds all entries from the given context to this builder.
         */
        public Builder addAll(MDContext context) {
            if (context != null) {
                addAll(context.contextMap);
            }
            return this;
        }

        /**
         * Removes a key from the context.
         */
        public Builder remove(String key) {
            contextMap.remove(key);
            return this;
        }

        /**
         * Clears all entries from the builder.
         */
        public Builder clear() {
            contextMap.clear();
            return this;
        }

        /**
         * Builds the immutable MDContext.
         */
        public MDContext build() {
            return new MDContext(contextMap);
        }
    }
}
