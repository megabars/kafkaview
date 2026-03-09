package com.kafkaview.ui.dialog;

import com.kafkaview.service.KafkaService;
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
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class SendMessageDialog {

    private final KafkaService kafkaService;
    private final String topic;

    private Stage dialogStage;
    private TextField keyField;
    private TextArea valueArea;
    private Button sendButton;
    private Button cancelButton;
    private Label statusLabel;
    private Label validationLabel;

    private boolean confirmed = false;

    public SendMessageDialog(KafkaService kafkaService, String topic, Stage ownerStage) {
        this.kafkaService = kafkaService;
        this.topic = topic;

        dialogStage = new Stage();
        dialogStage.setTitle("Отправить сообщение");
        dialogStage.initOwner(ownerStage);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setResizable(true);
        dialogStage.setScene(new Scene(buildContent(), 520, 380));
    }

    /**
     * Показывает диалог и ждёт закрытия.
     * @return true, если сообщение успешно отправлено
     */
    public boolean showAndWait() {
        dialogStage.showAndWait();
        return confirmed;
    }

    private VBox buildContent() {
        Label topicLabel = new Label("Топик: " + topic);
        topicLabel.setFont(Font.font(null, FontWeight.BOLD, 13));

        Label keyLabel = new Label("Ключ (необязательно):");
        keyField = new TextField();
        keyField.setPromptText("Оставьте пустым, если ключ не нужен");

        Label valueLabel = new Label("Сообщение:");
        valueLabel.setFont(Font.font(null, FontWeight.BOLD, 12));

        valueArea = new TextArea();
        valueArea.setPromptText("Введите текст сообщения");
        valueArea.setWrapText(true);
        valueArea.setPrefRowCount(8);
        VBox.setVgrow(valueArea, Priority.ALWAYS);

        validationLabel = new Label();
        validationLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #cc0000;");

        statusLabel = new Label();
        statusLabel.setStyle("-fx-font-size: 12px;");

        sendButton = new Button("Отправить");
        sendButton.setDefaultButton(true);
        sendButton.setPrefWidth(100);
        sendButton.setOnAction(e -> onSend());

        cancelButton = new Button("Отмена");
        cancelButton.setPrefWidth(80);
        cancelButton.setOnAction(e -> dialogStage.close());

        HBox buttons = new HBox(10, statusLabel, cancelButton, sendButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        VBox content = new VBox(8,
                topicLabel,
                new Separator(),
                keyLabel,
                keyField,
                valueLabel,
                valueArea,
                validationLabel,
                new Separator(),
                buttons
        );
        content.setPadding(new Insets(20));
        return content;
    }

    private void onSend() {
        String value = valueArea.getText();
        if (value == null || value.isBlank()) {
            validationLabel.setText("Сообщение не может быть пустым");
            return;
        }
        validationLabel.setText("");

        String key = keyField.getText().trim();

        sendButton.setDisable(true);
        cancelButton.setDisable(true);
        statusLabel.setText("Отправка...");
        statusLabel.setTextFill(Color.GRAY);

        kafkaService.sendMessage(topic, key, value)
                .thenRunAsync(() -> {
                    confirmed = true;
                    dialogStage.close();
                }, Platform::runLater)
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        statusLabel.setText("Ошибка: " + cause.getMessage());
                        statusLabel.setTextFill(Color.RED);
                        sendButton.setDisable(false);
                        cancelButton.setDisable(false);
                    });
                    return null;
                });
    }
}
