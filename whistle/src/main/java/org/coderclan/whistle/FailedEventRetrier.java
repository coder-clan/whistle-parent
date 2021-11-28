package org.coderclan.whistle;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Fetch un-ACKed Events form database (SYS_UNSENT_EVENT) periodically, append them to the Sending Queues.
 *
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
public class FailedEventRetrier implements Runnable, ApplicationListener<ApplicationReadyEvent> {
    private static final Logger log = LoggerFactory.getLogger(FailedEventRetrier.class);

    private final DataSource ds;
    private final EventPersistenter persistenter;
    private final ObjectMapper objectMapper;

    private EventTypeRegistrar eventTypeRegistrar;


    public FailedEventRetrier(@Autowired(required = false) DataSource ds, EventPersistenter persistenter, ObjectMapper objectMapper, EventTypeRegistrar eventTypeRegistrar) {
        this.ds = ds;
        this.persistenter = persistenter;
        this.objectMapper = objectMapper;
        this.eventTypeRegistrar = eventTypeRegistrar;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        if (Objects.isNull(ds)) {
            log.info("No DataSource found, Thread RequeueFailedEvent does NOT start.");
            return;
        }

        startThread();
    }

    private void startThread() {
        Thread thread = new Thread(this);
        thread.setName("RequeueFailedEvent");
        thread.setDaemon(true);
        thread.start();
        thread.setUncaughtExceptionHandler((t, e) -> {
            log.error("Uncaught exception of " + t, e);
            EventUtil.safeSleep(10);
            startThread();
        });
    }


    @Override
    public void run() {
        if (ds == null) {
            log.warn("No Datasource injected, Requeue Failed Event thread quit!");
            return;
        }
//        if (EventConstants.eventWaitingForSend.isEmpty()) {
//            log.info("There is no Event to publish. Queue Failed Event Thread exists.");
//            return;
//        }

        String sql = this.persistenter.getSql();
        while (true) {
            try (
                    Connection conn = ds.getConnection();
                    Statement statement = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE)
            ) {
                ArrayList<Event<?>> tempList = new ArrayList<>(10);
                boolean autoCommit = conn.getAutoCommit();
                try {
                    // set auto commit to false to lock row in database to prevent other thread to requeue.
                    conn.setAutoCommit(false);

                    ResultSet rs = statement.executeQuery(sql);
                    while (rs.next()) {
                        rs.updateInt(4, rs.getInt(4) + 1);
                        rs.updateRow();

                        EventType<?> type = eventTypeRegistrar.findEventType(rs.getString(2));
                        if (Objects.isNull(type)) {
                            log.error("Unrecognized Event Type: {}.", rs.getString(2));
                            continue;
                        }

                        EventContent eventContent = objectMapper.readValue(rs.getString(3), type.getContentType());

                        tempList.add(new Event(rs.getLong(1), type, eventContent));
                    }
                    rs.close();
                    conn.commit();
                } finally {
                    // restore auto commit state
                    conn.setAutoCommit(autoCommit);
                }

                for (Event<?> event : tempList) {
                    putEventToQueue(event);
                }

                // Don't sleep if we get MAX_QUEUE_COUNT events. since there may be more failed event in database
                if (tempList.size() != Constants.MAX_QUEUE_COUNT) {
                    EventUtil.safeSleep(10);
                }
            } catch (Exception e) {
                EventUtil.safeLog("", e);
                EventUtil.safeSleep(10);
            }
        }
    }

    private <C extends EventContent> void putEventToQueue(Event<C> event) {
        if (Constants.queue.contains(event)) {
            log.info("Event (persistentEventId={}) is already in the Sending Queue.", event.getPersistentEventId());
            return;
        }

        boolean success = Constants.queue.offer(event);
        if (success) {
            log.info("Requeued persistence event, eventId={} ", event.getPersistentEventId());
        } else {
            log.warn("Put event to queue failed.");
        }
    }


}
