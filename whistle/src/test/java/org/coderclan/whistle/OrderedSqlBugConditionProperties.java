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
 * Property 33: Bug Condition — All locking paths use ordered SQL
 *
 * Validates: Requirements 1.1, 1.2, 2.1, 2.2
 *
 * For any combination of {@code (supportsSkipLocked, supportsNowait, count)}
 * where the bug condition holds ({@code supportsSkipLocked=true} OR
 * {@code supportsNowait=true}), {@code buildRetrieveSql(count)} must contain
 * {@code order by retried_count asc, id desc}.
 *
 * On UNFIXED code this test is EXPECTED TO FAIL, confirming the bug exists:
 * the SKIP LOCKED and NOWAIT paths call {@code getBaseRetrieveSql()} which
 * produces SQL without ORDER BY.
 */
class OrderedSqlBugConditionProperties {

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
        ds.setURL("jdbc:h2:mem:bug_condition_test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
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
     * Property 33: Bug Condition — All locking paths use ordered SQL
     *
     * Scoped to bug condition inputs only: supportsSkipLocked=true OR supportsNowait=true.
     * Asserts that buildRetrieveSql(count) contains "order by retried_count asc, id desc".
     *
     * On unfixed code, this WILL FAIL because the SKIP LOCKED and NOWAIT paths
     * call getBaseRetrieveSql() which has no ORDER BY clause.
     *
     * Validates: Requirements 1.1, 1.2, 2.1, 2.2
     */
    @Property
    @Tag("Feature: whistle-event-system")
    @Tag("Property 33: Bug Condition - All locking paths use ordered SQL")
    void allLockingPathsUseOrderedSql(
            @ForAll("bugConditionSkipLocked") boolean supportsSkipLocked,
            @ForAll("bugConditionNowait") boolean supportsNowait,
            @ForAll("positiveCounts") int count
    ) throws Exception {
        // Filter to only bug condition inputs
        Assume.that(supportsSkipLocked || supportsNowait);

        DataSource ds = createDataSource();
        TestPersistenter persistenter = new TestPersistenter(ds);

        String sql = invokeBuildRetrieveSql(persistenter, supportsSkipLocked, supportsNowait, count);
        String sqlLower = sql.toLowerCase();

        assert sqlLower.contains("order by retried_count asc, id desc") :
                "Expected 'order by retried_count asc, id desc' in SQL for bug condition input " +
                "(supportsSkipLocked=" + supportsSkipLocked + ", supportsNowait=" + supportsNowait +
                ", count=" + count + "), got: " + sql;
    }

    @Provide
    Arbitrary<Boolean> bugConditionSkipLocked() {
        return Arbitraries.of(true, false);
    }

    @Provide
    Arbitrary<Boolean> bugConditionNowait() {
        return Arbitraries.of(true, false);
    }

    @Provide
    Arbitrary<Integer> positiveCounts() {
        return Arbitraries.integers().between(1, 100);
    }
}
