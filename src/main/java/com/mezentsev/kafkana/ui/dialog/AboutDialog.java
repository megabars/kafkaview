package com.mezentsev.kafkana.ui.dialog;

import com.mezentsev.kafkana.i18n.I18n;
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

import java.text.MessageFormat;

public class AboutDialog {

    private static final String APP_NAME    = "Kafkana";
    private static final String APP_VERSION = "0.6.0";
    private static final String APP_AUTHOR  = "© 2026 Ivan Mezentsev";

    private final Stage dialogStage;

    public AboutDialog(Stage ownerStage) {
        dialogStage = new Stage();
        dialogStage.setTitle(I18n.t("about.title"));
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

        Label versionLabel = new Label(MessageFormat.format(I18n.t("about.version"), APP_VERSION));
        versionLabel.getStyleClass().add("about-version-label");

        Label descLabel = new Label(I18n.t("app.desc"));
        descLabel.getStyleClass().add("about-desc-label");
        descLabel.setWrapText(true);

        Label authorLabel = new Label(APP_AUTHOR);
        authorLabel.getStyleClass().add("about-author-label");

        Button closeButton = new Button(I18n.t("about.close"));
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
