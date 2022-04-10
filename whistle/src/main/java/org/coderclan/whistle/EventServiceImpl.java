package org.coderclan.whistle;


import net.jcip.annotations.ThreadSafe;
import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventService;
import org.coderclan.whistle.api.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
@ThreadSafe
public class EventServiceImpl implements EventService {
    private static final Logger log = LoggerFactory.getLogger(EventServiceImpl.class);


    @Autowired(required = false)
    private EventPersistenter eventPersistenter;
    @Autowired
    private TransactionalEventHandler transactionalEventHandler;
    @Autowired
    private EventQueue eventQueue;

    @Override
    public <C extends EventContent> void publishEvent(EventType<C> type, C content) {
        log.debug("Try to send event: eventType={}, content={}", type, content);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            Object persistentEventId = eventPersistenter.persistEvent(type, content);
            transactionalEventHandler.addEvent(new Event<>(persistentEventId, type, content));
        } else {
            this.putEventToQueue(-1, type, content);
        }
    }

    private <C extends EventContent> void putEventToQueue(long persistentEventId, EventType<C> type, C content) {
        boolean success = eventQueue.offer(new Event<Object, C>(persistentEventId, type, content));
        if (!success) {
            log.warn("Put event to queue failed.");
        }
    }
}
