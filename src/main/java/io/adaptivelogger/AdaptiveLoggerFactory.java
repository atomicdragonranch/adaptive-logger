package io.adaptivelogger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and managing adaptive logger instances.
 * Ensures that each logger name has a single logger instance
 * and provides centralized configuration management.
 *
 * <p><b>Design Decision:</b> This factory returns IAdaptiveLogger interface instances
 * rather than implementation classes to:
 * <ul>
 *   <li>Provide clear separation between API and implementation</li>
 *   <li>Enable future extensibility and alternative implementations</li>
 *   <li>Follow dependency inversion principle</li>
 *   <li>Make the factory pattern explicit in the codebase</li>
 * </ul>
 *
 * <p><b>Usage:</b> Always use AdaptiveLoggerFactory.getLogger() to obtain logger instances.
 * Do not use convenience methods or direct instantiation.
 *
 * @see IAdaptiveLogger for the interface contract
 * @see AdaptiveLoggerImpl for the default implementation
 */
public class AdaptiveLoggerFactory {
    private static final Logger LOG = LoggerFactory.getLogger(AdaptiveLoggerFactory.class);
    private static final Map<String, IAdaptiveLogger> loggers = new ConcurrentHashMap<>();
    private static volatile AdaptiveLoggingConfig globalConfig = AdaptiveLoggingConfig.defaultConfig();
    private static volatile boolean initialized = false;
    private static volatile Thread shutdownHook = null;

    /**
     * Initializes the adaptive logging system with the given configuration.
     * This should be called once at application startup.
     * Automatically registers a shutdown hook to ensure buffered logs are flushed on JVM termination.
     */
    public static synchronized void initialize(AdaptiveLoggingConfig config) {
        if (initialized) {
            // In distributed environments like Flink, initialization may be called multiple times.
            // This occurs because:
            // - The main() method executes on both JobManager (for validation) and TaskManagers
            // - Job restarts from checkpoints may re-execute initialization
            // - Task retries or scaling operations may trigger re-initialization
            // - Static state persists within the JVM between attempts
            //
            // This idempotent behavior is CRITICAL for production stability.
            // Without it, jobs would fail with "already initialized" errors during:
            // - Normal job submission (JobManager + TaskManager initialization)
            // - Recovery from checkpoints
            // - TaskManager failures and restarts
            // - Auto-scaling operations
            //
            // This is safe - just log and return:
            if (globalConfig != config) {
                // Only log if trying to initialize with a different config:
                LOG.debug("AdaptiveLoggerFactory already initialized, ignoring re-initialization attempt");
            }
            return;
        }
        globalConfig = config;
        initialized = true;
        ensureShutdownHook();
        logInitializationStatus(config);
    }

    /**
     * Logs the initialization status for operational visibility.
     * Called from both explicit initialize() and auto-initialization paths.
     */
    private static void logInitializationStatus(AdaptiveLoggingConfig config) {
        if (!config.isEnabled()) {
            LOG.info("Adaptive logging DISABLED - operating in pass-through mode");
        } else {
            LOG.info("Adaptive logging initialized: defaultLevel={}, ringBufferSize={}, escalationLevel={}, escalationDuration={}",
                config.getDefaultLevel(), config.getRingBufferSize(), config.getEscalationLevel(), config.getEscalationDuration());
        }
    }

    /**
     * Gets an adaptive logger for the given class.
     *
     * @param clazz The class for which to get the logger
     * @return An IAdaptiveLogger instance
     */
    public static IAdaptiveLogger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }

    /**
     * Gets an adaptive logger for the given name.
     *
     * @param name The name for the logger
     * @return An IAdaptiveLogger instance
     */
    public static IAdaptiveLogger getLogger(String name) {
        if (!initialized) {
            // Auto-initialize with default config if not explicitly initialized:
            synchronized (AdaptiveLoggerFactory.class) {
                if (!initialized) {
                    globalConfig = AdaptiveLoggingConfig.fromEnv();
                    initialized = true;
                    ensureShutdownHook();
                    logInitializationStatus(globalConfig);
                }
            }
        }

        return loggers.computeIfAbsent(name, n -> new AdaptiveLoggerImpl(n, globalConfig));
    }

    /**
     * Gets the current global configuration.
     */
    public static AdaptiveLoggingConfig getGlobalConfig() {
        return globalConfig;
    }

    /**
     * Updates the global configuration.
     * Note: This only affects newly created loggers. Existing loggers retain their configuration.
     */
    public static synchronized void updateGlobalConfig(AdaptiveLoggingConfig config) {
        globalConfig = config;
    }

    /**
     * Gets all currently active loggers.
     * Useful for management operations like bulk level changes.
     *
     * @return A copy of the current logger map
     */
    public static Map<String, IAdaptiveLogger> getAllLoggers() {
        return new ConcurrentHashMap<>(loggers);
    }

    /**
     * Resets all loggers to their default levels.
     */
    public static void resetAllLoggers() {
        loggers.values().forEach(IAdaptiveLogger::resetLevel);
    }

    /**
     * Clears all logger instances.
     * This should only be used in testing or shutdown scenarios.
     */
    public static synchronized void clear() {
        loggers.clear();
        initialized = false;
        globalConfig = AdaptiveLoggingConfig.defaultConfig();
    }

    /**
     * Ensures the shutdown hook is registered exactly once.
     * Uses double-checked locking to prevent duplicate registration.
     */
    private static void ensureShutdownHook() {
        if (shutdownHook == null) {
            synchronized (AdaptiveLoggerFactory.class) {
                if (shutdownHook == null) {
                    shutdownHook = new Thread(() -> {
                        shutdown();
                    }, "adaptive-logging-shutdown");
                    Runtime.getRuntime().addShutdownHook(shutdownHook);
                }
            }
        }
    }

    /**
     * Shuts down the adaptive logging system.
     * This should be called on application shutdown.
     */
    public static synchronized void shutdown() {
        // Dump any remaining buffered logs:
        loggers.values().forEach(logger -> {
            if (logger.getBufferSize() > 0) {
                logger.dumpBuffer();
            }
        });

        // Shutdown the scheduler:
        LogLevelScheduler.shutdown();

        // Clear all loggers:
        clear();
    }
}
