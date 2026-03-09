package com.kafkaview.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class KafkaMessage {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final SimpleStringProperty value;
    private final SimpleLongProperty timestamp;
    private final SimpleIntegerProperty partition;

    public KafkaMessage(String value, long timestamp, int partition) {
        this.value = new SimpleStringProperty(value != null ? value : "");
        this.timestamp = new SimpleLongProperty(timestamp);
        this.partition = new SimpleIntegerProperty(partition);
    }

    // --- value ---

    public String getValue() {
        return value.get();
    }

    public void setValue(String value) {
        this.value.set(value);
    }

    public StringProperty valueProperty() {
        return value;
    }

    // --- timestamp ---

    public long getTimestamp() {
        return timestamp.get();
    }

    public void setTimestamp(long ts) {
        this.timestamp.set(ts);
    }

    public LongProperty timestampProperty() {
        return timestamp;
    }

    // --- partition ---

    public int getPartition() {
        return partition.get();
    }

    public void setPartition(int partition) {
        this.partition.set(partition);
    }

    public IntegerProperty partitionProperty() {
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
