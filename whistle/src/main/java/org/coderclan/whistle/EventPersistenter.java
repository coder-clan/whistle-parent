package org.coderclan.whistle;

import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventType;

import java.util.List;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public interface EventPersistenter {
    /**
     * @param type
     * @param content
     * @return Persisted Event ID
     */
    <C extends EventContent> String persistEvent(EventType<C> type, C content);

    /**
     * Mark event as successfully delivered.
     *
     * @param persistentEventId
     */
    void confirmEvent(String persistentEventId);

    List<Event<?>> retrieveUnconfirmedEvent(int count);
}
