package org.coderclan.whistle;


import net.jcip.annotations.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;

/**
 * Handle the Event produced in a Database Transaction.
 * The event handled by this handler will be added into the Sending Queue after The Transaction committing.
 *
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
@ThreadSafe
public class TransactionalEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(TransactionalEventHandler.class);
    private static final ThreadLocal<Queue<Event<?>>> message = new ThreadLocal<>();

    private final EventSender eventSender;

    public TransactionalEventHandler(EventSender eventSender) {
        this.eventSender = eventSender;
    }

    /**
     * Add an event to this handler.
     *
     * @param event
     */
    public void addEvent(Event<?> event) {
        if (Objects.isNull(message.get())) {
            // first time calling this method

            // init ThreadLocal Variable
            message.set(new ArrayDeque<>());

            // register transactionSynchronization to listen on the Transaction Committing Event.
            TransactionSynchronizationManager.registerSynchronization(transactionSynchronization);
            logger.debug("TransactionSynchronizationManager registered.");
        }

        message.get().add(event);
    }


    private TransactionSynchronization transactionSynchronization = new TransactionSynchronization() {
        @Override
        public void suspend() {
            // DO NOTHING
        }

        @Override
        public void resume() {
            // DO NOTHING
        }

        @Override
        public void flush() {
            // DO NOTHING
        }

        @Override
        public void beforeCommit(boolean readOnly) {
            // DO NOTHING
        }

        @Override
        public void beforeCompletion() {
            // DO NOTHING
        }

        @Override
        public void afterCommit() {
            // DO NOTHING
        }

        @Override
        public void afterCompletion(int status) {
            try {
                // put events into the Sending Queue
                if (STATUS_COMMITTED == status) {
                    Queue<Event<?>> q = message.get();
                    sendEvent(q);
                }
            } finally {

                // reset the ThreadLocal
                message.remove();
            }
        }

        private void sendEvent(Queue<Event<?>> q) {
            if (q == null || q.isEmpty()) {
                return;
            }
            try {
                for (Event<?> event : q) {
                    eventSender.send(event);
                }
            } catch (Exception e) {
                logger.error("Sending failed.", e);
            }
        }
    };

}
