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
 * Property 34: Preservation — Locking clauses unchanged after fix
 *
 * Validates: Requirements 3.1, 3.2, 3.3
 *
 * For any combination of {@code (supportsSkipLocked, supportsNowait, count)},
 * {@code buildRetrieveSql(count)} produces SQL with the correct locking clause suffix:
 * <ul>
 *   <li>{@code supportsSkipLocked=true} → ends with {@code "for update skip locked"}</li>
 *   <li>{@code supportsNowait=true, supportsSkipLocked=false} → ends with {@code "for update nowait"}</li>
 *   <li>both false → ends with {@code "for update"}</li>
 * </ul>
 *
 * This test should PASS on both unfixed and fixed code, confirming that the
 * locking clause behavior is preserved across the fix.
 */
class LockingClausePreservationProperties {

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
        ds.setURL("jdbc:h2:mem:preservation_test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
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
     * Uses reflection to set the private boolean fields and invoke
     * the private buildRetrieveSql method.
     */
    private String invokeBuildRetrieveSql(AbstractRdbmsEventPersistenter persistenter,
                                          boolean skipLocked, boolean nowait, int count) throws Exception {
        Field skipLockedField = AbstractRdbmsEventPersistenter.class.getDeclaredField("supportsSkipLocked");
        skipLockedField.setAccessible(true);
        skipLockedField.setBoolean(persistenter, skipLocked);

        Field nowaitField = AbstractRdbmsEventPersistenter.class.getDeclaredField("supportsNowait");
        nowaitField.setAccessible(true);
        nowaitField.setBoolean(persistenter, nowait);

        Method buildMethod = AbstractRdbmsEventPersistenter.class.getDeclaredMethod("buildRetrieveSql", int.class);
        buildMethod.setAccessible(true);
        return (String) buildMethod.invoke(persistenter, count);
    }

    /**
     * Property 34: Preservation — Locking clauses unchanged after fix
     *
     * For all (supportsSkipLocked, supportsNowait, count) combinations, verify
     * the correct locking clause suffix is present in the generated SQL.
     *
     * Validates: Requirements 3.1, 3.2, 3.3
     */
    @Property
    @Tag("Feature: whistle-event-system, Property 34: Preservation — Locking clauses unchanged after fix")
    void lockingClausesPreservedForAllPaths(
            @ForAll boolean supportsSkipLocked,
            @ForAll boolean supportsNowait,
            @ForAll("positiveCounts") int count
    ) throws Exception {
        DataSource ds = createDataSource();
        TestPersistenter persistenter = new TestPersistenter(ds);

        String sql = invokeBuildRetrieveSql(persistenter, supportsSkipLocked, supportsNowait, count);

        if (supportsSkipLocked) {
            // Requirement 3.1: SKIP LOCKED path ends with "for update skip locked"
            assert sql.endsWith("for update skip locked") :
                    "Expected SQL to end with 'for update skip locked' when supportsSkipLocked=true, got: " + sql;
        } else if (supportsNowait) {
            // Requirement 3.2: NOWAIT path ends with "for update nowait"
            assert sql.endsWith("for update nowait") :
                    "Expected SQL to end with 'for update nowait' when supportsNowait=true and supportsSkipLocked=false, got: " + sql;
        } else {
            // Requirement 3.3: plain FOR UPDATE path ends with "for update"
            assert sql.endsWith("for update") :
                    "Expected SQL to end with 'for update' when both false, got: " + sql;
            // Also verify it does NOT end with "skip locked" or "nowait"
            assert !sql.endsWith("for update skip locked") :
                    "Should not end with 'for update skip locked' when both false, got: " + sql;
            assert !sql.endsWith("for update nowait") :
                    "Should not end with 'for update nowait' when both false, got: " + sql;
        }
    }

    @Provide
    Arbitrary<Integer> positiveCounts() {
        return Arbitraries.integers().between(1, 100);
    }
}
