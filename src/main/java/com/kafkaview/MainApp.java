package com.kafkaview;

import com.kafkaview.model.ConnectionSettings;
import com.kafkaview.model.SettingsPersistence;
import com.kafkaview.service.KafkaService;
import com.kafkaview.ui.MainWindow;
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
