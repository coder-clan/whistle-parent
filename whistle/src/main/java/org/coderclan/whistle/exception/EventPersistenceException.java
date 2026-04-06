package org.coderclan.whistle.exception;

/**
 * Thrown when an event cannot be persisted to the database.
 *
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public class EventPersistenceException extends RuntimeException {

    public EventPersistenceException() {
    }

    public EventPersistenceException(String message) {
        super(message);
    }

    public EventPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
