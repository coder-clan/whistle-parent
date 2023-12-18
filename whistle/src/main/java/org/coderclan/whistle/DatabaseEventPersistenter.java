package org.coderclan.whistle;

import net.jcip.annotations.ThreadSafe;
import org.coderclan.whistle.rdbms.AbstractRdbmsEventPersistenter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Created by aray(dot)chou(dot)cn(at)gmail(dot)com on 1/18/2018.
 *
 * @deprecated use other {@link AbstractRdbmsEventPersistenter}'s implementations instead.
 */
@ThreadSafe
@Deprecated
public class DatabaseEventPersistenter extends AbstractRdbmsEventPersistenter {
    private static final Logger log = LoggerFactory.getLogger(DatabaseEventPersistenter.class);

    public static final String DB_MYSQL = "MySQL";
    public static final String DB_POSTGRESQL = "PostgreSQL";
    public static final String DB_H2 = "H2";
    public static final String DB_ORACLE = "Oracle";

    private final String databaseProduct;
    private String databaseName;

    public DatabaseEventPersistenter(DataSource dataSource, EventContentSerializer serializer, EventTypeRegistrar eventTypeRegistrar, String tableName) {
        super(dataSource, serializer, eventTypeRegistrar, tableName);

        this.databaseProduct = this.databaseName();

    }


    protected String getConfirmSql() {

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

    @SuppressWarnings("java:S1192")
    protected String[] getCreateTableSql() {
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

    protected String getRetrieveSql(int count) {
        switch (databaseProduct) {
            case DB_MYSQL:
                return "select id,event_type,event_content,retried_count from " + tableName + " where success=false and update_time<now()- INTERVAL 10 second limit " + count + " for update";
            case DB_H2:
            case DB_POSTGRESQL:
                return "select id,event_type,event_content,retried_count from " + tableName + " where success=false and update_time<current_timestamp - INTERVAL '10' second  limit " + count + " for update";
            case DB_ORACLE:
                return "select rowid,event_type,event_content,retried_count from " + tableName + " where success=0 and update_time<(systimestamp - INTERVAL '10' second ) for update skip locked";
            default:
                throw new RuntimeException("Unsupported Database.");
        }
    }

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

    protected void fillDbId(PreparedStatement statement, String persistentEventId) throws SQLException {
        if (Objects.equals(databaseProduct, DB_ORACLE)) {
            statement.setString(1, persistentEventId);
        } else {
            statement.setLong(1, Long.parseLong(persistentEventId));
        }
    }
}
