package org.coderclan.whistle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.coderclan.whistle.api.EventContent;
import org.coderclan.whistle.api.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.*;

/**
 * Created by aray(dot)chou(dot)cn(at)gmail(dot)com on 1/18/2018.
 */
@Component
//@ConditionalOnBean(DataSource.class)
public class EventPersistenter implements ApplicationListener {
    private static final Logger log = LoggerFactory.getLogger(EventPersistenter.class);
    @Autowired
    private DataSource dataSource;

    @Autowired
    private ObjectMapper mapper;

    @Value("${org.coderclan.whistle.table.producedEvent:sys_event_out}")
    private String tableName;



    @Override
    public void onApplicationEvent(ApplicationEvent applicationEvent) {
        if (applicationEvent instanceof ContextRefreshedEvent) {
            createTable();
        }
    }

    /**
     * @param type
     * @param content
     * @return Database Event ID (Primary Key of SYS_UNSENT_EVENT)
     */
    public <C extends EventContent> long persistEvent(EventType<C> type, C content) {
        String json;
        try {
            json = this.mapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        long eventDbId;
        // persistent event to database;
        String sql = "insert into " + tableName + " (event_type,event_content)values(?,?)";

        // get Collection of current Transaction.
        // do NOT close this connection, it is managed by spring
        java.sql.Connection dbCon = DataSourceUtils.getConnection(this.dataSource);

        try (PreparedStatement ps = dbCon.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, type.getName());
            ps.setString(2, json);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            eventDbId = rs.getLong(1);
            log.info("Event persist to database, id={},type={},eventContent={}", eventDbId, type, json);
        } catch (Exception e) {
            log.error("Event persist to database failed, type={},eventContent={}", type, json);
            throw new RuntimeException(e);
        }
        return eventDbId;
    }

    @Transactional(rollbackFor = Exception.class)
    public void confirmEvent(long... persistentEventId) {
        String sql = "update " + tableName + " set success=true where id=?";
        try (
                Connection conn = dataSource.getConnection();
                PreparedStatement statement = conn.prepareStatement(sql);
        ) {
            for (long id : persistentEventId) {
                statement.setLong(1, id);
                statement.addBatch();
                log.debug("Confirm event: persistentEventId={}", persistentEventId);
            }
            statement.executeBatch();
        } catch (SQLException e) {
            log.error("Failed to update ", e);
        }
    }

    private void createTable() {
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
    public String getSql() {

        final String result;
        String db = EventUtil.databaseName(this.dataSource);
        switch (db) {
            case "MySQL":
                result = "select id,event_type,event_content,retried_count from "  + tableName + " where success=false and update_time<now()- INTERVAL 10 second limit " + Constants.MAX_QUEUE_COUNT + " for update";
                break;
            case "H2":
                result = "select id,event_type,event_content,retried_count from "  + tableName + " where success=false and update_time<DATEADD(second, -10, current_timestamp()) limit " + Constants.MAX_QUEUE_COUNT + " for update";
                break;
            default:
                throw new RuntimeException("Unsupported Database.");
        }
        return result;
    }

}
