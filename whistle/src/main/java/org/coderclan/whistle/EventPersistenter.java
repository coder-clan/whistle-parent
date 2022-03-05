package org.coderclan.whistle;

import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventType;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public interface EventPersistenter {
    /**
     * @param type
     * @param content
     * @return Persisted Event ID
     */
    <C extends EventContent> long persistEvent(EventType<C> type, C content);

    /**
     * Mark event as successfully delivered.
     *
     * @param persistentEventId
     */
    @Transactional(rollbackFor = Exception.class)
    void confirmEvent(long... persistentEventId);
}
