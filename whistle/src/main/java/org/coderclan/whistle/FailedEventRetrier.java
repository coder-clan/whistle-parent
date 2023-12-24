package org.coderclan.whistle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Retrieve un-ACKed Events and append them to the Sending Queues.
 *
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public class FailedEventRetrier implements ApplicationListener<ApplicationStartedEvent> {
    private static final Logger log = LoggerFactory.getLogger(FailedEventRetrier.class);

    @Value("${org.coderclan.whistle.retryDelay:10}")
    private int retryDelay;

    private final EventPersistenter eventPersistenter;
    private ScheduledExecutorService scheduler;
    private final EventSender eventSender;

    public FailedEventRetrier(@Autowired(required = false) EventPersistenter eventPersistenter, @Autowired EventSender eventSender) {
        this.eventPersistenter = eventPersistenter;
        this.eventSender = eventSender;
    }

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        if (Objects.isNull(eventPersistenter)) {
            return;
        }

        log.info("Delay for retrying to deliver un-confirmed event is: {}s", retryDelay);

        this.scheduler = Executors.newScheduledThreadPool(1);
        EventRetrierRunnable runnable = new EventRetrierRunnable();
        this.scheduler.scheduleWithFixedDelay(runnable, 0, retryDelay, TimeUnit.SECONDS);
    }

    private class EventRetrierRunnable implements Runnable {
        @Override
        public void run() {
            try {
                List<Event<?>> events;
                do {
                    events = eventPersistenter.retrieveUnconfirmedEvent();
                    if (Objects.isNull(events))
                        return;
                    for (Event<?> e : events) {
                        eventSender.send(e);
                    }
                } while (events.size() == Constants.MAX_QUEUE_COUNT);
            } catch (Exception e) {
                log.error("Exception countered when retrying the failed events.", e);
            }
        }
    }

}
