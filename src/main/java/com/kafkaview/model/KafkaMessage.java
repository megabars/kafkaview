package com.kafkaview.model;

import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class KafkaMessage {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final SimpleStringProperty key;
    private final SimpleStringProperty value;
    private final SimpleLongProperty timestamp;
    private final SimpleIntegerProperty partition;

    public KafkaMessage(String key, String value, long timestamp, int partition) {
        this.key       = new SimpleStringProperty(key   != null ? key   : "");
        this.value     = new SimpleStringProperty(value != null ? value : "");
        this.timestamp = new SimpleLongProperty(timestamp);
        this.partition = new SimpleIntegerProperty(partition);
    }

    // --- key ---

    public String getKey() {
        return key.get();
    }

    public ReadOnlyStringProperty keyProperty() {
        return key;
    }

    // --- value ---

    public String getValue() {
        return value.get();
    }

    public ReadOnlyStringProperty valueProperty() {
        return value;
    }

    // --- timestamp ---

    public long getTimestamp() {
        return timestamp.get();
    }

    public ReadOnlyLongProperty timestampProperty() {
        return timestamp;
    }

    // --- partition ---

    public int getPartition() {
        return partition.get();
    }

    public ReadOnlyIntegerProperty partitionProperty() {
        return partition;
    }

    // --- helpers ---

    public String getFormattedTimestamp() {
        if (getTimestamp() <= 0) {
            return "—";
        }
        LocalDateTime ldt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(getTimestamp()),
                ZoneId.systemDefault()
        );
        return ldt.format(FORMATTER);
    }
}
