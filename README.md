# KafkaView

Десктопное JavaFX приложение для просмотра топиков и сообщений Apache Kafka.

## Возможности

- Просмотр списка топиков Kafka
- Чтение последних сообщений из топиков (потоковая загрузка с конца)
- Пагинация сообщений (30 на страницу)
- Просмотр полного тела сообщения с форматированием (Text / JSON / XML)
- Отправка сообщений в топик
- Настройка подключения с проверкой связи
- Интерфейс на русском языке

## Стек

- **Java 17**
- **JavaFX 21**
- **Apache Kafka Client 3.7.0**
- **Maven**

## Сборка и запуск

### Запуск из исходников

```bash
# Скомпилировать
mvn clean compile

# Запустить приложение
mvn javafx:run
```

### Сборка дистрибутива

```bash
# Fat JAR (target/kafkaview-1.0.0-fat.jar)
mvn package

# macOS DMG с bundled JRE (target/dist/KafkaView-1.0.0.dmg)
mvn verify -Pdist-mac

# Windows .exe с bundled JRE (target/dist/KafkaView/)
mvn verify -Pdist
```

## Настройка подключения

При первом запуске откройте **Настройки → Подключение** и укажите:

- **Bootstrap servers** — адрес брокера Kafka, например `localhost:9092`
- **Макс. сообщений** — количество последних сообщений для загрузки (по умолчанию 100)

## Архитектура

```
src/main/java/com/kafkaview/
├── MainApp.java                        # Точка входа (JavaFX Application)
├── model/
│   ├── KafkaMessage.java               # Модель сообщения (JavaFX properties)
│   └── ConnectionSettings.java         # Настройки подключения
├── service/
│   └── KafkaService.java               # Вся логика работы с Kafka
└── ui/
    ├── MainWindow.java                  # Главное окно (split-pane 30/70)
    ├── TopicListPanel.java              # Панель списка топиков
    ├── MessageTablePanel.java           # Таблица сообщений с пагинацией
    └── dialog/
        ├── SettingsDialog.java          # Диалог настроек подключения
        ├── MessageDetailDialog.java     # Детальный просмотр сообщения
        └── SendMessageDialog.java       # Отправка сообщений
```

**Ключевые решения:**
- `KafkaService` использует `assign()` вместо `subscribe()` — без группы потребителей и ребалансировки
- Сообщения читаются с хвоста каждого партишена в обратном порядке
- Все Kafka I/O выполняется в однопоточном `ExecutorService`; обновления UI через `Platform.runLater()`

## Требования

- Java 17+
- Maven 3.8+
- Доступный брокер Apache Kafka
