package org.coderclan.whistle;

import net.jcip.annotations.ThreadSafe;
import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
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
public class DatabaseEventPersistenter implements ApplicationListener<ContextRefreshedEvent>, EventPersistenter {
    private static final Logger log = LoggerFactory.getLogger(DatabaseEventPersistenter.class);
    @Autowired
    private DataSource dataSource;

    @Autowired
    private EventContentSerializer serializer;

    @Autowired
    private EventTypeRegistrar eventTypeRegistrar;

    @Value("${org.coderclan.whistle.table.producedEvent:sys_event_out}")
    private String tableName;


    @Override
    public void onApplicationEvent(ContextRefreshedEvent applicationEvent) {
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
        String sql = "update " + tableName + " set success=true where id=?";
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement statement = conn.prepareStatement(sql);
        ) {
            statement.setString(1, persistentEventId);
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
     * @param count Max number of events to retrieve.
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Event<?>> retrieveUnconfirmedEvent(int count) {
        try (
                Connection conn = dataSource.getConnection();
                Statement statement = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE)
        ) {
            ArrayList<Event<?>> tempList = new ArrayList<>();

            // set auto commit to false to lock row in database to prevent other thread to requeue.
            conn.setAutoCommit(false);

            ResultSet rs = statement.executeQuery(this.getSql(count));
            while (rs.next()) {
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
        }

        return Collections.emptyList();
    }

    private void createTable() {
        log.info("Persistent Event table name: {}", this.tableName);
        try (
                Connection conn = dataSource.getConnection();
                Statement statement = conn.createStatement()
        ) {

            statement.execute("CREATE TABLE IF NOT EXISTS  " + tableName + " (\n" +
                    "  id int(11) unsigned NOT NULL AUTO_INCREMENT COMMENT '自增id',\n" +
                    "  event_type varchar(128) DEFAULT NULL COMMENT '消息事件Key',\n" +
                    "  retried_count int(10) unsigned NOT NULL DEFAULT '0' COMMENT '重试计数',\n" +
                    "  event_content varchar(4096) NOT NULL COMMENT '消息事件内容',\n" +
                    "  success boolean NOT NULL default false COMMENT '是否发送成功',\n" +
                    "  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',\n" +
                    "  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',\n" +
                    "  PRIMARY KEY (id)\n" +
                    ")");
        } catch (Exception e) {
            log.error("", e);
        }

    }

    public String getSql(int count) {

        final String result;
        String db = databaseName();
        switch (db) {
            case "MySQL":
                result = "select id,event_type,event_content,retried_count from " + tableName + " where success=false and update_time<now()- INTERVAL 10 second limit " + count + " for update";
                break;
            case "H2":
                result = "select id,event_type,event_content,retried_count from " + tableName + " where success=false and update_time<DATEADD(second, -10, current_timestamp()) limit " + count + " for update";
                break;
            default:
                throw new RuntimeException("Unsupported Database.");
        }
        return result;
    }


    private String databaseName;

    private String databaseName() {
        if (!Objects.isNull(databaseName)) {
            return databaseName;
        }

        synchronized (this) {
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
