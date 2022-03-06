package org.coderclan.whistle;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public interface EventQueue {
    boolean offer(Event event);

    Event take() throws InterruptedException;

    boolean contains(Event event);
}
