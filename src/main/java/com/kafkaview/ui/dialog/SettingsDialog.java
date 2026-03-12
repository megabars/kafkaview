package com.kafkaview.ui.dialog;

import com.kafkaview.model.ConnectionSettings;
import com.kafkaview.service.KafkaService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class SettingsDialog {

    private final ConnectionSettings settings;
    private final KafkaService kafkaService;

    private Stage dialogStage;
    private TextField bootstrapField;
    private TextField maxMessagesField;
    private Button testButton;
    private Button okButton;
    private Label testResultLabel;
    private Label validationLabel;

    private boolean confirmed = false;

    public SettingsDialog(ConnectionSettings settings, KafkaService kafkaService, Stage ownerStage) {
        this.settings = settings;
        this.kafkaService = kafkaService;

        dialogStage = new Stage();
        dialogStage.setTitle("Настройки подключения");
        dialogStage.initOwner(ownerStage);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setResizable(false);

        dialogStage.setScene(new Scene(buildContent(), 480, 300));
    }

    /**
     * Показывает диалог и ждёт закрытия.
     * @return true, если пользователь нажал OK и данные сохранены
     */
    public boolean showAndWait() {
        dialogStage.showAndWait();
        return confirmed;
    }

    private VBox buildContent() {
        // --- Bootstrap servers ---
        Label bootstrapTitle = new Label("Bootstrap Server(s)");
        bootstrapTitle.setFont(Font.font(null, FontWeight.BOLD, 13));

        Label bootstrapHint = new Label("Пример: localhost:9092  или  host1:9092,host2:9092");
        bootstrapHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #888888;");

        bootstrapField = new TextField(settings.getBootstrapServers());
        bootstrapField.setPromptText("host:port");
        bootstrapField.setPrefWidth(440);

        testButton = new Button("Проверить соединение");
        testResultLabel = new Label();
        testResultLabel.setStyle("-fx-font-size: 12px;");

        HBox testRow = new HBox(10, testButton, testResultLabel);
        testRow.setAlignment(Pos.CENTER_LEFT);
        testButton.setOnAction(e -> onTestConnection());

        // --- Max messages ---
        Label maxLabel = new Label("Максимум сообщений");
        maxLabel.setFont(Font.font(null, FontWeight.BOLD, 13));

        Label maxHint = new Label("Сколько последних сообщений загружать из топика (1–10 000)");
        maxHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #888888;");

        maxMessagesField = new TextField(String.valueOf(settings.getMaxMessages()));
        maxMessagesField.setPromptText("100");
        maxMessagesField.setPrefWidth(100);
        maxMessagesField.setMaxWidth(100);

        validationLabel = new Label();
        validationLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #cc0000;");

        okButton = new Button("OK");
        okButton.setDefaultButton(true);
        okButton.setPrefWidth(80);
        okButton.setOnAction(e -> onConfirm());

        Button cancelButton = new Button("Отмена");
        cancelButton.setPrefWidth(80);
        cancelButton.setOnAction(e -> dialogStage.close());

        HBox buttons = new HBox(10, cancelButton, okButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(8,
                bootstrapTitle,
                bootstrapHint,
                bootstrapField,
                testRow,
                new Separator(),
                maxLabel,
                maxHint,
                maxMessagesField,
                validationLabel,
                new Separator(),
                buttons
        );
        content.setPadding(new Insets(20));
        return content;
    }

    private void onTestConnection() {
        String value = bootstrapField.getText().trim();
        if (value.isEmpty()) {
            testResultLabel.setText("Введите адрес сервера");
            testResultLabel.setTextFill(Color.ORANGE);
            return;
        }

        testButton.setDisable(true);
        okButton.setDisable(true); // блокируем OK пока идёт тест, чтобы избежать race condition
        testResultLabel.setText("Проверка...");
        testResultLabel.setTextFill(Color.GRAY);

        // Временно применяем введённое значение для теста
        String previous = settings.getBootstrapServers();
        settings.setBootstrapServers(value);

        kafkaService.testConnection()
                .thenAcceptAsync(success -> {
                    if (success) {
                        testResultLabel.setText("Соединение успешно");
                        testResultLabel.setTextFill(Color.GREEN);
                    } else {
                        testResultLabel.setText("Соединение не установлено");
                        testResultLabel.setTextFill(Color.RED);
                    }
                    // Всегда откатываем — сохранение происходит только при нажатии OK
                    settings.setBootstrapServers(previous);
                    testButton.setDisable(false);
                    okButton.setDisable(false);
                }, Platform::runLater);
    }

    private void onConfirm() {
        String bootstrap = bootstrapField.getText().trim();
        if (bootstrap.isEmpty()) {
            validationLabel.setText("Адрес сервера не может быть пустым");
            return;
        }

        String maxStr = maxMessagesField.getText().trim();
        int maxMessages;
        try {
            maxMessages = Integer.parseInt(maxStr);
            if (maxMessages < 1 || maxMessages > 10_000) {
                validationLabel.setText("Максимум сообщений: от 1 до 10 000");
                return;
            }
        } catch (NumberFormatException e) {
            validationLabel.setText("Максимум сообщений: введите целое число");
            return;
        }

        settings.setBootstrapServers(bootstrap);
        settings.setMaxMessages(maxMessages);
        confirmed = true;
        dialogStage.close();
    }
}
