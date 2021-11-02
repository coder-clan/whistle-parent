package org.coderclan.whistle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Objects;


/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */

public class EventUtil {
    private EventUtil() {
    }

    private static final Logger log = LoggerFactory.getLogger(EventUtil.class);


    /**
     * never throw any exception
     *
     * @param second
     */
    public static void safeSleep(int second) {
        try {
            Thread.sleep(second * 1000L);
        } catch (InterruptedException e) {
            // All threads created by Whistle, are Daemon Thread.
            // It is safe to just terminate these threads.
            try {
                if (log.isInfoEnabled()) {
                    log.info("Thread {} is interrupted.", Thread.currentThread().getName());
                }
            } finally {
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            log.error("", e);
        }
    }

    /**
     * never throw any exception
     *
     * @param message
     * @param e
     */
    public static void safeLog(String message, Exception e) {
        try {
            log.error(message, e);
        } catch (Exception e1) {
            e.printStackTrace();
            e1.printStackTrace();
        }
    }

    private static volatile String databaseName;

    public static String databaseName(DataSource ds) {
        if (!Objects.isNull(databaseName)) {
            return databaseName;
        }

        synchronized (EventUtil.class) {
            while (Objects.isNull(databaseName)) {
                try (Connection con = ds.getConnection()) {
                    databaseName = con.getMetaData().getDatabaseProductName();
                } catch (Exception e) {
                    safeLog("", e);
                    safeSleep(10);
                }
            }
        }
        return databaseName;
    }
}
