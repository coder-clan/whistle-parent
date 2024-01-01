package org.coderclan.whistle.rdbms;

import org.coderclan.whistle.*;
import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventType;
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

    protected AbstractRdbmsEventPersistenter(DataSource dataSource, EventContentSerializer serializer, EventTypeRegistrar eventTypeRegistrar, String tableName) {
        this.dataSource = dataSource;
        this.serializer = serializer;
        this.eventTypeRegistrar = eventTypeRegistrar;
        this.tableName = tableName;

        this.confirmSql = getConfirmSql();
        this.retrieveSql = this.getRetrieveSql(Constants.RETRY_BATCH_COUNT);
        this.createTableSql = getCreateTableSql();
        this.insertSql = getInsertSql();

        createTable();
    }

    protected abstract String getConfirmSql();

    protected abstract String[] getCreateTableSql();

    protected abstract String getRetrieveSql(int count);

    protected abstract void fillDbId(PreparedStatement confirmEventStatement, String persistentEventId) throws SQLException;

    /**
     * persistent event to database
     *
     * @param type
     * @param content
     * @return Database Event ID (Primary Key of SYS_UNSENT_EVENT)
     */
    @Override
    public <C extends EventContent> String persistEvent(EventType<C> type, C content) {

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
            log.error("Event persist to database failed, type={},eventContent={}", type, json);
            throw new RuntimeException(e);
        }
        return eventDbId;
    }

    protected String getInsertSql() {
        return "insert into " + tableName + " (event_type,event_content)values(?,?)";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmEvent(String persistentEventId) {
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
        // get Collection of current Transaction.
        // do NOT close this connection, it is managed by spring
        Connection conn = DataSourceUtils.getConnection(this.dataSource);
        boolean originalAutoCommit = false;
        try (
                Statement statement = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE)
        ) {
            ArrayList<Event<?>> tempList = new ArrayList<>();

            // set auto commit to false to lock row in database to prevent other thread to requeue.
            originalAutoCommit = conn.getAutoCommit();
            if (originalAutoCommit) {
                conn.setAutoCommit(false);
            }

            ResultSet rs = statement.executeQuery(retrieveSql);
            int eventCount = 0;
            while (rs.next()) {
                if (++eventCount > Constants.RETRY_BATCH_COUNT) {
                    break;
                }
                rs.updateInt(4, rs.getInt(4) + 1);
                rs.updateRow();

                EventType<?> type = eventTypeRegistrar.findEventType(rs.getString(2));
                if (Objects.isNull(type)) {
                    log.error("Unrecognized Event Type: {}.", rs.getString(2));
                    continue;
                }

                EventContent eventContent = serializer.toEventContent(rs.getString(3), type.getContentType());

                tempList.add(new Event(rs.getString(1), type, eventContent));
            }
            rs.close();
            return tempList;
        } catch (Exception e) {
            log.error("", e);
        } finally {
            try {
                if (originalAutoCommit) {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                log.error("", e);
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
                try {
                    statement.execute(sql);
                } catch (SQLException e) {
                    log.debug("", e);
                } catch (Exception e) {
                    log.error(sql, e);
                }
            }
        } catch (Exception e) {
            log.error("", e);
        }
    }

}
