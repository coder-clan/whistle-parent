package org.coderclan.whistle;

import net.jcip.annotations.ThreadSafe;
import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Created by aray(dot)chou(dot)cn(at)gmail(dot)com on 1/18/2018.
 */
@ThreadSafe
public class DatabaseEventPersistenter implements EventPersistenter {
    private static final Logger log = LoggerFactory.getLogger(DatabaseEventPersistenter.class);
    public static final String DB_MYSQL = "MySQL";
    public static final String DB_POSTGRESQL = "PostgreSQL";
    public static final String DB_H2 = "H2";
    public static final String DB_ORACLE = "Oracle";
    @Autowired
    private final DataSource dataSource;

    @Autowired
    private final EventContentSerializer serializer;

    @Autowired
    private final EventTypeRegistrar eventTypeRegistrar;

    private final String tableName;
    private final String databaseProduct;
    private final String confirmSql;
    private final String retrieveSql;
    private final String[] createTableSql;

    public DatabaseEventPersistenter(DataSource dataSource, EventContentSerializer serializer, EventTypeRegistrar eventTypeRegistrar, String tableName) {
        this.dataSource = dataSource;
        this.serializer = serializer;
        this.eventTypeRegistrar = eventTypeRegistrar;
        this.tableName = tableName;

        this.databaseProduct = databaseName();
        this.confirmSql = getConfirmSql();
        this.retrieveSql = this.getRetrieveSql(Constants.MAX_QUEUE_COUNT);
        this.createTableSql = getCreateTableSql();

        createTable();
    }

    /**
     * @param type
     * @param content
     * @return Database Event ID (Primary Key of SYS_UNSENT_EVENT)
     */
    @Override
    public <C extends EventContent> String persistEvent(EventType<C> type, C content) {

        String json = this.serializer.toJson(content);

        String eventDbId;
        // persistent event to database;
        String sql = "insert into " + tableName + " (event_type,event_content)values(?,?)";

        // get Collection of current Transaction.
        // do NOT close this connection, it is managed by spring
        Connection dbCon = DataSourceUtils.getConnection(this.dataSource);

        try (PreparedStatement ps = dbCon.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, type.getName());
            ps.setString(2, json);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            eventDbId = rs.getString(1);
            log.info("Event persist to database, id={},type={},eventContent={}", eventDbId, type, json);
        } catch (Exception e) {
            log.error("Event persist to database failed, type={},eventContent={}", type, json);
            throw new RuntimeException(e);
        }
        return eventDbId;
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
            if (Objects.equals(databaseProduct, DB_ORACLE)) {
                statement.setString(1, persistentEventId);
            } else {
                statement.setLong(1, Long.parseLong(persistentEventId));
            }
            statement.addBatch();
            log.debug("Confirm event: persistentEventId={}", persistentEventId);

