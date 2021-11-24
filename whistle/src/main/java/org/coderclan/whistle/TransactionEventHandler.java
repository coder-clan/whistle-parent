package org.coderclan.whistle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;

/**
 * Handle the Event produced in an Database Transaction.
 * The event handled by this handler will be added into the Sending Queue after The Transaction committing.
 *
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public class TransactionEventHandler {
    private static final Logger logger = LoggerFactory.getLogger(TransactionEventHandler.class);
    private static final ThreadLocal<Queue<Event<?>>> message = new ThreadLocal<>();


    /**
     * Add an event to this handler.
     *
     * @param event
     */
    public void addEvent(Event<?> event) {
        if (Objects.isNull(message.get())) {
            // first time call this method

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
                    enqueueEvent(q);
                }
            } finally {

                // reset the ThreadLocal
                message.remove();
            }
        }

        private void enqueueEvent(Queue<Event<?>> q) {
            if (q == null || q.isEmpty()) {
                return;
            }
            try {
                for (Event<?> event : q) {
                    Constants.queue.offer(event);
                }
            } catch (Exception e) {
                logger.error("", e);
            }
        }
    };

}
