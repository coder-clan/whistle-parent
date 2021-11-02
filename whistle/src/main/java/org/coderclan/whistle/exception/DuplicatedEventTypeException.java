package org.coderclan.whistle.exception;

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