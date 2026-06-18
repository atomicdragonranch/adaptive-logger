package io.adaptivelogger;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler for automatic de-escalation of log levels.
 * Provides delayed execution for resetting log levels after escalation.
 */
public class LogLevelScheduler {

    private static final ScheduledExecutorService scheduler =
        Executors.newScheduledThreadPool(1, r -> {
            Thread thread = new Thread(r, "AdaptiveLogger-Scheduler");
            thread.setDaemon(true);
            return thread;
        });

    /**
     * Schedules a de-escalation for the given logger after the specified duration.
     *
     * @param logger   The logger to de-escalate
     * @param duration How long to wait before de-escalating
     */
    public static void scheduleDeescalation(IAdaptiveLogger logger, Duration duration) {
        if (logger == null || duration == null) {
            return;
        }

        scheduler.schedule(
            () -> {
                logger.resetLevel();
                logger.clearBuffer();
            },
            duration.toMillis(),
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * Shuts down the scheduler.
     * Should be called on application shutdown.
     */
    public static void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
