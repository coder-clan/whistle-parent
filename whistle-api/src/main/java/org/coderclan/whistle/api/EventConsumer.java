package org.coderclan.whistle.api;

import org.coderclan.whistle.exception.ConsumerException;

import java.util.function.Consumer;

/**
 * Consume event.
 * Implements must be thread safe!
 * Implements must hand duplicated messages. Duplicated messages could be determined by {@link AbstractEventContent#getIdempotentId()}
 *
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public interface EventConsumer<E extends EventContent> extends Consumer<E> {

    /**
     * @return event type which the Callback consumes.
     */
    EventType<E> getSupportEventType();

    /**
     * throw exception or return false will be treated as consumer fail.
     *
     * @param message
     * @return true if consumed successfully.
     */
    // Framework callback interface — intentionally declares throws Exception to allow implementors
    // maximum flexibility, similar to java.util.concurrent.Callable. The accept() default method
    // catches and wraps all exceptions.
    @SuppressWarnings("java:S112")
    boolean consume(E message) throws Exception;

    default void accept(E content) {
        try {
            if (!this.consume(content)) {
                throw new ConsumerException("Consume failed.");
            }
        } catch (ConsumerException e) {
            throw e;
        } catch (Exception e) {
            throw new ConsumerException(e);
        }
    }
}
