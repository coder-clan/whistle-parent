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

    private final EventPersistenter eventPersistenter;
    private final TransactionalEventHandler transactionalEventHandler;
    private final EventSender eventSender;

    public EventServiceImpl(
            @Autowired(required = false) EventPersistenter eventPersistenter,
            @Autowired TransactionalEventHandler transactionalEventHandler,
            @Autowired EventSender eventSender) {
        this.eventPersistenter = eventPersistenter;
        this.transactionalEventHandler = transactionalEventHandler;
        this.eventSender = eventSender;
    }

    @Override
    public <C extends EventContent> void publishEvent(EventType<C> type, C content) {
        log.debug("Try to send event: eventType={}, content={}", type, content);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            String persistentEventId = eventPersistenter.persistEvent(type, content);
            transactionalEventHandler.addEvent(new Event<>(persistentEventId, type, content));
        } else {
            log.info("Transaction is not active, send event without persisting!");
            this.send(type, content);
        }
    }

    private <C extends EventContent> void send(EventType<C> type, C content) {
        eventSender.send(new Event<>(null, type, content));
    }
}
