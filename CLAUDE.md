# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kafkana is a JavaFX desktop application for browsing and inspecting Apache Kafka topics and messages. The UI is in Russian.

## Build Commands

```bash
# Compile
mvn clean compile

# Run the application
mvn javafx:run

# Build fat JAR (target/kafkaview-1.0.0-fat.jar)
mvn package

# Build macOS DMG with bundled JRE (target/dist/Kafkana-1.0.0.dmg)
mvn verify -Pdist-mac

# Build Windows .exe distribution with bundled JRE
mvn verify -Pdist
```

There are no tests or linting configured in this project.

**macOS bundled JRE note:** The `dist-mac` profile uses `mac-aarch64` JavaFX classifiers and explicitly adds `java.naming,java.management,java.security.sasl,java.security.jgss` to jlink's `--add-modules` — these are required by the Kafka client and are not pulled in transitively by JavaFX modules alone.

## Architecture

**Stack:** Java 17, JavaFX 21, Apache Kafka Client 3.7.0, SLF4J, Maven

**Package:** `com.mezentsev.kafkana`

**Entry point:** `MainApp.java` — extends `javafx.application.Application`, initializes `ConnectionSettings`, `KafkaService`, and `MainWindow`, cleans up on exit.

**Layer structure:**
- `model/` — `KafkaMessage` (JavaFX observable properties: key, value, timestamp, partition, offset, headers), `ConnectionSettings` (bootstrap servers, max messages limit), `AppSettings` (all settings incl. default format and language), `AppSettingsPersistence` (file-based storage at `~/.kafkana/settings.properties`)
- `service/KafkaService.java` — Kafka I/O on a **single-threaded** `executor` (preserves producer state across calls) plus a 2-thread `adminExecutor` for `listTopics`/`testConnection` so they don't block ongoing fetches. Uses `assign()` instead of `subscribe()` to skip consumer group rebalance. `fetchMessagesStreaming()` seeks near the tail of each partition and delivers batches to a callback as they arrive. `distributeQuota()` spreads `maxMessages` across partitions proportionally, reallocating from under-filled ones.
- `ui/MainWindow.java` — Orchestrates the split-pane layout (30% left / 70% right), menu bar (File → Exit, Settings, Help → About), and wires topic selection to message loading
- `ui/TopicListPanel.java` — `ListView` of topics with a real-time search field (filters by substring), an icon refresh button in the header, and a topic count label at the bottom
- `ui/MessageTablePanel.java` — `TableView` with pagination (30 per page), columns for partition/value/timestamp/key/offset, double-click opens detail dialog, send-message and refresh buttons aligned to the right. Tracks a **generation counter** per fetch; batch callbacks compare generation to discard stale results from cancelled fetches.
- `ui/UiUtils.java` — Static helpers for switching CSS classes on status labels
- `ui/dialog/AppSettingsDialog.java` — Unified settings dialog with three tabs: Connection (bootstrap servers, max messages, test), Display (default message format), Interface (language). Replaces the old `SettingsDialog`.
- `ui/dialog/MessageDetailDialog.java` — Shows full message body with Text/JSON/XML formatting and message headers; includes resend button. Resend uses `Platform.runLater` to open `SendMessageDialog` after this dialog closes (required — `showAndWait` cannot be called from within a closing dialog's handler). JSON pretty-printer is hand-rolled; XML uses `javax.xml.transform` with XXE protection.
- `ui/dialog/SendMessageDialog.java` — Send a message to the current topic; key and headers are optional; shows success/error status inline, stays open for follow-up sends
- `ui/dialog/AboutDialog.java` — App info dialog

**Cancellation:** Each `fetchMessagesStreaming()` call mints a new `AtomicBoolean` token stored in `currentFetchToken`; the previous token is flipped to cancelled. The worker polls the token between polls to exit early.

**Styling:** `src/main/resources/com/mezentsev/kafkana/app.css` — dark theme (`#1e1e1e` background, `#3a7bd5` blue accent). Covers all Modena overrides plus custom classes: `.status-label`, `.status-label-error`, `.reload-button`, `.search-field`, `.topic-sidebar`, `.mono-text-area`, `.header-remove-btn`, etc.

**Localisation:** `i18n/I18n.java` — singleton utility; must be initialised via `I18n.init(language)` before any UI is created. All UI strings are retrieved with `I18n.t("key")` — missing keys return `!key!`. Bundles are in `src/main/resources/com/mezentsev/kafkana/i18n/` (`messages_ru.properties`, `messages_en.properties`), loaded as UTF-8 via a custom `Utf8Control` to handle Cyrillic correctly. Language is stored in `AppSettings` and takes effect after restart.

**App icon:** `src/main/resources/com/mezentsev/kafkana/icon.png` (1024×1024 RGBA) and `icon.icns` — Kafka hub logo + letter K, white on dark squircle background. The squircle shape is baked into the PNG with transparent corners because macOS does not apply the squircle mask to unsigned jpackage apps. Regenerate with Python/Pillow if the icon needs updating; rebuild `.icns` via `iconutil`. The `dist-mac` profile passes `--icon icon.icns` to jpackage. No icon-setting code runs at runtime — the bundle's `.icns` is used as-is by macOS.

**Logging:** `src/main/resources/simplelogger.properties` — suppresses Kafka client INFO noise; only WARN and above are emitted to stderr.

**Key behavior:** Message loading is bounded by `ConnectionSettings.maxMessages` (default 100). The service seeks near the tail of each partition so only recent messages are fetched.
