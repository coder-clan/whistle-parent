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
public class PostgresqlEventPersistenter extends AbstractRdbmsEventPersistenter {
    public PostgresqlEventPersistenter(DataSource dataSource, EventContentSerializer serializer, EventTypeRegistrar eventTypeRegistrar, String tableName) {
        super(dataSource, serializer, eventTypeRegistrar, tableName);
    }

    protected String getConfirmSql() {
        return "update " + tableName + " set success=true where id=?";
    }

    @SuppressWarnings("java:S1192")
    protected String[] getCreateTableSql() {
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
                "CREATE TRIGGER trigger_" + tableName + " before update on " + tableName + " for each row execute procedure sys_fun_update_time();"
        };
    }

    protected String getRetrieveSql(int count, boolean skipLockedSupported) {
        String base = "select id,event_type,event_content,retried_count from " + tableName + " where success=false and update_time<current_timestamp - INTERVAL '10' second ";
        if (skipLockedSupported) {
            // When supported, use SKIP LOCKED to avoid waiting on locked rows
            return base + "limit " + count + " for update skip locked";
        } else {
            // When SKIP LOCKED not supported, add deterministic ordering to reduce deadlock risk
            return base + "order by update_time, id limit " + count + " for update";
        }
    }


    protected void fillDbId(PreparedStatement statement, String persistentEventId) throws SQLException {
        statement.setLong(1, Long.parseLong(persistentEventId));
    }
}
