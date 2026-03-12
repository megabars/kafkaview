package com.kafkaview.service;

import com.kafkaview.model.ConnectionSettings;
import com.kafkaview.model.KafkaMessage;
import javafx.application.Platform;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class KafkaService {

    private static final Logger log = LoggerFactory.getLogger(KafkaService.class);

    private static final Duration POLL_TIMEOUT = Duration.ofMillis(500);
    private static final int ADMIN_TIMEOUT_MS = 10_000;

    private final ConnectionSettings settings;
    private final ExecutorService executor;

    // Флаг отмены текущего fetch. Volatile — читается на executor-потоке, пишется на FX-потоке.
    private volatile boolean fetchCancelled = false;

    public KafkaService(ConnectionSettings settings) {
        this.settings = settings;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "kafka-worker");
            t.setDaemon(true);
            return t;
        });
    }

    // -----------------------------------------------------------------------
    // Список топиков через AdminClient
    // -----------------------------------------------------------------------

    public CompletableFuture<List<String>> listTopics() {
        return CompletableFuture.supplyAsync(() -> {
            Properties props = buildAdminProps();
            try (AdminClient admin = AdminClient.create(props)) {
                return admin.listTopics(new ListTopicsOptions().timeoutMs(ADMIN_TIMEOUT_MS))
                        .names()
                        .get(15, TimeUnit.SECONDS)
                        .stream()
                        .sorted()
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.error("Не удалось получить список топиков", e);
                throw new RuntimeException("Не удалось получить список топиков: " + e.getMessage(), e);
            }
        }, executor);
    }

    // -----------------------------------------------------------------------
    // Проверка соединения
    // -----------------------------------------------------------------------

    public CompletableFuture<Boolean> testConnection() {
        return CompletableFuture.supplyAsync(() -> {
            Properties props = buildAdminProps();
            props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000");
            props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "5000");
            try (AdminClient admin = AdminClient.create(props)) {
                admin.listTopics(new ListTopicsOptions().timeoutMs(5000))
                        .names()
                        .get(7, TimeUnit.SECONDS);
                log.info("Проверка соединения успешна: {}", settings.getBootstrapServers());
                return true;
            } catch (Exception e) {
                log.warn("Проверка соединения не удалась: {}", e.getMessage());
                return false;
            }
        }, executor);
    }

    // -----------------------------------------------------------------------
    // Потоковая загрузка сообщений: каждый batch отправляется в UI сразу
    //
    // onBatch    — вызывается на FX-потоке после каждого poll с новыми записями
    // onComplete — вызывается на FX-потоке когда все сообщения прочитаны
    // onError    — вызывается на FX-потоке при ошибке
    // -----------------------------------------------------------------------

    /**
     * Сбрасывает флаг отмены. Вызывается перед началом нового fetch,
     * чтобы прерванный предыдущий fetch остановился на следующей итерации.
     */
    public void cancelFetch() {
        fetchCancelled = true;
    }

    public void fetchMessagesStreaming(
            String topic,
            Consumer<List<KafkaMessage>> onBatch,
            Runnable onComplete,
            Consumer<Throwable> onError) {

        executor.submit(() -> {
            fetchCancelled = false; // сброс в начале каждого fetch (на executor-потоке)
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(buildConsumerProps())) {
                // assign() вместо subscribe() — обходим group coordinator и rebalance delay (3с)
                // partitionsFor() делает быстрый metadata-запрос к брокеру
                List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                        .map(pi -> new TopicPartition(pi.topic(), pi.partition()))
                        .collect(Collectors.toList());
                consumer.assign(partitions);

                Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);

                // Пустой топик — завершаем сразу
                long totalAvailable = endOffsets.values().stream()
                        .mapToLong(Long::longValue).sum();
                if (totalAvailable == 0) {
                    Platform.runLater(onComplete);
                    return;
                }

                // Умный seek: начинаем с offset = endOffset - maxPerPartition,
                // чтобы читать только последние N сообщений без просмотра всего топика
                int maxPerPartition = Math.max(1,
                        settings.getMaxMessages() / Math.max(1, partitions.size()));
                for (TopicPartition tp : partitions) {
                    long end = endOffsets.getOrDefault(tp, 0L);
                    consumer.seek(tp, Math.max(0, end - maxPerPartition));
                }

                log.debug("Загрузка топика '{}': {} партиций, ~{} сообщений на партицию",
                        topic, partitions.size(), maxPerPartition);

                // Основной цикл чтения — каждый batch сразу отправляем в UI
                while (!fetchCancelled) {
                    ConsumerRecords<String, String> records = consumer.poll(POLL_TIMEOUT);

                    if (!records.isEmpty()) {
                        List<KafkaMessage> batch = new ArrayList<>(records.count());
                        for (ConsumerRecord<String, String> r : records) {
                            batch.add(new KafkaMessage(r.key(), r.value(), r.timestamp(), r.partition()));
                        }
                        // Передаём batch в UI немедленно, не дожидаясь конца топика
                        Platform.runLater(() -> onBatch.accept(batch));
                    }

                    boolean reachedEnd = partitions.stream().allMatch(tp ->
                        consumer.position(tp) >= endOffsets.getOrDefault(tp, 0L));
                    if (reachedEnd) break;
                }

                if (!fetchCancelled) {
                    Platform.runLater(onComplete);
                }

            } catch (Exception e) {
                if (!fetchCancelled) {
                    log.error("Ошибка загрузки сообщений из топика '{}'", topic, e);
                    Platform.runLater(() -> onError.accept(
                            new RuntimeException("Не удалось загрузить сообщения из топика \""
                                    + topic + "\": " + e.getMessage(), e)));
                }
            }
        });
    }

    // -----------------------------------------------------------------------
    // Отправка сообщения в топик
    // -----------------------------------------------------------------------

    public CompletableFuture<Void> sendMessage(String topic, String key, String value) {
        return CompletableFuture.runAsync(() -> {
            try (KafkaProducer<String, String> producer = new KafkaProducer<>(buildProducerProps())) {
                String resolvedKey = (key == null || key.isBlank()) ? null : key;
                ProducerRecord<String, String> record = new ProducerRecord<>(topic, resolvedKey, value);
                producer.send(record).get(); // .get() блокирует до подтверждения; flush() не нужен
                log.info("Сообщение отправлено в топик '{}'", topic);
            } catch (Exception e) {
                log.error("Не удалось отправить сообщение в топик '{}'", topic, e);
                throw new RuntimeException("Не удалось отправить сообщение в топик \""
                        + topic + "\": " + e.getMessage(), e);
            }
        }, executor);
    }

    // -----------------------------------------------------------------------
    // Остановка фонового потока
    // -----------------------------------------------------------------------

    public void shutdown() {
        fetchCancelled = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ignored) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // -----------------------------------------------------------------------
    // Вспомогательные методы построения конфигурации
    // -----------------------------------------------------------------------

    private Properties buildAdminProps() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, settings.getBootstrapServers());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(ADMIN_TIMEOUT_MS));
        props.put(AdminClientConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, "5000");
        return props;
    }

    private Properties buildProducerProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, settings.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "10000");
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "15000");
        return props;
    }

    private Properties buildConsumerProps() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, settings.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "kafkaview-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500");
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "10000");
        props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, "15000");
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, "500");
        return props;
    }
}
