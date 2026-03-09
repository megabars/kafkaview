# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

KafkaView is a JavaFX desktop application for browsing and inspecting Apache Kafka topics and messages. The UI is in Russian.

## Build Commands

```bash
# Compile
mvn clean compile

# Run the application
mvn javafx:run

# Build fat JAR (target/kafkaview-1.0.0-fat.jar)
mvn package

# Build macOS DMG with bundled JRE (target/dist/KafkaView-1.0.0.dmg)
mvn verify -Pdist-mac

# Build Windows .exe distribution with bundled JRE
mvn verify -Pdist
```

There are no tests or linting configured in this project.

**macOS bundled JRE note:** The `dist-mac` profile uses `mac-aarch64` JavaFX classifiers and explicitly adds `java.naming,java.management,java.security.sasl,java.security.jgss` to jlink's `--add-modules` — these are required by the Kafka client and are not pulled in transitively by JavaFX modules alone.

## Architecture

**Stack:** Java 17, JavaFX 21, Apache Kafka Client 3.7.0, SLF4J, Maven

**Entry point:** `MainApp.java` — extends `javafx.application.Application`, initializes `ConnectionSettings`, `KafkaService`, and `MainWindow`, cleans up on exit.

**Layer structure:**
- `model/` — `KafkaMessage` (JavaFX observable properties: value, timestamp, partition) and `ConnectionSettings` (bootstrap servers, max messages limit)
- `service/KafkaService.java` — All Kafka I/O runs on a single-threaded `ExecutorService`; UI updates go through `Platform.runLater()`. Uses `assign()` instead of `subscribe()` to skip consumer group rebalance. `fetchMessagesStreaming()` seeks to the end of each partition and reads backward, delivering batches to a callback as they arrive.
- `ui/MainWindow.java` — Orchestrates the split-pane layout (30% left / 70% right), menu bar (File → Exit, Settings → Connection), and wires topic selection to message loading
- `ui/TopicListPanel.java` — `ListView` of topics with a refresh button and status label
- `ui/MessageTablePanel.java` — `TableView` with pagination (30 per page), columns for value/timestamp/partition, double-click opens detail dialog
- `ui/dialog/SettingsDialog.java` — Connection config with a "test connection" button
- `ui/dialog/MessageDetailDialog.java` — Shows full message body with Text/JSON/XML formatting (JSON pretty-printer is hand-rolled; XML uses `javax.xml.transform`)

**Key behavior:** Message loading is bounded by `ConnectionSettings.maxMessages` (default 100). The service seeks near the tail of each partition so only recent messages are fetched.
