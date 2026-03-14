package com.mezentsev.kafkana.ui.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class AboutDialog {

    private static final String APP_NAME    = "Kafkana";
    private static final String APP_VERSION = "0.5.0";
    private static final String APP_DESC    = "Просмотр и отправка сообщений Apache Kafka";
    private static final String APP_AUTHOR  = "© 2026 Ivan Mezentsev";

    private final Stage dialogStage;

    public AboutDialog(Stage ownerStage) {
        dialogStage = new Stage();
        dialogStage.setTitle("О программе");
        if (ownerStage != null) {
            dialogStage.initOwner(ownerStage);
            dialogStage.initModality(Modality.WINDOW_MODAL);
        }
        dialogStage.setResizable(false);
        Scene aboutScene = new Scene(buildContent(), 320, 200);
        aboutScene.getStylesheets().add(getClass().getResource("/com/mezentsev/kafkana/app.css").toExternalForm());
        dialogStage.setScene(aboutScene);
    }

    public void showAndWait() {
        dialogStage.showAndWait();
    }

    private VBox buildContent() {
        Label nameLabel = new Label(APP_NAME);
        nameLabel.setFont(Font.font(null, FontWeight.BOLD, 20));

        Label versionLabel = new Label("Версия " + APP_VERSION);
        versionLabel.getStyleClass().add("about-version-label");

        Label descLabel = new Label(APP_DESC);
        descLabel.getStyleClass().add("about-desc-label");
        descLabel.setWrapText(true);

        Label authorLabel = new Label(APP_AUTHOR);
        authorLabel.getStyleClass().add("about-author-label");

        Button closeButton = new Button("Закрыть");
        closeButton.setDefaultButton(true);
        closeButton.setPrefWidth(90);
        closeButton.setOnAction(e -> dialogStage.close());

        VBox content = new VBox(10,
                nameLabel,
                versionLabel,
                new Separator(),
                descLabel,
                authorLabel,
                closeButton
        );
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(24));
        return content;
    }
}
