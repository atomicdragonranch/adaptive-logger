package io.adaptivelogger.mdc;

import io.adaptivelogger.model.MDContext;

import org.apache.flink.api.common.functions.RuntimeContext;

/**
 * Flink-specific MDC provider that adds Flink runtime context information.
 * Includes task name, subtask index, attempt number, and job ID.
 */
public class FlinkMDCProvider implements MDCProvider {

    private final RuntimeContext runtimeContext;

    public FlinkMDCProvider(RuntimeContext runtimeContext) {
        this.runtimeContext = runtimeContext;
    }

    @Override
    public MDContext getCurrentContext() {
        MDContext.Builder builder = MDContext.builderWithCurrent();

        if (runtimeContext != null) {
            // Add Flink-specific context:
            builder.add("taskName", runtimeContext.getTaskName())
                   .add("subtaskIndex", String.valueOf(runtimeContext.getIndexOfThisSubtask()))
                   .add("attemptNumber", String.valueOf(runtimeContext.getAttemptNumber()))
                   .add("jobId", runtimeContext.getJobId().toString());

            // Add parallelism info:
            builder.add("numberOfParallelSubtasks", String.valueOf(runtimeContext.getNumberOfParallelSubtasks()));
        }

        return builder.build();
    }
}
