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

    // DB metadata cached
    protected String dbProductName;
    protected String dbProductVersion;
    protected boolean supportsSkipLocked;

    protected AbstractRdbmsEventPersistenter(DataSource dataSource, EventContentSerializer serializer, EventTypeRegistrar eventTypeRegistrar, String tableName) {
        this.dataSource = dataSource;
        this.serializer = serializer;
        this.eventTypeRegistrar = eventTypeRegistrar;
        this.tableName = tableName;
        initMetaData();
        this.confirmSql = getConfirmSql();

        this.retrieveSql = this.getRetrieveSql(Constants.RETRY_BATCH_COUNT, this.supportsSkipLocked);

        this.createTableSql = getCreateTableSql();
        this.insertSql = getInsertSql();

        createTable();
    }

    private void initMetaData() {
        // detect DB product/version and whether SKIP LOCKED is supported
        String prodName = "unknown";
        String prodVersion = "unknown";
        boolean skipSupported = false;
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData md = conn.getMetaData();
            prodName = md.getDatabaseProductName();
            prodVersion = md.getDatabaseProductVersion();
            skipSupported = detectSkipLockedSupport(prodName, prodVersion);
        } catch (SQLException e) {
            log.warn("Failed to detect database product/version, assume SKIP LOCKED unsupported", e);
        }
        this.dbProductName = prodName;
        this.dbProductVersion = prodVersion;
        this.supportsSkipLocked = skipSupported;
    }

    protected abstract String getConfirmSql();

    protected abstract String[] getCreateTableSql();

    /**
     * Retrieve unconfirmed event SQL
     *
     * @param count               number of events to retrieve
     * @param skipLockedSupported whether SKIP LOCKED is supported by database
     * @return SQL that retrieves unconfirmed events
     */
    protected abstract String getRetrieveSql(int count, boolean skipLockedSupported);

    protected abstract void fillDbId(PreparedStatement confirmEventStatement, String persistentEventId) throws SQLException;

    private boolean detectSkipLockedSupport(String productName, String productVersion) {
        if (productName == null) return false;
        String lower = productName.toLowerCase();

        // use the raw productVersion string directly and let compareVersionStrings normalize it
        String ver = productVersion;
        if (ver == null || ver.isEmpty()) return false;

        if (lower.contains("postgresql")) {
            // SKIP LOCKED supported since PG 9.5
            return versionGreaterThan(ver, 9,5);
        }

        if (lower.contains("mariadb")) {
            // MariaDB: SKIP LOCKED supported in MariaDB 10.3+
            return versionGreaterThan(ver, 10,3);
        }
        if (lower.contains("mysql")) {
            // MySQL: SKIP LOCKED supported in MySQL 8.0+
            return versionGreaterThan(ver, 8,0);
        }
        if (lower.contains("oracle")) {
            // Oracle: SKIP LOCKED supported since 9.0
            return versionGreaterThan(ver, 9,0);
        }
        if (lower.contains("h2")) {
            // H2: SKIP LOCKED supported since version 1.4.200
            return versionGreaterThan(ver, 1,5);
        }
        return false;
    }

    private static boolean versionGreaterThan(String ver, int supportMajor, int supportMinor) {
        try {
            String[] a = ver.split("\\.");
            int majorVersion = Integer.parseInt(a[0]);
            int minorVersion = a.length > 1 ? Integer.parseInt(a[1]) : 0;
            return (majorVersion > supportMajor) ||
                    (majorVersion == supportMajor && minorVersion >= supportMinor);
        }catch (Exception e){
            log.warn("Failed to parse major version, assume SUPPORT MINOR", e);
            return false;
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
