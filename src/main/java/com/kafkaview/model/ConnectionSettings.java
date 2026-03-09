package com.kafkaview.model;

public class ConnectionSettings {

    private static final String DEFAULT_BOOTSTRAP = "localhost:9092";
    private static final int MAX_MESSAGES = 100;

    private volatile String bootstrapServers;

    public ConnectionSettings() {
        this.bootstrapServers = DEFAULT_BOOTSTRAP;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    public int getMaxMessages() {
        return MAX_MESSAGES;
    }

    public boolean isConfigured() {
        return bootstrapServers != null && !bootstrapServers.isBlank();
    }
}
