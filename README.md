# Kafkana

Десктопное JavaFX приложение для просмотра и отправки сообщений Apache Kafka.

## Возможности

- Просмотр списка топиков Kafka
- Потоковая загрузка последних сообщений с конца каждого партишена
- Пагинация сообщений (30 на страницу), сортировка по любой колонке
- Просмотр полного тела сообщения с форматированием (Текст / JSON / XML)
- Просмотр и отправка заголовков (headers) сообщений
- Повторная отправка сообщения из таблицы или окна просмотра
- Единое окно настроек с тремя разделами:
  - **Подключение** — bootstrap servers, макс. сообщений, проверка связи
  - **Отображение** — формат сообщений по умолчанию (Текст / JSON / XML)
  - **Интерфейс** — язык приложения (Русский / English)
- Настройки сохраняются в `~/.kafkana/settings.properties`
- Локализация: русский и английский языки

## Стек

- **Java 17**
- **JavaFX 21**
- **Apache Kafka Client 3.7.0**
- **Maven**

## Установка

Скачайте готовый дистрибутив для вашей платформы со страницы [Releases](../../releases):

| Платформа | Файл |
|-----------|------|
| macOS (Apple Silicon) | `Kafkana-{version}-mac.dmg` |
| Windows | `Kafkana-{version}-windows.zip` |
| Ubuntu / Debian | `Kafkana-{version}-linux.deb` |

**macOS:** откройте DMG, перетащите Kafkana в Applications.

**Windows:** распакуйте ZIP, запустите `Kafkana\Kafkana.exe`.

**Ubuntu:** `sudo dpkg -i Kafkana-{version}-linux.deb`, затем запустите `kafkana`.

## Сборка из исходников

```bash
# Запустить из исходников
mvn javafx:run

# Fat JAR (target/kafkaview-1.0.0-fat.jar)
mvn package

# macOS DMG с bundled JRE
mvn verify -Pdist-mac

# Windows app-image с bundled JRE
mvn verify -Pdist

# Linux .deb с bundled JRE (требует fakeroot)
mvn verify -Pdist-linux
```

## Настройки

Откройте **Настройки** в меню и укажите параметры:

- **Bootstrap servers** — адрес брокера Kafka, например `localhost:9092` или `host1:9092,host2:9092`
- **Макс. сообщений** — сколько последних сообщений загружать из топика (по умолчанию 100, макс. 10 000)
- **Формат по умолчанию** — формат, который будет выбран при открытии сообщения
- **Язык** — Русский или English (вступает в силу после перезапуска)

Настройки хранятся в `~/.kafkana/settings.properties`.

## Архитектура

```
src/main/java/com/mezentsev/kafkana/
├── MainApp.java                         # Точка входа (JavaFX Application)
├── i18n/
│   └── I18n.java                        # Локализация (ResourceBundle, UTF-8)
├── model/
│   ├── KafkaMessage.java                # Модель сообщения (JavaFX observable properties)
│   ├── ConnectionSettings.java          # Настройки подключения
│   ├── AppSettings.java                 # Все настройки приложения
│   └── AppSettingsPersistence.java      # Хранение настроек в файле
├── service/
│   └── KafkaService.java                # Вся логика работы с Kafka
└── ui/
    ├── MainWindow.java                  # Главное окно (split-pane 30/70)
    ├── TopicListPanel.java              # Панель списка топиков
    ├── MessageTablePanel.java           # Таблица сообщений с пагинацией
    └── dialog/
        ├── AppSettingsDialog.java       # Единое окно настроек (3 вкладки)
        ├── MessageDetailDialog.java     # Детальный просмотр с форматированием
        ├── SendMessageDialog.java       # Отправка сообщений с заголовками
        └── AboutDialog.java             # О программе
```

**Ключевые решения:**
- `KafkaService` использует `assign()` вместо `subscribe()` — без группы потребителей и ребалансировки
- Сообщения читаются с хвоста каждого партишена; `distributeQuota()` распределяет `maxMessages` пропорционально между партишенами
- Kafka I/O выполняется в однопоточном `executor`; `adminExecutor` (2 потока) для `listTopics`/`testConnection` — чтобы не блокировать текущий fetch
- Каждый `fetchMessagesStreaming()` выдаёт новый токен отмены (`AtomicBoolean`); предыдущий fetch прерывается между poll-итерациями
- Счётчик поколений в `MessageTablePanel` отбрасывает устаревшие батчи от отменённых запросов

