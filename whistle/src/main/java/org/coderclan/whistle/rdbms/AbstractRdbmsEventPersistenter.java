package org.coderclan.whistle.rdbms;

import org.coderclan.whistle.*;
import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventType;
import org.coderclan.whistle.exception.EventPersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public abstract class AbstractRdbmsEventPersistenter implements EventPersistenter {

    private static final Logger log = LoggerFactory.getLogger(AbstractRdbmsEventPersistenter.class);

    protected final DataSource dataSource;

    protected final EventContentSerializer serializer;

    protected final EventTypeRegistrar eventTypeRegistrar;
    protected final String tableName;
    protected final String confirmSql;
    protected final String retrieveSql;
    protected final String[] createTableSql;
    private final String insertSql;

    private final boolean supportsSkipLocked;
    private final boolean supportsNowait;
    private final int retrieveTransactionTimeout;

    protected AbstractRdbmsEventPersistenter(DataSource dataSource, EventContentSerializer serializer, EventTypeRegistrar eventTypeRegistrar, String tableName, int retrieveTransactionTimeout) {
        this.dataSource = dataSource;
        this.serializer = serializer;
        this.eventTypeRegistrar = eventTypeRegistrar;
        this.tableName = tableName;
        this.confirmSql = getConfirmSql();

        this.createTableSql = getCreateTableSql();
        this.insertSql = getInsertSql();

        createTable();

        this.supportsSkipLocked = LockFeatureProbe.probeFeature(dataSource, tableName, "SKIP LOCKED");
        this.supportsNowait = LockFeatureProbe.probeFeature(dataSource, tableName, "NOWAIT");
        this.retrieveSql = buildRetrieveSql(Constants.RETRY_BATCH_COUNT);
        this.retrieveTransactionTimeout = retrieveTransactionTimeout;

        String strategy;
        if (supportsSkipLocked) {
            strategy = "SKIP LOCKED";
        } else if (supportsNowait) {
            strategy = "NOWAIT";
        } else {
            strategy = "FOR UPDATE";
        }
        log.info("Locking strategy for table '{}': {}", tableName, strategy);
        log.info("retrieveTransactionTimeout for table '{}': {}s", tableName, retrieveTransactionTimeout);
    }

    protected abstract String getConfirmSql();

    protected abstract String[] getCreateTableSql();

    protected abstract void fillDbId(PreparedStatement confirmEventStatement, String persistentEventId) throws SQLException;

    /**
     * Return the base retrieve SQL with deterministic ordering, without any locking clause.
     * Used as fallback when neither SKIP LOCKED nor NOWAIT is supported.
     * Orders by retried_count ASC (poison events sink down) then id DESC (newer events first within same retry count).
     * Example: {@code SELECT ... WHERE ... ORDER BY retried_count ASC, id DESC LIMIT n}
     *
     * @param count the maximum number of rows to retrieve
     * @return the ordered base SQL string
     */
    protected abstract String getOrderedBaseRetrieveSql(int count);

    private String buildRetrieveSql(int count) {
        if (supportsSkipLocked) {
            return getOrderedBaseRetrieveSql(count) + " for update skip locked";
        } else if (supportsNowait) {
            return getOrderedBaseRetrieveSql(count) + " for update nowait";
        } else {
            return getOrderedBaseRetrieveSql(count) + " for update";
        }
    }

    /**
     * persistent event to database
     *
     * @param type
     * @param content
     * @return Database Event ID (Primary Key of SYS_UNSENT_EVENT)
     */
    @Override
    public <C extends EventContent> String persistEvent(EventType<C> type, C content) {
        log.trace("persistEvent() entry — type={}", type);

        String json = this.serializer.toJson(content);

        String eventDbId;

        // get Collection of current Transaction.
        // do NOT close this connection, it is managed by spring
        Connection dbCon = DataSourceUtils.getConnection(this.dataSource);

        try (PreparedStatement ps = dbCon.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, type.getName());
            ps.setString(2, json);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            eventDbId = rs.getString(1);
            log.debug("Event persist to database, id={},type={},eventContent={}", eventDbId, type, json);
        } catch (Exception e) {
            throw new EventPersistenceException("Event persist to database failed, type=" + type + ", eventContent=" + json, e);
        }
        return eventDbId;
    }

    protected String getInsertSql() {
        return "insert into " + tableName + " (event_type,event_content)values(?,?)";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmEvent(String persistentEventId) {
        log.trace("confirmEvent() entry — persistentEventId={}", persistentEventId);
        // get Collection of current Transaction.
        // do NOT close this connection, it is managed by spring
        Connection conn = DataSourceUtils.getConnection(this.dataSource);
        try (
                PreparedStatement statement = conn.prepareStatement(confirmSql);
        ) {
            fillDbId(statement, persistentEventId);
            statement.addBatch();
            log.debug("Confirm event: persistentEventId={}", persistentEventId);

            statement.executeBatch();
        } catch (SQLException e) {
            log.error("Failed to update ", e);
        }
    }

    /**
     * Retrieve unconfirmed event.
     *
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Event<?>> retrieveUnconfirmedEvent() {
        log.trace("retrieveUnconfirmedEvent() entry — sql={}", retrieveSql);
        // get Collection of current Transaction.
        // do NOT close this connection, it is managed by spring
        Connection conn = DataSourceUtils.getConnection(this.dataSource);
        boolean originalAutoCommit = false;
        try (
                Statement statement = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE)
        ) {
            statement.setQueryTimeout(this.retrieveTransactionTimeout);

            ArrayList<Event<?>> tempList = new ArrayList<>();

            // set auto commit to false to lock row in database to prevent other thread to requeue.
            originalAutoCommit = conn.getAutoCommit();
            if (originalAutoCommit) {
                conn.setAutoCommit(false);
            }

            ResultSet rs = statement.executeQuery(retrieveSql);
            int eventCount = 0;
            while (rs.next() && eventCount < Constants.RETRY_BATCH_COUNT) {
                eventCount++;
                rs.updateInt(4, rs.getInt(4) + 1);
                rs.updateRow();

                EventType<?> type = eventTypeRegistrar.findEventType(rs.getString(2));
                if (Objects.isNull(type)) {
                    if (log.isErrorEnabled()) {
                        log.error("Unrecognized Event Type: {}.", rs.getString(2));
                    }
                } else {
                    EventContent eventContent = serializer.toEventContent(rs.getString(3), type.getContentType());
                    tempList.add(createEvent(rs.getString(1), type, eventContent));
                }
            }
            rs.close();
            log.trace("retrieveUnconfirmedEvent() — returning {} event(s)", tempList.size());
            return tempList;
        } catch (SQLException e) {
            log.error("Failed to retrieve unconfirmed events (queryTimeout={}s): {}", this.retrieveTransactionTimeout, e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error retrieving unconfirmed events", e);
        } finally {
            try {
                if (originalAutoCommit) {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                log.error("Failed to restore autoCommit on connection", e);
            }
        }

        return Collections.emptyList();
    }

    public void createTable() {
        log.info("Persistent Event table name: {}", this.tableName);
        try (
                Connection conn = dataSource.getConnection();
                Statement statement = conn.createStatement()
        ) {
            for (String sql : createTableSql) {
                executeSingleCreateTableSql(statement, sql);
            }
        } catch (Exception e) {
            log.error("Failed to obtain connection for table creation: tableName={}", this.tableName, e);
        }
    }

    private void executeSingleCreateTableSql(Statement statement, String sql) {
        try {
            statement.execute(sql);
        } catch (SQLException e) {
            log.debug("Table creation SQL skipped (may already exist): {}", sql, e);
        } catch (Exception e) {
            log.error("Failed to execute table creation SQL: {}", sql, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <C extends EventContent> Event<C> createEvent(String id, EventType<C> type, EventContent content) {
        return new Event<>(id, type, (C) content);
    }

}
