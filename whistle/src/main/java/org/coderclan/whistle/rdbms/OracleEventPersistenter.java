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
    public OracleEventPersistenter(DataSource dataSource, EventContentSerializer serializer, EventTypeRegistrar eventTypeRegistrar, String tableName, int retrieveTransactionTimeout) {
        super(dataSource, serializer, eventTypeRegistrar, tableName, retrieveTransactionTimeout);
    }

    protected String getConfirmSql() {
        return "update " + tableName + " set success=1 where rowid=?";
    }

    @SuppressWarnings("java:S1192")
    protected String[] getCreateTableSql() {
        String seqName = "SEQ_" + tableName;
        String triggerName = "TRIGGER_UPDATE_" + tableName;
        return new String[]{
                "CREATE SEQUENCE " + seqName + "\n",
                "CREATE TABLE " + tableName + "\n" +
                        "(\n" +
                        "  ID NUMBER(*, 0) DEFAULT " + seqName + ".NEXTVAL NOT NULL \n" +
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
                "CREATE OR REPLACE TRIGGER " + triggerName + " \n" +
                        "BEFORE UPDATE ON " + tableName + " \n" +
                        "for each row\n" +
                        "BEGIN\n" +
                        "  :NEW.update_time := current_timestamp;\n" +
                        "END;"
        };
    }

    @Override
    protected String getOrderedBaseRetrieveSql(int count) {
        return "select rowid,event_type,event_content,retried_count from " + tableName
                + " where success=0 and update_time<(systimestamp - INTERVAL '10' second ) "
                + "order by retried_count asc, id desc"

                // select rowid,event_type,event_content,retried_count from sys_persistent_event where success=0 and update_time<(systimestamp - INTERVAL '10' second ) order by retried_count asc, id desc fetch first 32 rows only for update skip locked
                // ORA-02014: 不能从具有 DISTINCT, GROUP BY 等的视图选择 FOR UPDATE
                // the following line can NOT be used.
                //
                //   + " fetch first " + count + " rows only"

                ;
    }


    protected void fillDbId(PreparedStatement statement, String persistentEventId) throws SQLException {
        statement.setString(1, persistentEventId);
    }
}
