package io.adaptivelogger.mdc;

import io.adaptivelogger.model.MDContext;

/**
 * Interface for providing MDC context.
 * Allows for different implementations based on execution environment.
 */
public interface MDCProvider {

    /**
     * Gets the current MDC context.
     *
     * @return The current MDC context
     */
    MDContext getCurrentContext();

    /**
     * Default implementation that simply returns the current thread's MDC.
     */
    class DefaultMDCProvider implements MDCProvider {
        @Override
        public MDContext getCurrentContext() {
            return MDContext.current();
        }
    }
}
