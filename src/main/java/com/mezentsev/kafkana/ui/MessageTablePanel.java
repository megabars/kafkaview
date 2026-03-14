package com.mezentsev.kafkana.ui;

import com.mezentsev.kafkana.i18n.I18n;
import com.mezentsev.kafkana.model.AppSettings;
import com.mezentsev.kafkana.model.KafkaMessage;
import com.mezentsev.kafkana.service.KafkaService;
import com.mezentsev.kafkana.ui.dialog.MessageDetailDialog;
import com.mezentsev.kafkana.ui.dialog.SendMessageDialog;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;


public class MessageTablePanel {

    private static final int PAGE_SIZE = 30;

    // Псевдокласс для ячеек с пустым ключом; стиль определяется в app.css
    private static final PseudoClass PSEUDO_EMPTY_KEY = PseudoClass.getPseudoClass("empty-key");

    private final KafkaService kafkaService;
    private final AppSettings appSettings;

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
    private final Button reloadButton;
    private final VBox root;

    private Stage ownerStage;
    private int currentPage = 0;
    private String currentTopic = null;

    // Счётчик поколений: при каждом новом loadMessages() инкрементируется,
    // чтобы устаревшие batch-коллбэки из предыдущего fetch игнорировались.
    private int generation = 0;

    public MessageTablePanel(KafkaService kafkaService, AppSettings appSettings) {
        this.kafkaService = kafkaService;
        this.appSettings = appSettings;

        titleLabel = new Label(I18n.t("messages.panel.title"));
        titleLabel.setFont(Font.font(null, FontWeight.BOLD, 14));

        sendButton = new Button(I18n.t("messages.panel.send"));
        sendButton.setDisable(true);
        sendButton.setOnAction(e -> openSendDialog());

        reloadButton = new Button("⟳");
        reloadButton.setDisable(true);
        reloadButton.getStyleClass().add("reload-button");
        reloadButton.setTooltip(new Tooltip(I18n.t("messages.panel.reload.tooltip")));
        reloadButton.setOnAction(e -> loadMessages(currentTopic));

        tableView = new TableView<>(pageItems);
        tableView.setPlaceholder(new Label(I18n.t("messages.panel.placeholder")));
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(tableView, Priority.ALWAYS);

        // Порядок колонок: Партиция, Сообщение, Дата создания, Ключ, Offset.
        tableView.getColumns().addAll(
                buildPartitionColumn(),
                buildValueColumn(),
                buildTimestampColumn(),
                buildKeyColumn(),
                buildOffsetColumn()
        );

        // Двойной клик — открыть детальное окно; ПКМ — контекстное меню
        tableView.setRowFactory(tv -> {
            TableRow<KafkaMessage> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    openDetailDialog(row.getItem());
                }
            });

            MenuItem resendItem = new MenuItem(I18n.t("messages.panel.resend"));
            resendItem.setOnAction(e -> {
                KafkaMessage msg = row.getItem();
                if (msg != null) openSendDialogWithPrefill(msg);
            });
            ContextMenu contextMenu = new ContextMenu(resendItem);
            row.contextMenuProperty().bind(
                Bindings.when(row.emptyProperty())
                    .then((ContextMenu) null)
                    .otherwise(contextMenu)
            );

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
        prevButton = new Button(I18n.t("messages.panel.prev"));
        prevButton.setDisable(true);
        prevButton.setOnAction(e -> { currentPage--; refreshPage(); });

        nextButton = new Button(I18n.t("messages.panel.next"));
        nextButton.setDisable(true);
        nextButton.setOnAction(e -> { currentPage++; refreshPage(); });

        pageLabel = new Label();
        pageLabel.getStyleClass().add("page-label");

        HBox pagination = new HBox(10, prevButton, pageLabel, nextButton);
        pagination.setAlignment(Pos.CENTER);

        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");

        HBox header = new HBox(10, titleLabel, reloadButton, sendButton);
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
        // Захватываем поколение для этого запроса; предыдущий fetch отменяется
        // автоматически внутри fetchMessagesStreaming().
        final int myGen = ++generation;

        currentTopic = topic;
        sendButton.setDisable(false);
        reloadButton.setDisable(false);
        titleLabel.setText(MessageFormat.format(I18n.t("messages.panel.title.topic"), topic));
        setStatusNormal(I18n.t("messages.panel.loading"));
        allMessages.clear();
        pageItems.clear();
        pageLabel.setText("");
        prevButton.setDisable(true);
        nextButton.setDisable(true);

