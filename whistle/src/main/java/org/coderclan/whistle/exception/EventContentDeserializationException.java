package org.coderclan.whistle.exception;

public class EventContentDeserializationException extends RuntimeException {
    public EventContentDeserializationException() {
    }

    public EventContentDeserializationException(String message) {
        super(message);
    }

    public EventContentDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public EventContentDeserializationException(Throwable cause) {
        super(cause);
    }
}
