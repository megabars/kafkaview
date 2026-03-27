package com.mezentsev.kafkana.ui.dialog;

import com.mezentsev.kafkana.i18n.I18n;
import com.mezentsev.kafkana.model.AppSettings;
import com.mezentsev.kafkana.service.KafkaService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.text.MessageFormat;
import java.util.regex.Pattern;

/**
 * Единое окно настроек с тремя вкладками:
 * <ol>
 *   <li>Подключение — bootstrap servers и максимум сообщений</li>
 *   <li>Отображение — формат сообщений по умолчанию</li>
 *   <li>Интерфейс — язык приложения</li>
 * </ol>
 */
public class AppSettingsDialog {

    private final AppSettings settings;
    private final KafkaService kafkaService;
    private final Stage dialogStage;
    private final String originalLanguage;

    // Вкладка "Подключение"
    private TextField bootstrapField;
    private TextField maxMessagesField;
    private TextField fetchTimeoutField;
    private TextField adminTimeoutField;
    private Button testButton;
    private Button okButton;
    private Label testResultLabel;
    private Label validationLabel;

    // Вкладка "Отображение"
    private ComboBox<String> formatBox;

    // Вкладка "Интерфейс"
    private ComboBox<String> languageBox;

    private boolean confirmed = false;

    public AppSettingsDialog(AppSettings settings, KafkaService kafkaService, Stage ownerStage) {
        this.settings = settings;
        this.kafkaService = kafkaService;
        this.originalLanguage = settings.getLanguage();

        dialogStage = new Stage();
        dialogStage.setTitle(I18n.t("settings.title"));
        dialogStage.initOwner(ownerStage);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setResizable(false);

        Scene scene = new Scene(buildRoot(), 520, 560);
        java.net.URL cssUrl = getClass().getResource("/com/mezentsev/kafkana/app.css");
        if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
        dialogStage.setScene(scene);
    }

    /**
     * Показывает диалог и ждёт закрытия.
     * @return true, если пользователь нажал OK
     */
    public boolean showAndWait() {
        dialogStage.showAndWait();
        return confirmed;
    }

    /** Возвращает true, если пользователь изменил язык — нужен перезапуск. */
    public boolean isLanguageChanged() {
        return !originalLanguage.equals(settings.getLanguage());
    }

    // -----------------------------------------------------------------------

