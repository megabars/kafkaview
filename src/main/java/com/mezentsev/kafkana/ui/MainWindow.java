package com.mezentsev.kafkana.ui;

import com.mezentsev.kafkana.i18n.I18n;
import com.mezentsev.kafkana.model.AppSettings;
import com.mezentsev.kafkana.model.AppSettingsPersistence;
import com.mezentsev.kafkana.service.KafkaService;
import com.mezentsev.kafkana.ui.dialog.AboutDialog;
import com.mezentsev.kafkana.ui.dialog.AppSettingsDialog;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class MainWindow {

    private final AppSettings settings;
    private final KafkaService kafkaService;

    private TopicListPanel topicListPanel;
    private MessageTablePanel messageTablePanel;

    public MainWindow(AppSettings settings, KafkaService kafkaService) {
        this.settings = settings;
        this.kafkaService = kafkaService;
    }

    public void show(Stage primaryStage) {
        topicListPanel = new TopicListPanel(kafkaService);
        messageTablePanel = new MessageTablePanel(kafkaService, settings);
        messageTablePanel.setOwnerStage(primaryStage);

        // Связываем выбор топика с загрузкой сообщений
        topicListPanel.setOnTopicSelected(messageTablePanel::loadMessages);

        // Загружаем топики после того как все компоненты собраны
        topicListPanel.loadTopics();

        // Разделитель: 30% — список топиков, 70% — таблица сообщений
        SplitPane splitPane = new SplitPane(
                topicListPanel.getView(),
                messageTablePanel.getView()
        );
        splitPane.setDividerPositions(0.28);
        SplitPane.setResizableWithParent(topicListPanel.getView(), false);

        MenuBar menuBar = buildMenuBar(primaryStage);

        VBox root = new VBox(menuBar, splitPane);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        Scene scene = new Scene(root, 1100, 700);
        scene.getStylesheets().add(getClass().getResource("/com/mezentsev/kafkana/app.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setTitle(I18n.t("app.title"));
        primaryStage.setMinWidth(750);
        primaryStage.setMinHeight(450);

        primaryStage.show();
    }

    private MenuBar buildMenuBar(Stage ownerStage) {
        // --- Меню "Файл" ---
        MenuItem exitItem = new MenuItem(I18n.t("menu.file.exit"));
        exitItem.setOnAction(e -> Platform.exit());

        Menu fileMenu = new Menu(I18n.t("menu.file"));
        fileMenu.getItems().add(exitItem);

        // --- Меню "Настройки" ---
        MenuItem configureItem = new MenuItem(I18n.t("menu.settings.open"));
        configureItem.setOnAction(e -> openSettingsDialog(ownerStage));

        Menu settingsMenu = new Menu(I18n.t("menu.settings"));
        settingsMenu.getItems().add(configureItem);

        // --- Меню "Справка" ---
        MenuItem aboutItem = new MenuItem(I18n.t("menu.help.about"));
        aboutItem.setOnAction(e -> new AboutDialog(ownerStage).showAndWait());

        Menu helpMenu = new Menu(I18n.t("menu.help"));
        helpMenu.getItems().add(aboutItem);

        MenuBar menuBar = new MenuBar(fileMenu, settingsMenu, helpMenu);
        menuBar.setUseSystemMenuBar(true);
        return menuBar;
    }

    private void openSettingsDialog(Stage ownerStage) {
        AppSettingsDialog dialog = new AppSettingsDialog(settings, kafkaService, ownerStage);
        boolean confirmed = dialog.showAndWait();
        if (confirmed) {
            AppSettingsPersistence.save(settings);
            topicListPanel.loadTopics();
            if (dialog.isLanguageChanged()) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle(I18n.t("settings.title"));
                alert.setHeaderText(null);
                alert.setContentText(I18n.t("settings.interface.restart.notice"));
                alert.initOwner(ownerStage);
                alert.showAndWait();
            }
        }
    }

}
