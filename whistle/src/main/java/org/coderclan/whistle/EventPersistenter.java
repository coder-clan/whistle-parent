package org.coderclan.whistle;

import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventType;

import java.util.List;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public interface EventPersistenter<ID> {
    /**
     * @param type
     * @param content
     * @return Persisted Event ID
     */
    <C extends EventContent> ID persistEvent(EventType<C> type, C content);

    /**
     * Mark event as successfully delivered.
     *
     * @param persistentEventId
     */
    void confirmEvent(ID... persistentEventId);

    List<Event<ID, ?>> retrieveUnconfirmedEvent(int count);
}