    private VBox buildRoot() {
        TabPane tabPane = new TabPane(
                buildConnectionTab(),
                buildDisplayTab(),
                buildInterfaceTab()
        );
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        okButton = new Button(I18n.t("settings.ok"));
        okButton.setDefaultButton(true);
        okButton.setPrefWidth(80);
        okButton.setOnAction(e -> onConfirm());

        Button cancelButton = new Button(I18n.t("settings.cancel"));
        cancelButton.setPrefWidth(80);
        cancelButton.setOnAction(e -> dialogStage.close());

        HBox buttons = new HBox(10, cancelButton, okButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPadding(new Insets(10, 16, 14, 16));

        VBox root = new VBox(tabPane, new Separator(), buttons);
        VBox.setVgrow(tabPane, javafx.scene.layout.Priority.ALWAYS);
        return root;
    }

    // -----------------------------------------------------------------------
    // Вкладка "Подключение"
    // -----------------------------------------------------------------------

    private Tab buildConnectionTab() {
        Label bootstrapTitle = new Label(I18n.t("settings.connection.bootstrap.title"));
        bootstrapTitle.setFont(Font.font(null, FontWeight.BOLD, 13));

        Label bootstrapHint = new Label(I18n.t("settings.connection.bootstrap.hint"));
        bootstrapHint.getStyleClass().add("hint-label");

        bootstrapField = new TextField(settings.getConnection().getBootstrapServers());
        bootstrapField.setPromptText(I18n.t("settings.connection.bootstrap.prompt"));
        bootstrapField.setPrefWidth(460);

        testButton = new Button(I18n.t("settings.connection.test"));
        testResultLabel = new Label();

        HBox testRow = new HBox(10, testButton, testResultLabel);
        testRow.setAlignment(Pos.CENTER_LEFT);
        testButton.setOnAction(e -> onTestConnection());

        Label maxLabel = new Label(I18n.t("settings.connection.max.title"));
        maxLabel.setFont(Font.font(null, FontWeight.BOLD, 13));

        Label maxHint = new Label(I18n.t("settings.connection.max.hint"));
        maxHint.getStyleClass().add("hint-label");

        maxMessagesField = new TextField(String.valueOf(settings.getConnection().getMaxMessages()));
        maxMessagesField.setPromptText(I18n.t("settings.connection.max.prompt"));
        maxMessagesField.setPrefWidth(100);
        maxMessagesField.setMaxWidth(100);

        Label timeoutsTitle = new Label(I18n.t("settings.connection.timeouts.title"));
        timeoutsTitle.setFont(Font.font(null, FontWeight.BOLD, 13));

        Label fetchLabel = new Label(I18n.t("settings.connection.fetch.timeout.label"));
        Label fetchHint  = new Label(I18n.t("settings.connection.fetch.timeout.hint"));
        fetchHint.getStyleClass().add("hint-label");
        fetchTimeoutField = new TextField(String.valueOf(settings.getConnection().getFetchTimeoutSec()));
        fetchTimeoutField.setPrefWidth(80);
        fetchTimeoutField.setMaxWidth(80);
        HBox fetchRow = new HBox(8, fetchLabel, fetchTimeoutField);
        fetchRow.setAlignment(Pos.CENTER_LEFT);

        Label adminLabel = new Label(I18n.t("settings.connection.admin.timeout.label"));
        Label adminHint  = new Label(I18n.t("settings.connection.admin.timeout.hint"));
        adminHint.getStyleClass().add("hint-label");
        adminTimeoutField = new TextField(String.valueOf(settings.getConnection().getAdminTimeoutSec()));
        adminTimeoutField.setPrefWidth(80);
        adminTimeoutField.setMaxWidth(80);
        HBox adminRow = new HBox(8, adminLabel, adminTimeoutField);
        adminRow.setAlignment(Pos.CENTER_LEFT);

        validationLabel = new Label();
        validationLabel.getStyleClass().add("status-label-error");

        VBox content = new VBox(8,
                bootstrapTitle, bootstrapHint, bootstrapField, testRow,
                new Separator(),
                maxLabel, maxHint, maxMessagesField,
                new Separator(),
                timeoutsTitle,
                fetchRow, fetchHint,
                adminRow, adminHint,
                validationLabel
        );
        content.setPadding(new Insets(16));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Tab tab = new Tab(I18n.t("settings.tab.connection"), scroll);
        return tab;
    }

    // -----------------------------------------------------------------------
    // Вкладка "Отображение"
    // -----------------------------------------------------------------------

    private Tab buildDisplayTab() {
        Label formatLabel = new Label(I18n.t("settings.display.format.label"));

        formatBox = new ComboBox<>();
        formatBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String key) {
                if (key == null) return "";
                return switch (key) {
                    case "JSON" -> "JSON";
                    case "XML"  -> "XML";
                    case "HEX"  -> I18n.t("detail.format.hex");
                    default     -> I18n.t("detail.format.text");
                };
            }
            @Override
            public String fromString(String s) { return s; }
        });
        formatBox.getItems().addAll("TEXT", "JSON", "XML", "HEX");
        formatBox.setValue(settings.getDefaultMessageFormat());
        formatBox.setPrefWidth(160);

        HBox row = new HBox(12, formatLabel, formatBox);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(12, row);
        content.setPadding(new Insets(16));

        return new Tab(I18n.t("settings.tab.display"), content);
    }

    // -----------------------------------------------------------------------
    // Вкладка "Интерфейс"
    // -----------------------------------------------------------------------

    private Tab buildInterfaceTab() {
        Label langLabel = new Label(I18n.t("settings.interface.language.label"));

        languageBox = new ComboBox<>();
        languageBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String lang) {
                if ("en".equals(lang)) return I18n.t("settings.interface.language.en");
                return I18n.t("settings.interface.language.ru");
            }
            @Override
            public String fromString(String s) { return s; }
        });
        languageBox.getItems().addAll("ru", "en");
        languageBox.setValue(settings.getLanguage());
        languageBox.setPrefWidth(160);

        HBox langRow = new HBox(12, langLabel, languageBox);
        langRow.setAlignment(Pos.CENTER_LEFT);

        Label restartNotice = new Label(I18n.t("settings.interface.restart.notice"));
        restartNotice.getStyleClass().add("hint-label");
        restartNotice.setWrapText(true);

        VBox content = new VBox(12, langRow, restartNotice);
        content.setPadding(new Insets(16));

        return new Tab(I18n.t("settings.tab.interface"), content);
    }

    // -----------------------------------------------------------------------
    // Логика подключения (перенесена из SettingsDialog)
    // -----------------------------------------------------------------------

    private static final Pattern BOOTSTRAP_PATTERN =
            Pattern.compile("^[^:,\\s]+:\\d+(,[^:,\\s]+:\\d+)*$");

    private static String validateBootstrap(String value) {
        if (value.isEmpty()) return I18n.t("settings.connection.error.bootstrap.empty");
        if (!BOOTSTRAP_PATTERN.matcher(value).matches())
            return I18n.t("settings.connection.error.bootstrap.format");
        for (String part : value.split(",")) {
            int colon = part.indexOf(':');
            if (colon >= 0) {
                try {
                    int port = Integer.parseInt(part.substring(colon + 1).trim());
                    if (port < 1 || port > 65535)
                        return MessageFormat.format(
                                I18n.t("settings.connection.error.port.range"), port);
                } catch (NumberFormatException e) {
                    return I18n.t("settings.connection.error.port.overflow");
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

        validationLabel.setText("");
        testButton.setDisable(true);
        okButton.setDisable(true);
        setTestResultStyle("test-result-normal");
        testResultLabel.setText(I18n.t("settings.connection.test.checking"));

        kafkaService.testConnection(value)
                .thenAcceptAsync(success -> {
                    if (success) {
                        setTestResultStyle("test-result-success");
                        testResultLabel.setText(I18n.t("settings.connection.test.success"));
                    } else {
                        setTestResultStyle("test-result-error");
                        testResultLabel.setText(I18n.t("settings.connection.test.fail"));
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
                validationLabel.setText(I18n.t("settings.connection.error.max.range"));
                return;
            }
        } catch (NumberFormatException e) {
            validationLabel.setText(I18n.t("settings.connection.error.max.format"));
            return;
        }

        int fetchTimeout;
        try {
            fetchTimeout = Integer.parseInt(fetchTimeoutField.getText().trim());
            if (fetchTimeout < 1 || fetchTimeout > 300) {
                validationLabel.setText(I18n.t("settings.connection.error.fetch.timeout"));
                return;
            }
        } catch (NumberFormatException e) {
            validationLabel.setText(I18n.t("settings.connection.error.fetch.timeout"));
            return;
        }

        int adminTimeout;
        try {
            adminTimeout = Integer.parseInt(adminTimeoutField.getText().trim());
            if (adminTimeout < 1 || adminTimeout > 60) {
                validationLabel.setText(I18n.t("settings.connection.error.admin.timeout"));
                return;
            }
        } catch (NumberFormatException e) {
            validationLabel.setText(I18n.t("settings.connection.error.admin.timeout"));
            return;
        }

        settings.getConnection().setBootstrapServers(bootstrap);
        settings.getConnection().setMaxMessages(maxMessages);
        settings.getConnection().setFetchTimeoutSec(fetchTimeout);
        settings.getConnection().setAdminTimeoutSec(adminTimeout);
        settings.setDefaultMessageFormat(formatBox.getValue());
        settings.setLanguage(languageBox.getValue());

        confirmed = true;
        dialogStage.close();
    }
}
