# Adaptive Logger

[![CI](https://github.com/atomicdragonranch/adaptive-logger/actions/workflows/ci.yml/badge.svg)](https://github.com/atomicdragonranch/adaptive-logger/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://openjdk.org/)
[![Tests](https://img.shields.io/badge/tests-177%20passing-brightgreen.svg)]()

An adaptive logging library for JVM applications. Wraps SLF4J with dynamic log level management, automatic error-driven escalation, ring buffer debug context capture, rate limiting, sampling, and lazy evaluation.

Designed for high-throughput streaming applications (Apache Flink, Kafka Streams) but works with any SLF4J-based project.

## Core Concept

Log4j sets the ceiling (what *can* be logged). Adaptive Logger controls the floor (what *is* logged, and when).

In production, you run at `INFO`. When errors occur, the logger automatically escalates to `DEBUG`, dumps its ring buffer of recent debug-level events for context, then de-escalates after a configurable cooldown. You get full debug context around errors without running at `DEBUG` all the time.

## Features

- **Dynamic log levels** - change levels at runtime without restarts
- **Error-driven escalation** - automatically drops to DEBUG when error thresholds are hit, with scheduled de-escalation
- **Ring buffer** - captures recent debug/trace events even when those levels are disabled; dumps on error for post-mortem context
- **Rate limiting** - fluent `atMostEvery(duration)` API to suppress noisy log sites
- **Sampling** - fixed-rate (percentage) and count-based (every Nth) sampling with suppressed-count reporting
- **Lazy evaluation** - `Supplier`-based logging methods avoid argument construction when the level is disabled
- **MDC context** - preserves and restores MDC state across async boundaries; optional Flink-aware provider adds task metadata
- **Critical error detection** - pattern matching for OOM, checkpoint failures, state corruption, serialization errors

## Quick Start

```xml
<dependency>
    <groupId>io.adaptivelogger</groupId>
    <artifactId>adaptive-logger</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```java
import io.adaptivelogger.AdaptiveLoggerFactory;
import io.adaptivelogger.IAdaptiveLogger;

public class MyService {
    private static final IAdaptiveLogger log = AdaptiveLoggerFactory.getLogger(MyService.class);

    public void process(Event event) {
        // Standard SLF4J usage - all Logger methods work
        log.info("Processing event: {}", event.getId());

        // Lazy evaluation - Supplier only called if DEBUG is enabled
        log.debugLazy("Event details: {}", () -> event.toDetailedString());

        // Rate limiting - log at most once per 30 seconds
        log.atMostEvery(Duration.ofSeconds(30)).warn("Backpressure detected on partition {}", partitionId);

        // Sampling - log 10% of events
        log.sample(0.10).debug("Sampled event throughput: {}", throughput);
    }
}
```

## Configuration

All configuration is via environment variables with sensible defaults:

| Variable | Default | Description |
|----------|---------|-------------|
| `ADAPTIVE_LOGGING_ENABLED` | `true` | Enable/disable adaptive features |
| `ADAPTIVE_LOGGING_DEFAULT_LEVEL` | `INFO` | Default log level |
| `ADAPTIVE_LOGGING_BUFFER_SIZE` | `100` | Ring buffer capacity |
| `ADAPTIVE_LOGGING_DUMP_ON_ERROR` | `true` | Auto-dump buffer on error |
| `ADAPTIVE_LOGGING_ERROR_THRESHOLD` | `5` | Errors before escalation |
| `ADAPTIVE_LOGGING_ERROR_WINDOW_SECONDS` | `60` | Sliding window for error counting |
| `ADAPTIVE_LOGGING_ESCALATION_LEVEL` | `DEBUG` | Level to escalate to |
| `ADAPTIVE_LOGGING_ESCALATION_DURATION_SECONDS` | `300` | How long to stay escalated |
| `ADAPTIVE_LOGGING_ESCALATION_COOLDOWN_SECONDS` | `60` | Minimum time between escalations |

Or configure programmatically:

```java
AdaptiveLoggingConfig config = AdaptiveLoggingConfig.builder()
    .enabled(true)
    .defaultLevel(Level.INFO)
    .bufferSize(200)
    .errorThreshold(3)
    .escalationLevel(Level.DEBUG)
    .escalationDurationSeconds(600)
    .build();

AdaptiveLoggerFactory.initialize(config);
```

## Flink Integration

For Apache Flink applications, use `FlinkMDCProvider` to automatically add task metadata to log context:

```java
public class MyOperator extends RichMapFunction<String, String> {
    private transient IAdaptiveLogger log;

    @Override
    public void open(Configuration parameters) {
        FlinkMDCProvider provider = new FlinkMDCProvider(getRuntimeContext());
        log = AdaptiveLoggerFactory.getLogger(MyOperator.class);
    }
}
```

The Flink dependency is optional/provided. Non-Flink projects don't pull it in.

## Building

```bash
mvn clean compile    # compile
mvn test             # run tests
mvn package          # build jar
```

## Requirements

- Java 17+
- SLF4J 2.x
- Apache Flink 1.20+ (optional, for FlinkMDCProvider only)

## License

Apache License 2.0
