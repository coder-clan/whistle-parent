package org.coderclan.whistle;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

@ConfigurationProperties("org.coderclan.whistle")
public class WhistleConfigurationProperties {
    private int retryDelay = 10;
    private String applicationName;


    private String persistentTableName = "sys_event_out";

    @Deprecated
    @Value("${org.coderclan.whistle.table.producedEvent:}")
    private String oldPersistentTableName;

    @Value("${spring.application.name}")
    private String defaultApplicationName;

    public void setRetryDelay(int retryDelay) {
        this.retryDelay = retryDelay;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public void setPersistentTableName(String persistentTableName) {
        this.persistentTableName = persistentTableName;
    }

    public int getRetryDelay() {
        return retryDelay;
    }

    public String getApplicationName() {
        if (Objects.isNull(applicationName)) {
            return defaultApplicationName;
        }
        return applicationName;
    }

    public String getPersistentTableName() {

        // Compatible for old version configuration property: org.coderclan.whistle.table.producedEvent
        if (Objects.equals(this.persistentTableName, "sys_event_out") && !Objects.equals(oldPersistentTableName, "")) {
            return oldPersistentTableName;
        }

        return persistentTableName;
    }


}
