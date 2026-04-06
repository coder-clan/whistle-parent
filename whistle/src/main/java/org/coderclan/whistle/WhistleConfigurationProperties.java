package org.coderclan.whistle;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

@ConfigurationProperties("org.coderclan.whistle")
public class WhistleConfigurationProperties {
    private int retryDelay = 10;
    private String applicationName;


    private String persistentTableName = "sys_event_out";

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

    /**
     * Query timeout in seconds for the event retrieval SELECT ... FOR UPDATE statement.
     * Protects against deadlocks when SKIP LOCKED is not available.
     * Default: 5 seconds.
     */
    private int retrieveTransactionTimeout = 5;

    public int getRetrieveTransactionTimeout() {
        return retrieveTransactionTimeout;
    }

    public void setRetrieveTransactionTimeout(int retrieveTransactionTimeout) {
        this.retrieveTransactionTimeout = retrieveTransactionTimeout;
    }

    public String getPersistentTableName() {
        return persistentTableName;
    }


}
