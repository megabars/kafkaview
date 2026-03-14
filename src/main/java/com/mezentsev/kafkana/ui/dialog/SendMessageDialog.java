package com.mezentsev.kafkana.ui.dialog;

import com.mezentsev.kafkana.model.KafkaMessage;
import com.mezentsev.kafkana.service.KafkaService;
import com.mezentsev.kafkana.ui.UiUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SendMessageDialog {

    private final KafkaService kafkaService;
    private final String topic;

    private final Stage dialogStage;
    private TextField keyField;
    private TextArea valueArea;
    private Button sendButton;
    private Button closeButton;
    private Label statusLabel;
    private Label validationLabel;
    private VBox headersBox;

    // true если хотя бы одно сообщение было успешно отправлено в этом сеансе
    private boolean wasSent = false;

    public SendMessageDialog(KafkaService kafkaService, String topic, Stage ownerStage) {
        this(kafkaService, topic, ownerStage, null);
    }

    public SendMessageDialog(KafkaService kafkaService, String topic, Stage ownerStage, KafkaMessage prefill) {
        this.kafkaService = kafkaService;
        this.topic = topic;

        dialogStage = new Stage();
        dialogStage.setTitle("Отправить сообщение");
        dialogStage.initOwner(ownerStage);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setResizable(true);
        dialogStage.setScene(new Scene(buildContent(prefill), 520, 500));
    }

    /**
     * Показывает диалог и ждёт закрытия.
     * @return true, если хотя бы одно сообщение было успешно отправлено
     */
    public boolean showAndWait() {
        dialogStage.showAndWait();
        return wasSent;
    }

    private VBox buildContent(KafkaMessage prefill) {
        Label topicLabel = new Label("Топик: " + topic);
        topicLabel.setFont(Font.font(null, FontWeight.BOLD, 13));

        Label keyLabel = new Label("Ключ (необязательно):");
        keyField = new TextField();
        keyField.setPromptText("Оставьте пустым, если ключ не нужен");

        // --- Секция заголовков ---
        Label headersLabel = new Label("Заголовки:");
        headersLabel.setFont(Font.font(null, FontWeight.BOLD, 12));

        Button addHeaderBtn = new Button("+ Добавить заголовок");
        addHeaderBtn.setOnAction(e -> addHeaderRow());

        headersBox = new VBox(4, addHeaderBtn);
        headersBox.setPadding(new Insets(0));

        // --- Секция сообщения ---
        Label valueLabel = new Label("Сообщение:");
        valueLabel.setFont(Font.font(null, FontWeight.BOLD, 12));

        valueArea = new TextArea();
        valueArea.setPromptText("Введите текст сообщения");
        valueArea.setWrapText(true);
        valueArea.setPrefRowCount(8);
        VBox.setVgrow(valueArea, Priority.ALWAYS);

        validationLabel = new Label();
        validationLabel.getStyleClass().add("status-label-error");

        statusLabel = new Label();
        statusLabel.getStyleClass().add("status-label");

        sendButton = new Button("Отправить");
        sendButton.setDefaultButton(true);
        sendButton.setPrefWidth(100);
        sendButton.setOnAction(e -> onSend());

        closeButton = new Button("Закрыть");
        closeButton.setPrefWidth(80);
        closeButton.setOnAction(e -> dialogStage.close());

        HBox buttons = new HBox(10, statusLabel, closeButton, sendButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        VBox content = new VBox(8,
                topicLabel,
                new Separator(),
                keyLabel,
                keyField,
                headersLabel,
                headersBox,
                new Separator(),
                valueLabel,
                valueArea,
                validationLabel,
                new Separator(),
                buttons
        );
        content.setPadding(new Insets(20));

        if (prefill != null) {
            keyField.setText(prefill.getKey());
            valueArea.setText(prefill.getValue());
            prefill.getHeaders().forEach(this::addHeaderRow);
        }

        return content;
    }

    private void addHeaderRow() {
        addHeaderRow("", "");
    }

    private void addHeaderRow(String name, String value) {
        TextField nameField = new TextField(name);
        nameField.setPromptText("Название");
        nameField.setPrefWidth(160);

        TextField valueField = new TextField(value);
        valueField.setPromptText("Значение");
        HBox.setHgrow(valueField, Priority.ALWAYS);

        Button removeBtn = new Button("×");
        removeBtn.getStyleClass().add("header-remove-btn");

        HBox row = new HBox(6, nameField, valueField, removeBtn);
        row.setAlignment(Pos.CENTER_LEFT);

        removeBtn.setOnAction(e -> headersBox.getChildren().remove(row));

        // Вставляем строку перед кнопкой «+ Добавить заголовок» (последний элемент)
        int insertIndex = headersBox.getChildren().size() - 1;
        headersBox.getChildren().add(insertIndex, row);
    }

    private List<Header> collectHeaders() {
        List<Header> headers = new ArrayList<>();
        for (var node : headersBox.getChildren()) {
            if (!(node instanceof HBox row)) continue;
            if (row.getChildren().size() < 2) continue;
            if (!(row.getChildren().get(0) instanceof TextField nameField)) continue;
            if (!(row.getChildren().get(1) instanceof TextField valueField)) continue;
            String name = nameField.getText().trim();
            if (name.isEmpty()) continue;
            String val = valueField.getText();
            headers.add(new RecordHeader(name, val.getBytes(StandardCharsets.UTF_8)));
        }
        return headers;
    }

    private void onSend() {
        String value = valueArea.getText();
        if (value.isBlank()) {
            validationLabel.setText("Сообщение не может быть пустым");
            return;
        }
        validationLabel.setText("");

        String key = keyField.getText().trim();
        List<Header> headers = collectHeaders();

        sendButton.setDisable(true);
        closeButton.setDisable(true);
        UiUtils.setStatusNormal(statusLabel, "Отправка...");

        kafkaService.sendMessage(topic, key, value, headers)
                .thenRunAsync(() -> {
                    wasSent = true;
                    UiUtils.setStatusNormal(statusLabel, "Сообщение отправлено");
                    sendButton.setDisable(false);
                    closeButton.setDisable(false);
                }, Platform::runLater)
                .exceptionallyAsync(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    String msg = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
                    UiUtils.setStatusError(statusLabel, "Ошибка: " + msg);
                    sendButton.setDisable(false);
                    closeButton.setDisable(false);
                    return null;
                }, Platform::runLater);
    }
}
