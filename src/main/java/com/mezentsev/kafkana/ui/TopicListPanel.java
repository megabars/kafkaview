package com.mezentsev.kafkana.ui;

import com.mezentsev.kafkana.i18n.I18n;
import com.mezentsev.kafkana.service.KafkaService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.text.MessageFormat;
import java.util.List;
import java.util.function.Consumer;

public class TopicListPanel {


    private final KafkaService kafkaService;

    private final ListView<String> topicListView;
    private final Button refreshButton;
    private final Label statusLabel;
    private final VBox root;

    private Consumer<String> onTopicSelected;

    public TopicListPanel(KafkaService kafkaService) {
        this.kafkaService = kafkaService;

        Label title = new Label(I18n.t("topic.panel.title"));
        title.setFont(Font.font(null, FontWeight.BOLD, 14));

        topicListView = new ListView<>();
        topicListView.setMinWidth(180);
        VBox.setVgrow(topicListView, Priority.ALWAYS);

        statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.getStyleClass().add("status-label");

        refreshButton = new Button(I18n.t("topic.panel.refresh"));
        refreshButton.setMaxWidth(Double.MAX_VALUE);
        refreshButton.setOnAction(e -> loadTopics());

        topicListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldTopic, newTopic) -> {
                    if (newTopic != null && onTopicSelected != null) {
                        onTopicSelected.accept(newTopic);
                    }
                }
        );

        root = new VBox(8, title, topicListView, statusLabel, refreshButton);
        root.setPadding(new Insets(10));
        root.setMinWidth(200);
        root.setMaxWidth(400);
    }

    public Node getView() {
        return root;
    }

    public void setOnTopicSelected(Consumer<String> handler) {
        this.onTopicSelected = handler;
    }

    public void loadTopics() {
        statusLabel.setText(I18n.t("topic.panel.loading"));
        refreshButton.setDisable(true);
        topicListView.getItems().clear();

        kafkaService.listTopics()
                .thenAcceptAsync(this::onTopicsLoaded, Platform::runLater)
                .exceptionallyAsync(ex -> { onTopicsError(ex); return null; }, Platform::runLater);
    }

    private void onTopicsLoaded(List<String> topics) {
        topicListView.getItems().setAll(topics);
        setStatusNormal(topics.isEmpty()
                ? I18n.t("topic.panel.empty")
                : MessageFormat.format(I18n.t("topic.panel.count"), String.valueOf(topics.size())));
        refreshButton.setDisable(false);
    }

    private void onTopicsError(Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        setStatusError(MessageFormat.format(I18n.t("topic.panel.error"), cause.getMessage()));
        refreshButton.setDisable(false);
    }

    private void setStatusNormal(String text) {
        UiUtils.setStatusNormal(statusLabel, text);
    }

    private void setStatusError(String text) {
        UiUtils.setStatusError(statusLabel, text);
    }
}
