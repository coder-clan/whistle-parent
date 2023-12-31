package org.coderclan.whistle;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 * @deprecated use {@link ReactorEventSender} instead.
 */
public class EventQueueImpl implements EventQueue {

    public EventQueueImpl(int eventQueueSize) {
        queue = new LinkedBlockingQueue<>(eventQueueSize);
    }

    private final LinkedBlockingQueue<Event> queue;

    @Override
    public boolean offer(Event event) {
        return queue.offer(event);
    }

    @Override
    public Event take() throws InterruptedException {
        return queue.take();
    }

    @Override
    public boolean contains(Event event) {
        return queue.contains(event);
    }
}
