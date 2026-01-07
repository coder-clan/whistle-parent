package org.coderclan.whistle.rdbms;

import net.jcip.annotations.ThreadSafe;
import org.coderclan.whistle.EventContentSerializer;
import org.coderclan.whistle.EventTypeRegistrar;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.SQLException;


/**
 * @author aray(dot)chou(dot)cn(at)gmail(dot)com
 */
@ThreadSafe
public class OracleEventPersistenter extends AbstractRdbmsEventPersistenter {
    public OracleEventPersistenter(DataSource dataSource, EventContentSerializer serializer, EventTypeRegistrar eventTypeRegistrar, String tableName) {
        super(dataSource, serializer, eventTypeRegistrar, tableName);
    }

    protected String getConfirmSql() {
        return "update " + tableName + " set success=1 where id=?";
    }

    @SuppressWarnings("java:S1192")
    protected String[] getCreateTableSql() {
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
    }

    protected String getRetrieveSql(int count, boolean skipLockedSupported) {
        String base = "select id,event_type,event_content,retried_count from " + tableName + " where success=0 and update_time<(systimestamp - INTERVAL '10' second ) ";
        if (skipLockedSupported) {
            return base + "for update skip locked";
        } else {
            return base + "order by update_time, id for update";
        }
    }


    protected void fillDbId(PreparedStatement statement, String persistentEventId) throws SQLException {
        statement.setString(1, persistentEventId);
    }
}
