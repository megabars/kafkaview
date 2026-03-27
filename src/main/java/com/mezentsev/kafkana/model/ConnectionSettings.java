package com.mezentsev.kafkana.model;

public class ConnectionSettings {

    public static final String DEFAULT_BOOTSTRAP         = "localhost:9092";
    public static final int    DEFAULT_MAX_MESSAGES      = 100;
    public static final int    DEFAULT_FETCH_TIMEOUT_SEC = 10;   // idle-timeout ожидания poll
    public static final int    DEFAULT_ADMIN_TIMEOUT_SEC = 10;   // таймаут listTopics / testConnection

    private volatile String bootstrapServers;
    private volatile int    maxMessages;
    private volatile int    fetchTimeoutSec;
    private volatile int    adminTimeoutSec;

    public ConnectionSettings() {
        this.bootstrapServers = DEFAULT_BOOTSTRAP;
        this.maxMessages      = DEFAULT_MAX_MESSAGES;
        this.fetchTimeoutSec  = DEFAULT_FETCH_TIMEOUT_SEC;
        this.adminTimeoutSec  = DEFAULT_ADMIN_TIMEOUT_SEC;
    }

    public String getBootstrapServers() { return bootstrapServers; }
    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public int getMaxMessages() { return maxMessages; }
    public void setMaxMessages(int maxMessages) {
        this.maxMessages = Math.max(1, maxMessages);
    }

    public int getFetchTimeoutSec() { return fetchTimeoutSec; }
    public void setFetchTimeoutSec(int sec) {
        this.fetchTimeoutSec = Math.max(1, Math.min(sec, 300));
    }

    public int getAdminTimeoutSec() { return adminTimeoutSec; }
    public void setAdminTimeoutSec(int sec) {
        this.adminTimeoutSec = Math.max(1, Math.min(sec, 60));
    }
}
