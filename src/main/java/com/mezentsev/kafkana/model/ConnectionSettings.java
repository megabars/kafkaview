package com.mezentsev.kafkana.model;

public class ConnectionSettings {

    public static final String DEFAULT_BOOTSTRAP   = "localhost:9092";
    public static final int    DEFAULT_MAX_MESSAGES = 100;

    private volatile String bootstrapServers;
    private volatile int    maxMessages;

    public ConnectionSettings() {
        this.bootstrapServers = DEFAULT_BOOTSTRAP;
        this.maxMessages      = DEFAULT_MAX_MESSAGES;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public int getMaxMessages() {
        return maxMessages;
    }

    public void setMaxMessages(int maxMessages) {
        this.maxMessages = Math.max(1, maxMessages);
    }

}
