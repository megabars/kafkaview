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
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class SettingsDialog {


    private final ConnectionSettings settings;
    private final KafkaService kafkaService;

    private final Stage dialogStage;
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
        bootstrapHint.getStyleClass().add("hint-label");

        bootstrapField = new TextField(settings.getBootstrapServers());
        bootstrapField.setPromptText("host:port");
        bootstrapField.setPrefWidth(440);

        testButton = new Button("Проверить соединение");
        testResultLabel = new Label();

        HBox testRow = new HBox(10, testButton, testResultLabel);
        testRow.setAlignment(Pos.CENTER_LEFT);
        testButton.setOnAction(e -> onTestConnection());

        // --- Max messages ---
        Label maxLabel = new Label("Максимум сообщений");
        maxLabel.setFont(Font.font(null, FontWeight.BOLD, 13));

        Label maxHint = new Label("Сколько последних сообщений загружать из топика (1–10 000)");
        maxHint.getStyleClass().add("hint-label");

        maxMessagesField = new TextField(String.valueOf(settings.getMaxMessages()));
        maxMessagesField.setPromptText("100");
        maxMessagesField.setPrefWidth(100);
        maxMessagesField.setMaxWidth(100);

        validationLabel = new Label();
        validationLabel.getStyleClass().add("status-label-error");

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

    // Паттерн: один или несколько адресов host:port через запятую; порт обязателен
    private static final java.util.regex.Pattern BOOTSTRAP_PATTERN =
            java.util.regex.Pattern.compile("^[^:,\\s]+:\\d+(,[^:,\\s]+:\\d+)*$");

    /**
     * Проверяет формат bootstrap-адреса и диапазон порта (1–65535).
     * Возвращает null при успехе или текст ошибки.
     */
    private static String validateBootstrap(String value) {
        if (value.isEmpty()) return "Адрес сервера не может быть пустым";
        if (!BOOTSTRAP_PATTERN.matcher(value).matches()) return "Неверный формат — ожидается host:port";
        for (String part : value.split(",")) {
            int colon = part.indexOf(':');
            if (colon >= 0) {
                try {
                    int port = Integer.parseInt(part.substring(colon + 1).trim());
                    if (port < 1 || port > 65535) return "Недопустимый порт: " + port + " (допустимо 1–65535)";
                } catch (NumberFormatException e) {
                    return "Недопустимый порт (слишком большое число)";
                }
            }
        }
        return null;
    }

    private void setTestResultStyle(String styleClass) {
        testResultLabel.getStyleClass().removeAll(
                "test-result-normal", "test-result-warning", "test-result-success", "test-result-error");
        testResultLabel.getStyleClass().add(styleClass);
    }

    private void onTestConnection() {
        String value = bootstrapField.getText().trim();
        String error = validateBootstrap(value);
        if (error != null) {
            setTestResultStyle("test-result-warning");
            testResultLabel.setText(error);
            return;
        }

        validationLabel.setText(""); // очищаем ошибку валидации, если была
        testButton.setDisable(true);
        okButton.setDisable(true);
        setTestResultStyle("test-result-normal");
        testResultLabel.setText("Проверка...");

        // Передаём адрес напрямую — settings не трогаем до нажатия OK
        kafkaService.testConnection(value)
                .thenAcceptAsync(success -> {
                    if (success) {
                        setTestResultStyle("test-result-success");
                        testResultLabel.setText("Соединение успешно");
                    } else {
                        setTestResultStyle("test-result-error");
                        testResultLabel.setText("Соединение не установлено");
                    }
                    testButton.setDisable(false);
                    okButton.setDisable(false);
                }, Platform::runLater);
    }

    private void onConfirm() {
        String bootstrap = bootstrapField.getText().trim();
        String bootstrapError = validateBootstrap(bootstrap);
        if (bootstrapError != null) {
            validationLabel.setText(bootstrapError);
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
