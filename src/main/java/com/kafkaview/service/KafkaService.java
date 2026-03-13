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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class KafkaService {

    private static final Logger log = LoggerFactory.getLogger(KafkaService.class);

    private static final Duration POLL_TIMEOUT    = Duration.ofMillis(500);
    private static final int     ADMIN_TIMEOUT_MS = 10_000;
    private static final int     TEST_TIMEOUT_MS  = 5_000;
    // Максимальное суммарное время ожидания данных от брокера при одном fetch.
    // Если за это время ни одного нового сообщения не пришло, считаем fetch завершённым.
    private static final Duration FETCH_TIMEOUT   = Duration.ofSeconds(10);

    private final ConnectionSettings settings;
    // executor — единственный поток для fetch/send (разделяет состояние producer)
    private final ExecutorService executor;
    // adminExecutor — отдельный пул для listTopics/testConnection (без общего состояния),
    // чтобы они не вставали в очередь за долгим fetchMessagesStreaming.
    private final ExecutorService adminExecutor;

    // Токен отмены текущего fetch. Каждый новый fetchMessagesStreaming() создаёт свой
    // AtomicBoolean и сохраняет его здесь; cancelFetch() устанавливает его в true.
    // Таким образом задачи в очереди (ещё не стартовавшие) немедленно останавливаются
    // при запуске, а не сбрасывают флаг и продолжают работу.
    private volatile AtomicBoolean currentFetchToken = new AtomicBoolean(true); // изначально «отменён»

    // Защита от двойного shutdown().
    private final AtomicBoolean shutdownCalled = new AtomicBoolean(false);

    // Переиспользуемый Producer: создаётся при первой отправке и закрывается при shutdown.
    // Все обращения — исключительно на executor-потоке, синхронизация не нужна.
    private KafkaProducer<String, String> producer;
    private String producerBootstrap; // серверы, для которых создан текущий producer

    public KafkaService(ConnectionSettings settings) {
        this.settings = settings;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "kafka-worker");
            t.setDaemon(true);
            return t;
        });
        AtomicInteger adminCounter = new AtomicInteger(1);
        this.adminExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "kafka-admin-" + adminCounter.getAndIncrement());
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
                        .get(ADMIN_TIMEOUT_MS + 2_000, TimeUnit.MILLISECONDS)
                        .stream()
                        .sorted()
                        .toList();
            } catch (Exception e) {
                log.error("Не удалось получить список топиков", e);
                throw new RuntimeException("Не удалось получить список топиков: " + e.getMessage(), e);
            }
        }, adminExecutor);
    }

    // -----------------------------------------------------------------------
    // Проверка соединения
    // -----------------------------------------------------------------------

    /**
     * Проверяет соединение с указанными bootstrap-серверами без изменения текущих настроек.
     */
    public CompletableFuture<Boolean> testConnection(String bootstrapServers) {
        return CompletableFuture.supplyAsync(() -> {
            Properties props = new Properties();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(TEST_TIMEOUT_MS));
            props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, String.valueOf(TEST_TIMEOUT_MS));
            props.put(AdminClientConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, String.valueOf(TEST_TIMEOUT_MS));
            try (AdminClient admin = AdminClient.create(props)) {
                admin.listTopics(new ListTopicsOptions().timeoutMs(TEST_TIMEOUT_MS))
                        .names()
                        .get(TEST_TIMEOUT_MS + 2_000L, TimeUnit.MILLISECONDS);
                log.info("Проверка соединения успешна: {}", bootstrapServers);
                return true;
            } catch (Exception e) {
                log.warn("Проверка соединения не удалась: {}", e.getMessage());
                return false;
            }
        }, adminExecutor);
    }

    // -----------------------------------------------------------------------
    // Потоковая загрузка сообщений: каждый batch отправляется в UI сразу
    //
    // onBatch    — вызывается на FX-потоке после каждого poll с новыми записями
    // onComplete — вызывается на FX-потоке когда все сообщения прочитаны
    // onError    — вызывается на FX-потоке при ошибке
    // -----------------------------------------------------------------------

    /**
     * Отменяет текущий (и все ожидающие в очереди) fetch-запросы.
     */
    public void cancelFetch() {
        currentFetchToken.set(true);
    }

    public void fetchMessagesStreaming(
            String topic,
            Consumer<List<KafkaMessage>> onBatch,
            Runnable onComplete,
            Consumer<Throwable> onError) {

        // Отменяем предыдущий fetch автоматически, чтобы вызывающий код
        // не обязан вызывать cancelFetch() вручную перед каждым вызовом.
        cancelFetch();
        AtomicBoolean cancelToken = new AtomicBoolean(false);
        currentFetchToken = cancelToken;

        executor.submit(() -> {
            if (cancelToken.get()) return; // задача успела устареть ещё в очереди
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(buildConsumerProps())) {
                // assign() вместо subscribe() — обходим group coordinator и rebalance delay (3с)
                // partitionsFor() делает быстрый metadata-запрос к брокеру
                List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                        .map(pi -> new TopicPartition(pi.topic(), pi.partition()))
                        .toList();
                consumer.assign(partitions);

                Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
                Map<TopicPartition, Long> beginOffsets = consumer.beginningOffsets(partitions);

                // Пустой топик — завершаем сразу (учитываем retention: end == begin)
                long totalAvailable = partitions.stream()
                        .mapToLong(tp -> Math.max(0,
                                endOffsets.getOrDefault(tp, 0L) - beginOffsets.getOrDefault(tp, 0L)))
                        .sum();
                if (totalAvailable == 0) {
                    Platform.runLater(onComplete);
                    return;
                }

                // Seek: начинаем с конца минус квота, перераспределяя квоту
                // от малозаполненных партиций к заполненным.
                int maxMessages = settings.getMaxMessages();
                Map<TopicPartition, Long> seekQuota =
                        distributeQuota(partitions, beginOffsets, endOffsets, maxMessages);
                for (TopicPartition tp : partitions) {
                    long end = endOffsets.getOrDefault(tp, 0L);
                    consumer.seek(tp, Math.max(0, end - seekQuota.getOrDefault(tp, 0L)));
                }

                log.debug("Загрузка топика '{}': {} партиций, квота {} сообщений (доступно: {})",
                        topic, partitions.size(), maxMessages, totalAvailable);

                // Основной цикл чтения — каждый batch сразу отправляем в UI
                int collected = 0;
                // idleDeadline сбрасывается при каждом непустом poll — считаем
                // время простоя брокера, а не суммарное время fetch.
                long idleDeadline = System.currentTimeMillis() + FETCH_TIMEOUT.toMillis();
                while (!cancelToken.get()) {
                    if (System.currentTimeMillis() > idleDeadline) {
                        log.warn("Fetch idle-timeout ({}s) для топика '{}': загружено {} сообщений",
                                FETCH_TIMEOUT.getSeconds(), topic, collected);
                        break;
                    }

                    ConsumerRecords<String, String> records = consumer.poll(POLL_TIMEOUT);

                    if (!records.isEmpty()) {
                        idleDeadline = System.currentTimeMillis() + FETCH_TIMEOUT.toMillis();
                        List<KafkaMessage> batch = new ArrayList<>(records.count());
                        for (ConsumerRecord<String, String> r : records) {
                            if (collected >= maxMessages) break;
                            batch.add(new KafkaMessage(
                                    r.key(), r.value(), r.timestamp(), r.partition(), r.offset()));
                            collected++;
                        }
                        if (!batch.isEmpty()) {
                            // Передаём batch в UI немедленно, не дожидаясь конца топика
                            Platform.runLater(() -> onBatch.accept(batch));
                        }
                        if (collected >= maxMessages) break;
                    }

                    boolean reachedEnd = partitions.stream().allMatch(tp ->
                        consumer.position(tp) >= endOffsets.getOrDefault(tp, 0L));
                    if (reachedEnd) break;
                }

                if (!cancelToken.get()) {
                    Platform.runLater(onComplete);
                }

            } catch (Exception e) {
                if (!cancelToken.get()) {
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
            try {
                KafkaProducer<String, String> p = getOrCreateProducer();
                String resolvedKey = (key == null || key.isBlank()) ? null : key;
                ProducerRecord<String, String> record = new ProducerRecord<>(topic, resolvedKey, value);
                p.send(record).get(); // .get() блокирует до подтверждения; flush() не нужен
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
        if (!shutdownCalled.compareAndSet(false, true)) {
            return; // повторный вызов игнорируем
        }
        cancelFetch();
        // Закрываем producer на executor-потоке перед остановкой
        executor.submit(() -> {
            if (producer != null) {
                producer.close(Duration.ofSeconds(2));
                producer = null;
            }
        });
        executor.shutdown();
        adminExecutor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            if (!adminExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                adminExecutor.shutdownNow();
            }
        } catch (InterruptedException ignored) {
            executor.shutdownNow();
            adminExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // -----------------------------------------------------------------------
    // Вспомогательные методы построения конфигурации
    // -----------------------------------------------------------------------

    /**
     * Возвращает переиспользуемый Producer, пересоздавая его при смене bootstrap-серверов.
     * Вызывается только на executor-потоке.
     */
    private KafkaProducer<String, String> getOrCreateProducer() {
        String bootstrap = settings.getBootstrapServers(); // читаем один раз
        if (producer == null || !bootstrap.equals(producerBootstrap)) {
            if (producer != null) producer.close(Duration.ZERO);
            producerBootstrap = bootstrap;
            producer = new KafkaProducer<>(buildProducerProps(bootstrap));
        }
        return producer;
    }

    private Properties buildAdminProps() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, settings.getBootstrapServers());
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(ADMIN_TIMEOUT_MS));
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, String.valueOf(ADMIN_TIMEOUT_MS));
        props.put(AdminClientConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, "5000");
        return props;
    }

    private Properties buildProducerProps(String bootstrap) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
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
        // Фиксированный group ID: используем assign(), group coordinator не задействован,
        // но GROUP_ID требуется некоторыми брокерами для AdminAPI-совместимости.
        // UUID не нужен — одна постоянная группа не засоряет метаданные кластера.
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "kafkaview-readonly");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500");
        props.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, "15000");
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, "500");
        return props;
    }

    /**
     * Распределяет квоту {@code maxMessages} по партициям с перераспределением
     * от малозаполненных партиций (у которых сообщений меньше равной доли)
     * к более заполненным, чтобы в сумме загружалось как можно ближе к лимиту.
     */
    private Map<TopicPartition, Long> distributeQuota(
            List<TopicPartition> partitions,
            Map<TopicPartition, Long> beginOffsets,
            Map<TopicPartition, Long> endOffsets,
            int maxMessages) {

        Map<TopicPartition, Long> quota = new HashMap<>();
        List<TopicPartition> toDistribute = new ArrayList<>(partitions);
        int remaining = maxMessages;

        while (!toDistribute.isEmpty() && remaining > 0) {
            int perPartition = Math.max(1, remaining / toDistribute.size());
            List<TopicPartition> capped = new ArrayList<>();
            for (TopicPartition tp : toDistribute) {
                long avail = Math.max(0,
                        endOffsets.getOrDefault(tp, 0L) - beginOffsets.getOrDefault(tp, 0L));
                if (avail <= perPartition) {
                    // Math.min предохраняет remaining от ухода в минус когда
                    // perPartition = max(1, remaining/size) и size > remaining.
                    long take = Math.min(avail, remaining);
                    quota.put(tp, take);
                    remaining -= take;
                    capped.add(tp);
                }
            }
            if (capped.isEmpty()) {
                // Все оставшиеся партиции могут принять свою долю.
                // Используем base = remaining/size (а не perPartition), чтобы
                // избежать отрицательной квоты когда remaining < size.
                // Остаток от деления отдаём последней партиции.
                int size  = toDistribute.size();
                int base  = remaining / size;
                int extra = remaining % size;
                for (int i = 0; i < size; i++) {
                    TopicPartition tp = toDistribute.get(i);
                    long share = (i < size - 1) ? base : (long) base + extra;
                    quota.put(tp, share);
                }
                break;
            }
            toDistribute.removeAll(capped);
        }

        return quota;
    }
}
