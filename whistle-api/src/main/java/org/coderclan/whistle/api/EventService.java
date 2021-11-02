package org.coderclan.whistle.api;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public interface EventService {
    /**
     * @param type
     * @param content
     * @param <C>     EventContent
     */
    <C extends EventContent> void publishEvent(EventType<C> type, C content);
}
