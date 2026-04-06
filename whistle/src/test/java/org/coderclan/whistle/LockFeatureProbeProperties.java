package org.coderclan.whistle;

import net.jqwik.api.*;
import org.coderclan.whistle.rdbms.LockFeatureProbe;
import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Property 1: Probe is side-effect-free
 *
 * Validates: Requirements 1.4, 2.1, 2.3
 *
 * For any table state (with zero or more rows) and for any clause string
 * (valid or invalid), executing {@code probeFeature} SHALL leave the table
 * row count unchanged.
 */
class LockFeatureProbeProperties {

    private static final String TABLE_NAME = "whistle_event";

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (\n" +
            "  id int NOT NULL AUTO_INCREMENT,\n" +
            "  event_type varchar(128) DEFAULT NULL,\n" +
            "  retried_count int NOT NULL DEFAULT '0',\n" +
            "  event_content varchar(4096) NOT NULL,\n" +
            "  success boolean NOT NULL default false,\n" +
            "  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
            "  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n" +
            "  PRIMARY KEY (id)\n" +
            ")";

    private static final String INSERT_ROW_SQL =
            "INSERT INTO " + TABLE_NAME + " (event_type, event_content) VALUES ('test_type', 'test_content')";

    private static final String COUNT_SQL =
            "SELECT COUNT(*) FROM " + TABLE_NAME;

    /**
     * Creates a fresh H2 in-memory DataSource with a unique database name
     * to ensure test isolation.
     */
    private DataSource createDataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:probe_test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        return ds;
    }

    private void createTable(DataSource ds) throws SQLException {
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_TABLE_SQL);
        }
    }

    private void insertRows(DataSource ds, int count) throws SQLException {
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            for (int i = 0; i < count; i++) {
                stmt.executeUpdate(INSERT_ROW_SQL);
            }
        }
    }

    private int countRows(DataSource ds) throws SQLException {
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(COUNT_SQL)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Property
    @Tag("Feature: probe-lock-feature-detection, Property 1: Probe is side-effect-free")
    void probeFeatureDoesNotChangeRowCount(
            @ForAll("rowCounts") int initialRows,
            @ForAll("clauseStrings") String clause
    ) throws SQLException {
        DataSource ds = createDataSource();
        createTable(ds);
        insertRows(ds, initialRows);

        int countBefore = countRows(ds);

        LockFeatureProbe.probeFeature(ds, TABLE_NAME, clause);

        int countAfter = countRows(ds);

        assert countBefore == countAfter :
                "probeFeature must not change row count. Before: " + countBefore +
                ", After: " + countAfter + ", clause: '" + clause + "'";
    }

    @Provide
    Arbitrary<Integer> rowCounts() {
        return Arbitraries.integers().between(0, 10);
    }

    /**
     * Property 1 (corollary): Probe never throws
     *
     * Validates: Requirements 2.4, 2.5
     *
     * For any clause string (valid or invalid), {@code probeFeature} returns
     * true or false without throwing any exception.
     */
    @Property
    @Tag("Feature: probe-lock-feature-detection, Property 1 corollary: Probe never throws")
    void probeFeatureNeverThrows(
            @ForAll("clauseStrings") String clause
    ) throws SQLException {
        DataSource ds = createDataSource();
        createTable(ds);

        boolean result = LockFeatureProbe.probeFeature(ds, TABLE_NAME, clause);

        // The return value must be a boolean — true or false.
        // If probeFeature threw, we would never reach this assertion.
        assert result || !result :
                "probeFeature must return a boolean for clause: '" + clause + "'";
    }

    @Provide
    Arbitrary<String> clauseStrings() {
        return Arbitraries.oneOf(
                // Valid SQL locking clauses
                Arbitraries.of("SKIP LOCKED", "NOWAIT", ""),
                // Arbitrary strings that may be invalid SQL
                Arbitraries.strings().ascii().ofMinLength(0).ofMaxLength(50)
        );
    }
}
