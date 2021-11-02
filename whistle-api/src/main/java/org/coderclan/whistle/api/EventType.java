package org.coderclan.whistle.api;

/**
 * Event Type.
 * <p>
 * Implements should override default methods equals and hashCode. since it will be used as Map keys.</p>
 *
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public interface EventType<C extends EventContent> {
    /**
     * * @return name of the Event Type, should be unique in the universe. e.g. com.xxx.user.UserCreated
     */
    String getName();

    /**
     * @return content(event body) class.
     */
    Class<C> getContentType();
}