## Требования

- Java 17+
- Maven 3.8+ (только для сборки из исходников)
- Доступный брокер Apache Kafka

---

# Kafkana (English)

A desktop JavaFX application for browsing and sending Apache Kafka messages.

## Features

- Browse the list of Kafka topics
- Stream the latest messages from the tail of each partition
- Paginated message table (30 per page), sortable by any column
- Full message body viewer with formatting (Text / JSON / XML)
- View and send message headers
- Resend any message directly from the table or the detail dialog
- Unified settings window with three tabs:
  - **Connection** — bootstrap servers, max messages, connection test
  - **Display** — default message format (Text / JSON / XML)
  - **Interface** — application language (Russian / English)
- Settings persisted to `~/.kafkana/settings.properties`
- Localisation: Russian and English

## Stack

- **Java 17**
- **JavaFX 21**
- **Apache Kafka Client 3.7.0**
- **Maven**

## Installation

Download a ready-made distribution for your platform from the [Releases](../../releases) page:

| Platform | File |
|----------|------|
| macOS (Apple Silicon) | `Kafkana-{version}-mac.dmg` |
| Windows | `Kafkana-{version}-windows.zip` |
| Ubuntu / Debian | `Kafkana-{version}-linux.deb` |

**macOS:** open the DMG and drag Kafkana to Applications.

**Windows:** unzip the archive and run `Kafkana\Kafkana.exe`.

**Ubuntu:** `sudo dpkg -i Kafkana-{version}-linux.deb`, then run `kafkana`.

## Building from source

```bash
# Run from source
mvn javafx:run

# Fat JAR (target/kafkaview-1.0.0-fat.jar)
mvn package

# macOS DMG with bundled JRE
mvn verify -Pdist-mac

# Windows app-image with bundled JRE
mvn verify -Pdist

# Linux .deb with bundled JRE (requires fakeroot)
mvn verify -Pdist-linux
```

## Settings

Open **Settings** from the menu and configure:

- **Bootstrap servers** — Kafka broker address, e.g. `localhost:9092` or `host1:9092,host2:9092`
- **Max messages** — how many recent messages to load per topic (default 100, max 10 000)
- **Default format** — format selected automatically when opening a message
- **Language** — Russian or English (takes effect after restart)

Settings are stored in `~/.kafkana/settings.properties`.

## Architecture

```
src/main/java/com/mezentsev/kafkana/
├── MainApp.java                         # Entry point (JavaFX Application)
├── i18n/
│   └── I18n.java                        # Localisation (ResourceBundle, UTF-8)
├── model/
│   ├── KafkaMessage.java                # Message model (JavaFX observable properties)
│   ├── ConnectionSettings.java          # Connection configuration
│   ├── AppSettings.java                 # All application settings
│   └── AppSettingsPersistence.java      # File-based settings storage
├── service/
│   └── KafkaService.java                # All Kafka I/O logic
└── ui/
    ├── MainWindow.java                  # Main window (30/70 split-pane)
    ├── TopicListPanel.java              # Topic list panel
    ├── MessageTablePanel.java           # Paginated message table
    └── dialog/
        ├── AppSettingsDialog.java       # Settings dialog (3 tabs)
        ├── MessageDetailDialog.java     # Detail viewer with formatting
        ├── SendMessageDialog.java       # Send message with headers
        └── AboutDialog.java             # About dialog
```

**Key design decisions:**
- `KafkaService` uses `assign()` instead of `subscribe()` — no consumer group, no rebalance delay
- Messages are read from the tail of each partition; `distributeQuota()` spreads `maxMessages` proportionally across partitions
- Kafka I/O runs on a single-threaded `executor`; a separate 2-thread `adminExecutor` handles `listTopics`/`testConnection` so they never block an ongoing fetch
- Every `fetchMessagesStreaming()` call mints a new cancellation token (`AtomicBoolean`); the previous fetch is interrupted between poll iterations
- A generation counter in `MessageTablePanel` discards stale batches from cancelled requests

## Requirements

- Java 17+
- Maven 3.8+ (source builds only)
- A reachable Apache Kafka broker
