/**
 * Adaptive logging system for Flink applications providing dynamic log level management.
 *
 * <h2>Overview</h2>
 * <p>This package provides a sophisticated logging framework that adapts to runtime conditions,
 * automatically escalating log levels during error scenarios to provide maximum debugging context
 * while maintaining optimal performance during normal operation.
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li><b>Dynamic Log Level Management</b> - Change log levels at runtime without restart</li>
 *   <li><b>Automatic Error Escalation</b> - Escalates to DEBUG/TRACE when error threshold exceeded</li>
 *   <li><b>Debug/Trace Message Buffering</b> - Circular buffer stores recent events for post-mortem analysis</li>
 *   <li><b>MDC Context Preservation</b> - Maintains distributed tracing context across Flink operators</li>
 *   <li><b>Lazy Evaluation</b> - Defers expensive computations until messages are actually logged</li>
 *   <li><b>Idempotent Initialization</b> - Handles Flink's distributed execution model gracefully</li>
 *   <li><b>Rate Limiting</b> - Prevent log flooding with time-window based suppression</li>
 *   <li><b>Sampling</b> - Reduce volume while maintaining statistical visibility</li>
 * </ul>
 *
 * <h2>Architecture Principle: Ceiling and Floor</h2>
 * <p><b>Log4j sets the ceiling, AdaptiveLogger controls the floor.</b></p>
 * <ul>
 *   <li>Log4j configuration determines the maximum verbosity allowed (ceiling)</li>
 *   <li>AdaptiveLogger dynamically controls what actually gets logged (floor)</li>
 *   <li>Example: With Log4j at DEBUG and AdaptiveLogger at INFO, only INFO+ messages are logged</li>
 *   <li>On error escalation, AdaptiveLogger raises its floor to match the ceiling</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * // Replace standard SLF4J logger
 * private static final IAdaptiveLogger LOG = AdaptiveLoggerFactory.getLogger(MyOperator.class);
 *
 * // Initialize once at application startup (idempotent)
 * AdaptiveLoggerFactory.initialize(AdaptiveLoggingConfig.fromEnv());
 *
 * // Use like standard logger
 * LOG.info("Processing started");
 * LOG.debug("Details: {}", details);  // Buffered unless DEBUG enabled
 *
 * // Lazy evaluation for performance
 * LOG.debugLazy("Expensive: {}", () -> computeExpensiveValue());
 *
 * // Add MDC context for tracing
 * MDContext context = MDContext.builder()
 *     .add("marketId", marketId)
 *     .add("betId", betId)
 *     .build();
 *
 * MDContext.apply(context, () -> {
 *     LOG.info("Processing bet");
 *     // All logs within this block include marketId and betId
 * });
 * }</pre>
 *
 * <h2>Configuration via Environment Variables:</h2>
 * <ul>
 *   <li>{@code ADAPTIVE_LOGGING_ENABLED} - Enable/disable adaptive logging (default: true)</li>
 *   <li>{@code ADAPTIVE_LOGGING_DEFAULT_LEVEL} - Default log level (default: INFO)</li>
 *   <li>{@code ADAPTIVE_LOGGING_BUFFER_SIZE} - Ring buffer size for debug events (default: 1000)</li>
 *   <li>{@code ADAPTIVE_LOGGING_DUMP_ON_ERROR} - Dump buffer when errors occur (default: true)</li>
 *   <li>{@code ADAPTIVE_LOGGING_BUFFER_INFO} - Buffer INFO messages in addition to DEBUG (default: false)</li>
 *   <li>{@code ADAPTIVE_LOGGING_BUFFER_DUMP_COOLDOWN_SECONDS} - Cooldown between buffer dumps (default: 30)</li>
 *   <li>{@code ADAPTIVE_LOGGING_ERROR_THRESHOLD} - Error count before escalation (default: 5)</li>
 *   <li>{@code ADAPTIVE_LOGGING_ERROR_WINDOW_SECONDS} - Time window for error counting (default: 60)</li>
 *   <li>{@code ADAPTIVE_LOGGING_ESCALATION_COOLDOWN_SECONDS} - Cooldown between escalations (default: 600)</li>
 *   <li>{@code ADAPTIVE_LOGGING_ESCALATION_LEVEL} - Target level for escalation: DEBUG or TRACE (default: DEBUG)</li>
 *   <li>{@code ADAPTIVE_LOGGING_ESCALATION_DURATION_SECONDS} - How long to stay escalated (default: 300)</li>
 *   <li>{@code ADAPTIVE_LOGGING_CRITICAL_PATTERNS} - Additional regex patterns to trigger escalation (optional, semicolon-delimited)</li>
 * </ul>
 *
 * <h2>Built-in Critical Error Patterns:</h2>
 * <p>The system automatically escalates on these critical errors (always active):</p>
 * <ul>
 *   <li><b>JVM Fatal:</b> OutOfMemoryError, StackOverflowError</li>
 *   <li><b>State Corruption:</b> StateBackendException, state corruption messages</li>
 *   <li><b>Checkpoint Failures:</b> CheckpointException, checkpoint failed messages</li>
 *   <li><b>Serialization:</b> SerializationException, InvalidProtocolBufferException</li>
 * </ul>
 * <p>Additional patterns can be added via ADAPTIVE_LOGGING_CRITICAL_PATTERNS environment variable.</p>
 *
 * <h2>Rate Limiting:</h2>
 * <p>Prevent log flooding during failure scenarios with time-window based rate limiting:</p>
 * <pre>{@code
 * // Log at most once every 30 seconds:
 * LOG.atMostEvery(30, TimeUnit.SECONDS)
 *    .warn("Connection pool exhausted");
 *
 * // Group related messages under a key:
 * LOG.atMostEvery(1, TimeUnit.MINUTES, "db-connection")
 *    .error("Database connection failed: {}", exception.getMessage());
 *
 * // Include count of suppressed messages:
 * LOG.atMostEvery(30, TimeUnit.SECONDS)
 *    .withSuppressedCount()
 *    .error("Critical failure", exception);
 *
 * // Periodic summary of rate limiting activity:
 * LOG.info("Rate limiting: {}", LOG.getRateLimitingSummary(true));
 * }</pre>
 *
 * <h3>Rate Limiting Components:</h3>
 * <ul>
 *   <li>{@link io.adaptivelogger.RateLimiter} - Interface for rate limiting strategies</li>
 *   <li>{@link io.adaptivelogger.TimeBasedRateLimiter} - Time-window based implementation (O(1) lookups)</li>
 *   <li>{@link io.adaptivelogger.RateLimitedLogger} - Fluent API for rate-limited logging</li>
 * </ul>
 *
 * <h2>Sampling:</h2>
 * <p>Reduce log volume for high-frequency events while maintaining statistical visibility:</p>
 * <pre>{@code
 * // Log approximately 10% of messages:
 * LOG.sample(0.1)
 *    .debug("High-volume event: {}", eventId);
 *
 * // Log every 100th message (deterministic):
 * LOG.sampleEvery(100)
 *    .trace("Market tick: {}", tick);
 *
 * // Include count of sampled-out messages:
 * LOG.sample(0.1)
 *    .withSampledCount()
 *    .debug("Processing: {}", data);
 * }</pre>
 *
 * <h3>Sampling Strategies:</h3>
 * <ul>
 *   <li><b>Fixed-rate sampling</b> ({@code sample(0.1)}) - Log ~10% of messages using random sampling</li>
 *   <li><b>Count-based sampling</b> ({@code sampleEvery(100)}) - Log every Nth message deterministically</li>
 * </ul>
 *
 * <h3>Sampling Components:</h3>
 * <ul>
 *   <li>{@link io.adaptivelogger.Sampler} - Interface for sampling strategies</li>
 *   <li>{@link io.adaptivelogger.FixedRateSampler} - Percentage-based random sampling</li>
 *   <li>{@link io.adaptivelogger.CountBasedSampler} - Every-Nth-message sampling</li>
 *   <li>{@link io.adaptivelogger.SampledLogger} - Fluent API for sampled logging</li>
 * </ul>
 *
 * <h2>Performance Considerations:</h2>
 * <ul>
 *   <li>INFO level: Negligible overhead (< 1% CPU)</li>
 *   <li>DEBUG level: ~5% CPU overhead with buffering</li>
 *   <li>TRACE level: ~20% throughput reduction, ~25% CPU increase</li>
 *   <li>Use lazy evaluation in hot paths to defer expensive computations</li>
 *   <li>MDC context capture adds overhead in high-throughput scenarios (>1M events/sec)</li>
 *   <li>Rate limiting: O(1) lookups with lock-free CAS operations, negligible overhead</li>
 *   <li>Rate-limited messages: Zero logging overhead when suppressed (cheaper than actual logging)</li>
 *   <li>Sampling: O(1) decisions using ThreadLocalRandom (lock-free, no contention)</li>
 *   <li>Sampled-out messages: Near-zero overhead (random check only)</li>
 * </ul>
 *
 * @since 2026-06
 * @see AdaptiveLogger for the main logger interface
 * @see AdaptiveLoggerFactory for initialization and management
 * @see AdaptiveLoggingConfig for configuration options
 * @see MDContext for distributed tracing support
 * @see RateLimiter for rate limiting interface
 * @see TimeBasedRateLimiter for rate limiting implementation
 * @see Sampler for sampling interface
 * @see FixedRateSampler for percentage-based sampling
 * @see CountBasedSampler for every-Nth-message sampling
 */
package io.adaptivelogger;
