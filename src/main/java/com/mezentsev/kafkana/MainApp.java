package com.mezentsev.kafkana;

import com.mezentsev.kafkana.model.ConnectionSettings;
import com.mezentsev.kafkana.model.SettingsPersistence;
import com.mezentsev.kafkana.service.KafkaService;
import com.mezentsev.kafkana.ui.MainWindow;
import javafx.application.Application;
import javafx.stage.Stage;

public class MainApp extends Application {

    private ConnectionSettings connectionSettings;
    private KafkaService kafkaService;

    @Override
    public void start(Stage primaryStage) {
        connectionSettings = new ConnectionSettings();
        SettingsPersistence.load(connectionSettings);
        kafkaService = new KafkaService(connectionSettings);

        MainWindow mainWindow = new MainWindow(connectionSettings, kafkaService);
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
