package org.coderclan.whistle.exception;

/**
 * Throws when an Event Content type not found.
 *
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public class EventContentTypeNotFoundException extends RuntimeException {
    public EventContentTypeNotFoundException() {
    }

    public EventContentTypeNotFoundException(String message) {
        super(message);
    }

    public EventContentTypeNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public EventContentTypeNotFoundException(Throwable cause) {
        super(cause);
    }
}
