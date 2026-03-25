package com.mezentsev.kafkana.ui;

import com.mezentsev.kafkana.i18n.I18n;
import com.mezentsev.kafkana.service.KafkaService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TopicListPanel {

    private final KafkaService kafkaService;

    private final ListView<String> topicListView;
    private final Button iconRefreshBtn;
    private final Label countLabel;
    private final VBox root;

    private final List<String> allTopics = new ArrayList<>();

    private Consumer<String> onTopicSelected;

    public TopicListPanel(KafkaService kafkaService) {
        this.kafkaService = kafkaService;

        Label title = new Label(I18n.t("topic.panel.title"));
        title.setFont(Font.font(null, FontWeight.BOLD, 14));

        iconRefreshBtn = new Button("⟳");
        iconRefreshBtn.getStyleClass().add("reload-button");
        iconRefreshBtn.setTooltip(new Tooltip(I18n.t("topic.panel.refresh")));
        iconRefreshBtn.setOnAction(e -> loadTopics());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox headerRow = new HBox(0, title, spacer, iconRefreshBtn);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        topicListView = new ListView<>();
        topicListView.setMinWidth(180);
        VBox.setVgrow(topicListView, Priority.ALWAYS);

        TextField searchField = new TextField();
        searchField.setPromptText(I18n.t("topic.panel.search.prompt"));
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String filter = newVal == null ? "" : newVal.trim().toLowerCase();
            topicListView.getItems().setAll(
                    allTopics.stream()
                            .filter(t -> filter.isEmpty() || t.toLowerCase().contains(filter))
                            .toList()
            );
        });

        countLabel = new Label();
        countLabel.getStyleClass().add("topic-count-label");

        topicListView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldTopic, newTopic) -> {
                    if (newTopic != null && onTopicSelected != null) {
                        onTopicSelected.accept(newTopic);
                    }
                }
        );

        root = new VBox(8, headerRow, topicListView, searchField, countLabel);
        root.setPadding(new Insets(10, 8, 8, 8));
        root.setMinWidth(200);
        root.setMaxWidth(400);
        root.getStyleClass().add("topic-sidebar");
    }

    public Node getView() {
        return root;
    }

    public void setOnTopicSelected(Consumer<String> handler) {
        this.onTopicSelected = handler;
    }

    public void loadTopics() {
        countLabel.setText(I18n.t("topic.panel.loading"));
        iconRefreshBtn.setDisable(true);
        topicListView.getItems().clear();
        allTopics.clear();

        kafkaService.listTopics()
                .thenAcceptAsync(this::onTopicsLoaded, Platform::runLater)
                .exceptionallyAsync(ex -> { onTopicsError(ex); return null; }, Platform::runLater);
    }

    private void onTopicsLoaded(List<String> topics) {
        allTopics.clear();
        allTopics.addAll(topics);
        topicListView.getItems().setAll(topics);
        UiUtils.setStatusNormal(countLabel, topics.isEmpty()
                ? I18n.t("topic.panel.empty")
                : MessageFormat.format(I18n.t("topic.panel.count"), String.valueOf(topics.size())));
        iconRefreshBtn.setDisable(false);
    }

    private void onTopicsError(Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        UiUtils.setStatusError(countLabel, MessageFormat.format(I18n.t("topic.panel.error"), cause.getMessage()));
        iconRefreshBtn.setDisable(false);
    }
}
