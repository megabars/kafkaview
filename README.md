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

# Fat JAR (target/kafkaview-0.6.0-fat.jar)
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
