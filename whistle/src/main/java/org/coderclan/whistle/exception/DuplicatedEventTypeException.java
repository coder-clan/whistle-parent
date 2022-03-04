package org.coderclan.whistle.exception;

import org.coderclan.whistle.api.EventType;

/**
 * Duplicated Event Type found. See {@link EventType#getName()}.
 *
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public class DuplicatedEventTypeException extends RuntimeException {
    public DuplicatedEventTypeException() {
    }

    public DuplicatedEventTypeException(String message) {
        super(message);
    }

    public DuplicatedEventTypeException(String message, Throwable cause) {
        super(message, cause);
    }

    public DuplicatedEventTypeException(Throwable cause) {
        super(cause);
    }
}