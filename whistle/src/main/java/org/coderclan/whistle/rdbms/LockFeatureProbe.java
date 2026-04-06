package org.coderclan.whistle.rdbms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Utility class that probes the database to determine whether a given
 * {@code FOR UPDATE} locking clause is supported.
 *
 * <p>Each probe executes {@code SELECT * FROM <table> WHERE 1=0 FOR UPDATE <clause>}
 * inside a rolled-back transaction on a dedicated connection, so it is
 * side-effect-free and safe to run at startup.</p>
 */
public final class LockFeatureProbe {

    private static final Logger log = LoggerFactory.getLogger(LockFeatureProbe.class);

    private LockFeatureProbe() {
    }

    /**
     * Test whether the database accepts a given {@code FOR UPDATE} clause.
     * Uses {@code WHERE 1=0} so no rows are touched. Always rolls back.
     *
     * @param dataSource the DataSource to obtain a connection from
     * @param tableName  the event table to probe against
     * @param clause     the locking clause to test, e.g. {@code "SKIP LOCKED"} or {@code "NOWAIT"}
     * @return {@code true} if the database accepted the clause, {@code false} otherwise
     */
    @SuppressWarnings("java:S2077")
    public static boolean probeFeature(DataSource dataSource, String tableName, String clause) {
        log.trace("probeFeature() entry — table='{}', clause='{}'", tableName, clause);
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                String sql = "SELECT * FROM " + tableName + " WHERE 1=0 FOR UPDATE " + clause;
                log.trace("Executing probe SQL: {}", sql);
                stmt.execute(sql);
            } finally {
                conn.rollback();
            }
            log.trace("probeFeature() — clause '{}' supported on table '{}'", clause, tableName);
            return true;
        } catch (SQLException e) {
            log.debug("Probe for '{}' not supported: {}", clause, e.getMessage());
            return false;
        }
    }
}