            statement.executeBatch();
        } catch (SQLException e) {
            log.error("Failed to update ", e);
        }
    }

    private String getConfirmSql() {

        switch (databaseProduct) {
            case DB_MYSQL:
            case DB_H2:
            case DB_POSTGRESQL:
                return "update " + tableName + " set success=true where id=?";
            case DB_ORACLE:
                return "update " + tableName + " set success=1 where rowid=?";
            default:
                throw new RuntimeException("Unsupported Database.");
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
            originalAutoCommit  = conn.getAutoCommit();
            if(originalAutoCommit){
                conn.setAutoCommit(false);
            }

            ResultSet rs = statement.executeQuery(retrieveSql);
            int eventCount = 0;
            while (rs.next()) {
                if (++eventCount > Constants.MAX_QUEUE_COUNT) {
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
            conn.commit();
            return tempList;
        } catch (Exception e) {
            log.error("", e);
        }
        finally {
            try {
                if(originalAutoCommit){
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                log.error("", e);
            }
        }

        return Collections.emptyList();
    }

    private void createTable() {
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

    @SuppressWarnings("java:S1192")
    private String[] getCreateTableSql() {
        switch (databaseProduct) {
            case DB_MYSQL:
                return new String[]{"CREATE TABLE IF NOT EXISTS  " + tableName + " (\n" +
                        "  id int unsigned NOT NULL AUTO_INCREMENT,\n" +
                        "  event_type varchar(128) DEFAULT NULL,\n" +
                        "  retried_count int unsigned NOT NULL DEFAULT '0',\n" +
                        "  event_content varchar(4096) NOT NULL,\n" +
                        "  success boolean NOT NULL default false ,\n" +
                        "  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ,\n" +
                        "  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP ,\n" +
                        "  PRIMARY KEY (id)\n" +
                        ")"};
            case DB_H2:
                return new String[]{"CREATE TABLE IF NOT EXISTS  " + tableName + " (\n" +
                        "  id int NOT NULL AUTO_INCREMENT,\n" +
                        "  event_type varchar(128) DEFAULT NULL,\n" +
                        "  retried_count int NOT NULL DEFAULT '0',\n" +
                        "  event_content varchar(4096) NOT NULL,\n" +
                        "  success boolean NOT NULL default false ,\n" +
                        "  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ,\n" +
                        "  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP ,\n" +
                        "  PRIMARY KEY (id)\n" +
                        ")"};
            case DB_POSTGRESQL:
                return new String[]{"CREATE TABLE IF NOT EXISTS  " + tableName + " (\n" +
                        "  id bigserial PRIMARY KEY,\n" +
                        "  event_type varchar(128) DEFAULT NULL,\n" +
                        "  retried_count int NOT NULL DEFAULT '0',\n" +
                        "  event_content varchar(4096) NOT NULL,\n" +
                        "  success boolean NOT NULL default false ,\n" +
                        "  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ,\n" +
                        "  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP  \n" +
                        ")",
                        "create or replace function sys_fun_update_time() returns trigger AS $$\n" +
                                "begin\n" +
                                "    new.update_time = current_timestamp;\n" +
                                "    return new;\n" +
                                "END;\n" +
                                "$$ language plpgsql;",
                        "CREATE TRIGGER trigger_sys_persistent_event before update on sys_persistent_event for each row execute procedure sys_fun_update_time();"
                };
            case DB_ORACLE:
                return new String[]{
                        "CREATE SEQUENCE SEQ_SYS_PERSISTENT_EVENT\n",
                        "CREATE TABLE SYS_PERSISTENT_EVENT\n" +
                                "(\n" +
                                "  ID NUMBER(*, 0) DEFAULT SEQ_SYS_PERSISTENT_EVENT.NEXTVAL NOT NULL \n" +
                                ", EVENT_TYPE VARCHAR2(128 BYTE) NOT NULL \n" +
                                ", RETRIED_COUNT NUMBER(*, 0) DEFAULT 0 NOT NULL \n" +
                                ", EVENT_CONTENT VARCHAR2(2000 BYTE) NOT NULL \n" +
                                ", SUCCESS NUMBER(1, 0) DEFAULT 0 NOT NULL \n" +
                                ", CREATE_TIME TIMESTAMP(6) DEFAULT current_timestamp NOT NULL \n" +
                                ", UPDATE_TIME TIMESTAMP(6) DEFAULT current_timestamp\n" +
                                ",  PRIMARY KEY (\n" +
                                "    ID \n" +
                                "  )\n" +
                                ")\n",
                        "CREATE OR REPLACE TRIGGER TRIGGER_UPDATE_SYS_PERSISTENT_EVENT \n" +
                                "BEFORE UPDATE ON SYS_PERSISTENT_EVENT \n" +
                                "for each row\n" +
                                "BEGIN\n" +
                                "  :NEW.update_time := current_timestamp;\n" +
                                "END;"
                };

            default:
                throw new RuntimeException("Unsupported Database.");
        }
    }

    private String getRetrieveSql(int count) {
        switch (databaseProduct) {
            case DB_MYSQL:
                return "select id,event_type,event_content,retried_count from " + tableName + " where success=false and update_time<now()- INTERVAL 10 second limit " + count + " for update";
            case DB_H2:
            case DB_POSTGRESQL:
                return "select id,event_type,event_content,retried_count from " + tableName + " where success=false and update_time<current_timestamp - INTERVAL '10' second  limit " + count + " for update";
            case DB_ORACLE:
                return "select rowid,event_type,event_content,retried_count from " + tableName + " where success=0 and update_time<(systimestamp - INTERVAL '10' second ) for update";
            default:
                throw new RuntimeException("Unsupported Database.");
        }
    }


    private String databaseName;

    private String databaseName() {
//        if (!Objects.isNull(databaseName)) {
//            return databaseName;
//        }

        //synchronized (this)
        {
            while (Objects.isNull(databaseName)) {
                try (Connection con = this.dataSource.getConnection()) {
                    databaseName = con.getMetaData().getDatabaseProductName();
                } catch (Exception e) {
                    log.error("", e);
                    try {
                        Thread.sleep(10 * 1000L);
                    } catch (InterruptedException e1) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        return databaseName;
    }
}
