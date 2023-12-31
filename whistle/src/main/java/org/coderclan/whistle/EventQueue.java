package org.coderclan.whistle;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 * @deprecated use {@link EventSender} instead.
 */
public interface EventQueue {
    boolean offer(Event event);

    Event take() throws InterruptedException;

    boolean contains(Event event);
}
