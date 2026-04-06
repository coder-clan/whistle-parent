package org.coderclan.whistle;

import net.jqwik.api.*;
import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.rdbms.AbstractRdbmsEventPersistenter;
import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Property 2: Locking clause priority selection
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 4.1, 4.2, 4.3
 *
 * For any combination of {@code (supportsSkipLocked, supportsNowait)},
 * {@code buildRetrieveSql} selects the correct locking clause following
 * strict priority: SKIP LOCKED > NOWAIT > plain FOR UPDATE.
 */
class LockingClausePriorityProperties {

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

    /**
     * Creates a fresh H2 in-memory DataSource with a unique database name.
     */
    private DataSource createDataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:priority_test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        return ds;
    }

    /**
     * Minimal concrete subclass of AbstractRdbmsEventPersistenter for testing.
     * Provides simple H2-compatible SQL implementations.
     */
    private static class TestPersistenter extends AbstractRdbmsEventPersistenter {

        TestPersistenter(DataSource dataSource) {
            super(dataSource, new StubSerializer(), new EventTypeRegistrar(null, null), TABLE_NAME, 5);
        }

        @Override
        protected String getConfirmSql() {
            return "update " + tableName + " set success=true where id=?";
        }

        @Override
        protected String[] getCreateTableSql() {
            return new String[]{CREATE_TABLE_SQL};
        }

        @Override
        protected void fillDbId(PreparedStatement statement, String persistentEventId) throws SQLException {
            statement.setLong(1, Long.parseLong(persistentEventId));
        }

        @Override
        protected String getOrderedBaseRetrieveSql(int count) {
            return "select id,event_type,event_content,retried_count from " + tableName
                    + " where success=false order by retried_count asc, id desc limit " + count;
        }
    }

    /** Stub serializer — not used in this test. */
    private static class StubSerializer implements EventContentSerializer {
        @Override
        public String toJson(EventContent content) { return "{}"; }

        @Override
        public EventContent toEventContent(String json, Class<? extends EventContent> type) { return null; }
    }

    /**
     * Uses reflection to set the private final boolean fields and invoke
     * the private buildRetrieveSql method with the given boolean combination.
     */
    private String invokeBuildRetrieveSql(AbstractRdbmsEventPersistenter persistenter,
                                          boolean skipLocked, boolean nowait, int count) throws Exception {
        // Set supportsSkipLocked
        Field skipLockedField = AbstractRdbmsEventPersistenter.class.getDeclaredField("supportsSkipLocked");
        skipLockedField.setAccessible(true);
        skipLockedField.setBoolean(persistenter, skipLocked);

        // Set supportsNowait
        Field nowaitField = AbstractRdbmsEventPersistenter.class.getDeclaredField("supportsNowait");
        nowaitField.setAccessible(true);
        nowaitField.setBoolean(persistenter, nowait);

        // Invoke buildRetrieveSql(int count)
        Method buildMethod = AbstractRdbmsEventPersistenter.class.getDeclaredMethod("buildRetrieveSql", int.class);
        buildMethod.setAccessible(true);
        return (String) buildMethod.invoke(persistenter, count);
    }

    @Property
    @Tag("Feature: whistle-event-system, Property 37: Locking clause priority selection")
    void lockingClauseFollowsStrictPriority(
            @ForAll boolean supportsSkipLocked,
            @ForAll boolean supportsNowait,
            @ForAll("positiveCounts") int count
    ) throws Exception {
        DataSource ds = createDataSource();
        TestPersistenter persistenter = new TestPersistenter(ds);

        String sql = invokeBuildRetrieveSql(persistenter, supportsSkipLocked, supportsNowait, count);
        String sqlLower = sql.toLowerCase();

        if (supportsSkipLocked) {
            // Requirement 3.1, 4.1: SKIP LOCKED has highest priority
            assert sqlLower.contains("for update skip locked") :
                    "Expected 'for update skip locked' when supportsSkipLocked=true, got: " + sql;
            assert sqlLower.contains("order by") :
                    "All paths should use ordered query after fix, got: " + sql;
        } else if (supportsNowait) {
            // Requirement 3.2, 4.2: NOWAIT is second priority
            assert sqlLower.contains("for update nowait") :
                    "Expected 'for update nowait' when supportsNowait=true and supportsSkipLocked=false, got: " + sql;
            assert !sqlLower.contains("skip locked") :
                    "Should not contain 'skip locked' when supportsSkipLocked=false, got: " + sql;
            assert sqlLower.contains("order by") :
                    "All paths should use ordered query after fix, got: " + sql;
        } else {
            // Requirement 3.3, 3.4, 4.3: plain FOR UPDATE with ordered query
            assert sqlLower.contains("for update") :
                    "Expected 'for update' as fallback, got: " + sql;
            assert !sqlLower.contains("skip locked") :
                    "Should not contain 'skip locked' when neither is supported, got: " + sql;
            assert !sqlLower.contains("nowait") :
                    "Should not contain 'nowait' when neither is supported, got: " + sql;
            assert sqlLower.contains("order by") :
                    "Should use ordered base query with plain FOR UPDATE, got: " + sql;
        }
    }

    /**
     * Property 3: Retrieve SQL always contains FOR UPDATE
     *
     * Validates: Requirements 4.1, 4.2, 4.3
     *
     * For any combination of probe results, the constructed retrieveSql
     * always contains the substring "FOR UPDATE" (case-insensitive).
     */
    @Property
    @Tag("Feature: whistle-event-system, Property 38: Retrieve SQL always contains FOR UPDATE")
    void retrieveSqlAlwaysContainsForUpdate(
            @ForAll boolean supportsSkipLocked,
            @ForAll boolean supportsNowait,
            @ForAll("positiveCounts") int count
    ) throws Exception {
        DataSource ds = createDataSource();
        TestPersistenter persistenter = new TestPersistenter(ds);

        String sql = invokeBuildRetrieveSql(persistenter, supportsSkipLocked, supportsNowait, count);

        assert sql.toLowerCase().contains("for update") :
                "Expected 'for update' in all cases, got: " + sql;
    }

    @Provide
    Arbitrary<Integer> positiveCounts() {
        return Arbitraries.integers().between(1, 100);
    }
}
