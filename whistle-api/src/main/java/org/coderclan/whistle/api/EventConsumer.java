package org.coderclan.whistle.api;

/**
 * Consume event.
 * Implements must be thread safe!
 * Implements must hand duplicated messages. Duplicated messages could be determined by {@link EventContent#getIdempotentId()}
 *
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public interface EventConsumer<E extends EventContent> {

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
    boolean consume(E message) throws Exception;
}
