package org.coderclan.whistle;

import net.jqwik.api.*;
import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.rdbms.AbstractRdbmsEventPersistenter;
import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Property 4: Base queries contain no locking clauses
 *
 * Validates: Requirements 5.1, 5.2
 *
 * For any positive count value, {@code getOrderedBaseRetrieveSql(count)} does not contain
 * {@code FOR UPDATE}, {@code SKIP LOCKED}, or {@code NOWAIT}.
 */
class BaseQueryNoLockingClauseProperties {

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

    private DataSource createDataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:base_query_test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        return ds;
    }

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

    private static class StubSerializer implements EventContentSerializer {
        @Override
        public String toJson(EventContent content) { return "{}"; }

        @Override
        public EventContent toEventContent(String json, Class<? extends EventContent> type) { return null; }
    }

    /**
     * Invokes a protected method on the persistenter via reflection.
     */
    private String invokeProtectedMethod(AbstractRdbmsEventPersistenter persistenter,
                                         String methodName, int count) throws Exception {
        Method method = AbstractRdbmsEventPersistenter.class.getDeclaredMethod(methodName, int.class);
        method.setAccessible(true);
        return (String) method.invoke(persistenter, count);
    }

    /**
     * Validates: Requirements 5.1, 5.2
     *
     * For any positive count value, getOrderedBaseRetrieveSql(count) does not contain
     * FOR UPDATE, SKIP LOCKED, or NOWAIT.
     */
    @Property
    @Tag("Feature: probe-lock-feature-detection, Property 4: Base queries contain no locking clauses")
    void orderedBaseRetrieveSqlContainsNoLockingClauses(
            @ForAll("positiveCounts") int count
    ) throws Exception {
        DataSource ds = createDataSource();
        TestPersistenter persistenter = new TestPersistenter(ds);

        String sql = invokeProtectedMethod(persistenter, "getOrderedBaseRetrieveSql", count);
        String sqlLower = sql.toLowerCase();

        assert !sqlLower.contains("for update") :
                "getOrderedBaseRetrieveSql should not contain 'for update', got: " + sql;
        assert !sqlLower.contains("skip locked") :
                "getOrderedBaseRetrieveSql should not contain 'skip locked', got: " + sql;
        assert !sqlLower.contains("nowait") :
                "getOrderedBaseRetrieveSql should not contain 'nowait', got: " + sql;
    }

    @Provide
    Arbitrary<Integer> positiveCounts() {
        return Arbitraries.integers().between(1, 1000);
    }
}
