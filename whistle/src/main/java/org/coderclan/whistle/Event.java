package org.coderclan.whistle;

import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventType;

import java.util.Objects;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public class Event<C extends EventContent> {
    private long persistentEventId;
    private EventType<C> type;
    private C content;

    public Event(long persistentEventId, EventType<C> type, C content) {
        this.persistentEventId = persistentEventId;
        this.type = type;
        this.content = content;
    }

    public long getPersistentEventId() {
        return persistentEventId;
    }

    public EventType<C> getType() {
        return type;
    }

    public C getContent() {
        return content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Event<?> event = (Event<?>) o;
        return Objects.equals(type, event.type) && Objects.equals(content, event.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, content);
    }
}
