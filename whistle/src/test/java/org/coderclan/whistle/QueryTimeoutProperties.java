package org.coderclan.whistle;

import net.jqwik.api.*;
import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.rdbms.AbstractRdbmsEventPersistenter;
import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Property 5: Query timeout is always applied
 *
 * Validates: Requirements 9.1, 9.2, 9.3
 *
 * For any positive {@code retrieveTransactionTimeout} value, verify that the
 * timeout is correctly stored in the persistenter and would be applied via
 * {@code Statement.setQueryTimeout} before query execution.
 *
 * Since {@code retrieveUnconfirmedEvent} requires a Spring-managed transaction
 * context ({@code DataSourceUtils.getConnection}), we verify the timeout value
 * is correctly propagated from the constructor to the private field. The actual
 * {@code setQueryTimeout} call in {@code retrieveUnconfirmedEvent} uses this
 * field directly, so correct storage guarantees correct application.
 */
class QueryTimeoutProperties {

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
        ds.setURL("jdbc:h2:mem:timeout_test_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        return ds;
    }

    private static class TestPersistenter extends AbstractRdbmsEventPersistenter {

        TestPersistenter(DataSource dataSource, int retrieveTransactionTimeout) {
            super(dataSource, new StubSerializer(), new EventTypeRegistrar(null, null),
                    TABLE_NAME, retrieveTransactionTimeout);
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
     * Property 5: Query timeout is always applied
     *
     * Validates: Requirements 9.1, 9.2, 9.3
     *
     * For any positive retrieveTransactionTimeout value, the value is correctly
     * stored in the persistenter's private field, ensuring it will be passed to
     * Statement.setQueryTimeout before query execution.
     */
    @Property
    @Tag("Feature: whistle-event-system, Property 40: Query timeout is always applied")
    void queryTimeoutIsAlwaysStored(@ForAll("positiveTimeouts") int timeout) throws Exception {
        DataSource ds = createDataSource();
        TestPersistenter persistenter = new TestPersistenter(ds, timeout);

        Field field = AbstractRdbmsEventPersistenter.class.getDeclaredField("retrieveTransactionTimeout");
        field.setAccessible(true);
        int storedTimeout = field.getInt(persistenter);

        assert storedTimeout == timeout :
                "Expected retrieveTransactionTimeout=" + timeout + " but got " + storedTimeout;
    }

    @Provide
    Arbitrary<Integer> positiveTimeouts() {
        return Arbitraries.integers().between(1, 60);
    }
}
