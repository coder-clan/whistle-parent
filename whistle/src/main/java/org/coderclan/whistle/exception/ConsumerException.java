package org.coderclan.whistle.exception;

/**
 * Wrap any uncaught Exception throwed by Event Consumers.
 *
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public class ConsumerException extends RuntimeException {
    public ConsumerException() {
    }

    public ConsumerException(String message) {
        super(message);
    }

    public ConsumerException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConsumerException(Throwable cause) {
        super(cause);
    }
}
