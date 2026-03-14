package com.mezentsev.kafkana;

import com.mezentsev.kafkana.i18n.I18n;
import com.mezentsev.kafkana.model.AppSettings;
import com.mezentsev.kafkana.model.AppSettingsPersistence;
import com.mezentsev.kafkana.service.KafkaService;
import com.mezentsev.kafkana.ui.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;

public class MainApp extends Application {

    private AppSettings appSettings;
    private KafkaService kafkaService;

    @Override
    public void start(Stage primaryStage) {
        appSettings = new AppSettings();
        AppSettingsPersistence.load(appSettings);
        I18n.init(appSettings.getLanguage());

        kafkaService = new KafkaService(appSettings.getConnection());

        MainWindow mainWindow = new MainWindow(appSettings, kafkaService);
        mainWindow.show(primaryStage);
    }

    @Override
    public void stop() {
        if (kafkaService != null) {
            kafkaService.shutdown();
        }
    }

    // Нужен для запуска из fat JAR вне module path
    public static void main(String[] args) {
        Application.launch(MainApp.class, args);
    }
}
