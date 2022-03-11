package org.coderclan.whistle.exception;

public class EventContentSerializationException extends RuntimeException {
    public EventContentSerializationException() {
    }

    public EventContentSerializationException(String message) {
        super(message);
    }

    public EventContentSerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public EventContentSerializationException(Throwable cause) {
        super(cause);
    }
}