        kafkaService.fetchMessagesStreaming(
                topic,

                // onBatch: вызывается на FX-потоке сразу после каждого poll
                batch -> {
                    if (generation != myGen) return; // устаревший fetch — игнорируем
                    allMessages.addAll(batch);
                    // Если активна сортировка, держим список отсортированным сразу,
                    // чтобы страница не «прыгала» при завершении загрузки.
                    java.util.Comparator<KafkaMessage> cmp = tableView.getComparator();
                    if (cmp != null) allMessages.sort(cmp);
                    refreshPage();
                    setStatusNormal(MessageFormat.format(
                            I18n.t("messages.panel.loaded"), String.valueOf(allMessages.size())));
                },

                // onComplete: вызывается на FX-потоке когда всё прочитано
                () -> {
                    if (generation != myGen) return;
                    // Применяем сортировку один раз по завершению загрузки
                    if (tableView.getComparator() != null) {
                        allMessages.sort(tableView.getComparator());
                        refreshPage();
                    }
                    setStatusNormal(allMessages.isEmpty()
                            ? MessageFormat.format(I18n.t("messages.panel.empty"), topic)
                            : MessageFormat.format(I18n.t("messages.panel.total"),
                                    String.valueOf(allMessages.size())));
                },

                // onError
                error -> {
                    if (generation != myGen) return;
                    pageLabel.setText("");
                    Throwable cause = error.getCause() != null ? error.getCause() : error;
                    String msg = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
                    setStatusError(MessageFormat.format(I18n.t("messages.panel.error"), msg));
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
                ? MessageFormat.format(I18n.t("messages.panel.page"),
                        String.valueOf(currentPage + 1), String.valueOf(totalPages),
                        String.valueOf(from + 1), String.valueOf(to), String.valueOf(total))
                : "");

        prevButton.setDisable(currentPage == 0);
        nextButton.setDisable(currentPage >= totalPages - 1);
    }

    private void openDetailDialog(KafkaMessage message) {
        MessageDetailDialog dialog = new MessageDetailDialog(
                message, ownerStage,
                () -> openSendDialogWithPrefill(message),
                appSettings.getDefaultMessageFormat());
        dialog.showAndWait();
    }

    private void openSendDialog() {
        SendMessageDialog dialog = new SendMessageDialog(kafkaService, currentTopic, ownerStage);
        if (dialog.showAndWait()) {
            loadMessages(currentTopic);
        }
    }

    private void openSendDialogWithPrefill(KafkaMessage prefill) {
        SendMessageDialog dialog = new SendMessageDialog(kafkaService, currentTopic, ownerStage, prefill);
        if (dialog.showAndWait()) {
            loadMessages(currentTopic);
        }
    }

    // -----------------------------------------------------------------------
    // Хелперы статусной строки
    // -----------------------------------------------------------------------

    private void setStatusNormal(String text) {
        UiUtils.setStatusNormal(statusLabel, text);
    }

    private void setStatusError(String text) {
        UiUtils.setStatusError(statusLabel, text);
    }

    // -----------------------------------------------------------------------
    // Построение колонок таблицы
    // -----------------------------------------------------------------------

    private TableColumn<KafkaMessage, String> buildValueColumn() {
        TableColumn<KafkaMessage, String> col = new TableColumn<>(I18n.t("col.value"));
        col.setCellValueFactory(data -> data.getValue().valueProperty());
        col.setPrefWidth(400);
        col.setMinWidth(150);
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

    private TableColumn<KafkaMessage, String> buildKeyColumn() {
        TableColumn<KafkaMessage, String> col = new TableColumn<>(I18n.t("col.key"));
        col.setCellValueFactory(data -> data.getValue().keyProperty());
        col.setPrefWidth(120);
        col.setMinWidth(60);
        col.setMaxWidth(200);
        col.setComparator(String::compareToIgnoreCase);

        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                boolean emptyKey = empty || item == null || item.isEmpty();
                setText(emptyKey ? null : (item.length() > 40 ? item.substring(0, 40) + "…" : item));
                // PseudoClass не конфликтует с CSS-стилями выделения строки
                pseudoClassStateChanged(PSEUDO_EMPTY_KEY, emptyKey);
            }
        });

        return col;
    }

    // Колонка даты: тип Long для корректной числовой сортировки
    private TableColumn<KafkaMessage, Long> buildTimestampColumn() {
        TableColumn<KafkaMessage, Long> col = new TableColumn<>(I18n.t("col.timestamp"));
        col.setCellValueFactory(data -> data.getValue().timestampProperty().asObject());
        col.setPrefWidth(180);
        col.setMinWidth(160);
        col.setMaxWidth(220);

        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Long ts, boolean empty) {
                super.updateItem(ts, empty);
                if (empty || ts == null) {
                    setText(null);
                } else {
                    setText(KafkaMessage.formatTimestamp(ts));
                }
            }
        });

        return col;
    }

    private TableColumn<KafkaMessage, Integer> buildPartitionColumn() {
        TableColumn<KafkaMessage, Integer> col = new TableColumn<>(I18n.t("col.partition"));
        col.setCellValueFactory(data -> data.getValue().partitionProperty().asObject());
        col.setPrefWidth(80);
        col.setMinWidth(70);
        col.setMaxWidth(120);
        col.setStyle("-fx-alignment: CENTER;");
        return col;
    }

    private TableColumn<KafkaMessage, Long> buildOffsetColumn() {
        TableColumn<KafkaMessage, Long> col = new TableColumn<>(I18n.t("col.offset"));
        col.setCellValueFactory(data -> data.getValue().offsetProperty().asObject());
        col.setPrefWidth(90);
        col.setMinWidth(70);
        col.setMaxWidth(130);
        col.setStyle("-fx-alignment: CENTER_RIGHT;");
        return col;
    }
}
