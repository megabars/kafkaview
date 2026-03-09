package com.kafkaview.ui;

import com.kafkaview.model.KafkaMessage;
import com.kafkaview.service.KafkaService;
import com.kafkaview.ui.dialog.MessageDetailDialog;
import com.kafkaview.ui.dialog.SendMessageDialog;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MessageTablePanel {

    private static final int PAGE_SIZE = 30;
    private static final DateTimeFormatter TS_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final KafkaService kafkaService;

    private final TableView<KafkaMessage> tableView;
    // Все загруженные сообщения (полный список для сортировки и пагинации)
    private final List<KafkaMessage> allMessages = new ArrayList<>();
    // Текущая страница — то, что видит таблица
    private final ObservableList<KafkaMessage> pageItems = FXCollections.observableArrayList();

    private final Label titleLabel;
    private final Label statusLabel;
    private final Label pageLabel;
    private final Button prevButton;
    private final Button nextButton;
    private final Button sendButton;
    private final VBox root;

    private Stage ownerStage;
    private int currentPage = 0;
    private String currentTopic = null;

    public MessageTablePanel(KafkaService kafkaService) {
        this.kafkaService = kafkaService;

        titleLabel = new Label("Сообщения");
        titleLabel.setFont(Font.font(null, FontWeight.BOLD, 14));

        sendButton = new Button("Отправить сообщение");
        sendButton.setDisable(true);
        sendButton.setOnAction(e -> openSendDialog());

        tableView = new TableView<>(pageItems);
        tableView.setPlaceholder(new Label("Выберите топик слева"));
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(tableView, Priority.ALWAYS);

        tableView.getColumns().addAll(
                buildValueColumn(),
                buildTimestampColumn(),
                buildPartitionColumn()
        );

        // Двойной клик — открыть детальное окно
        tableView.setRowFactory(tv -> {
            TableRow<KafkaMessage> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openDetailDialog(row.getItem());
                }
            });
            return row;
        });

        // Сортировка: при смене компаратора сортируем весь список и обновляем страницу
        tableView.comparatorProperty().addListener((obs, oldComp, newComp) -> {
            if (newComp != null) {
                allMessages.sort(newComp);
            }
            currentPage = 0;
            refreshPage();
        });

        // --- Элементы пагинации ---
        prevButton = new Button("◀ Пред.");
        prevButton.setDisable(true);
        prevButton.setOnAction(e -> { currentPage--; refreshPage(); });

        nextButton = new Button("След. ▶");
        nextButton.setDisable(true);
        nextButton.setOnAction(e -> { currentPage++; refreshPage(); });

        pageLabel = new Label();
        pageLabel.setStyle("-fx-font-size: 12px;");

        HBox pagination = new HBox(10, prevButton, pageLabel, nextButton);
        pagination.setAlignment(Pos.CENTER);

        statusLabel = new Label();
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");

        HBox header = new HBox(10, titleLabel, sendButton);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        root = new VBox(8, header, tableView, pagination, statusLabel);
        root.setPadding(new Insets(10));
    }

    public Node getView() {
        return root;
    }

    public void setOwnerStage(Stage stage) {
        this.ownerStage = stage;
    }

    public void loadMessages(String topic) {
        currentTopic = topic;
        sendButton.setDisable(false);
        titleLabel.setText("Сообщения — " + topic);
        statusLabel.setText("Загрузка...");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666666;");
        allMessages.clear();
        pageItems.clear();
        pageLabel.setText("");
        prevButton.setDisable(true);
        nextButton.setDisable(true);

        kafkaService.fetchMessagesStreaming(
                topic,

                // onBatch: вызывается на FX-потоке сразу после каждого poll
                batch -> {
                    allMessages.addAll(batch);
                    // Если пользователь уже выбрал сортировку — применяем на лету
                    if (tableView.getComparator() != null) {
                        allMessages.sort(tableView.getComparator());
                    }
                    refreshPage();
                    statusLabel.setText("Загружено: " + allMessages.size() + "…");
                },

                // onComplete: вызывается на FX-потоке когда всё прочитано
                () -> {
                    statusLabel.setText(allMessages.isEmpty()
                            ? "Топик \"" + topic + "\" пуст"
                            : "Всего сообщений: " + allMessages.size());
                },

                // onError
                error -> {
                    Throwable cause = error.getCause() != null ? error.getCause() : error;
                    statusLabel.setText("Ошибка загрузки: " + cause.getMessage());
                    statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #cc0000;");
                }
        );
    }

    // Обновляет содержимое таблицы и состояние кнопок пагинации
    private void refreshPage() {
        int total = allMessages.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) total / PAGE_SIZE));
        currentPage = Math.max(0, Math.min(currentPage, totalPages - 1));

        int from = currentPage * PAGE_SIZE;
        int to   = Math.min(from + PAGE_SIZE, total);

        pageItems.setAll(total > 0 ? allMessages.subList(from, to) : List.of());

        pageLabel.setText(total > 0
                ? "Страница " + (currentPage + 1) + " из " + totalPages
                  + "  (" + (from + 1) + "–" + to + " из " + total + ")"
                : "");

        prevButton.setDisable(currentPage == 0);
        nextButton.setDisable(currentPage >= totalPages - 1);
    }

    private void openDetailDialog(KafkaMessage message) {
        MessageDetailDialog dialog = new MessageDetailDialog(message, ownerStage);
        dialog.show();
    }

    private void openSendDialog() {
        SendMessageDialog dialog = new SendMessageDialog(kafkaService, currentTopic, ownerStage);
        if (dialog.showAndWait()) {
            loadMessages(currentTopic);
        }
    }

    // -----------------------------------------------------------------------
    // Построение колонок таблицы
    // -----------------------------------------------------------------------

    private TableColumn<KafkaMessage, String> buildValueColumn() {
        TableColumn<KafkaMessage, String> col = new TableColumn<>("Сообщение");
        col.setCellValueFactory(new PropertyValueFactory<>("value"));
        col.setPrefWidth(500);
        col.setComparator(String::compareToIgnoreCase);

        col.setCellFactory(c -> new TableCell<>() {
            private final Tooltip tooltip = new Tooltip();

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                } else {
                    setText(item.length() > 120 ? item.substring(0, 120) + "…" : item);
                    tooltip.setText(item.length() > 300 ? item.substring(0, 300) + "…" : item);
                    setTooltip(tooltip);
                }
            }
        });

        return col;
    }

    // Колонка даты: тип Long для корректной числовой сортировки
    private TableColumn<KafkaMessage, Long> buildTimestampColumn() {
        TableColumn<KafkaMessage, Long> col = new TableColumn<>("Дата создания");
        col.setCellValueFactory(data -> data.getValue().timestampProperty().asObject());
        col.setPrefWidth(180);
        col.setMinWidth(160);
        col.setMaxWidth(220);

        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Long ts, boolean empty) {
                super.updateItem(ts, empty);
                if (empty || ts == null || ts <= 0) {
                    setText(null);
                } else {
                    LocalDateTime ldt = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(ts), ZoneId.systemDefault());
                    setText(ldt.format(TS_FORMATTER));
                }
            }
        });

        return col;
    }

    private TableColumn<KafkaMessage, Integer> buildPartitionColumn() {
        TableColumn<KafkaMessage, Integer> col = new TableColumn<>("Партиция");
        col.setCellValueFactory(new PropertyValueFactory<>("partition"));
        col.setPrefWidth(80);
        col.setMinWidth(70);
        col.setMaxWidth(120);
        col.setStyle("-fx-alignment: CENTER;");
        return col;
    }
}
