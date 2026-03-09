package com.kafkaview.ui.dialog;

import com.kafkaview.model.KafkaMessage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.io.StringWriter;

public class MessageDetailDialog {

    private static final String FORMAT_TEXT = "Текст";
    private static final String FORMAT_JSON = "JSON";
    private static final String FORMAT_XML  = "XML";

    private final KafkaMessage message;
    private Stage dialogStage;

    private TextArea textArea;
    private Label errorLabel;

    public MessageDetailDialog(KafkaMessage message, Stage ownerStage) {
        this.message = message;

        dialogStage = new Stage();
        dialogStage.setTitle("Детали сообщения");
        if (ownerStage != null) {
            dialogStage.initOwner(ownerStage);
            dialogStage.initModality(Modality.WINDOW_MODAL);
        }

        Scene scene = new Scene(buildContent(), 680, 520);
        dialogStage.setScene(scene);
        dialogStage.setMinWidth(500);
        dialogStage.setMinHeight(380);
    }

    public void show() {
        dialogStage.show();
    }

    private VBox buildContent() {
        // --- Метаданные ---
        GridPane meta = new GridPane();
        meta.setHgap(12);
        meta.setVgap(6);

        ColumnConstraints labelCol = new ColumnConstraints(100);
        ColumnConstraints valueCol = new ColumnConstraints();
        valueCol.setHgrow(Priority.ALWAYS);
        meta.getColumnConstraints().addAll(labelCol, valueCol);

        meta.add(bold("Партиция:"),   0, 0);
        meta.add(value(String.valueOf(message.getPartition())), 1, 0);
        meta.add(bold("Дата/время:"), 0, 1);
        meta.add(value(message.getFormattedTimestamp()), 1, 1);

        // --- Заголовок + выбор формата ---
        Label contentLabel = new Label("Содержимое сообщения:");
        contentLabel.setFont(Font.font(null, FontWeight.BOLD, 13));
        HBox.setHgrow(contentLabel, Priority.ALWAYS);

        Label formatLabel = new Label("Формат:");
        formatLabel.setStyle("-fx-font-size: 12px;");

        ComboBox<String> formatBox = new ComboBox<>();
        formatBox.getItems().addAll(FORMAT_TEXT, FORMAT_JSON, FORMAT_XML);
        formatBox.setValue(FORMAT_TEXT);
        formatBox.setPrefWidth(100);

        HBox toolbar = new HBox(10, contentLabel, formatLabel, formatBox);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        // --- Текстовая область ---
        textArea = new TextArea(message.getValue());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 12px;");
        VBox.setVgrow(textArea, Priority.ALWAYS);

        // --- Метка ошибки форматирования ---
        errorLabel = new Label();
        errorLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #cc0000;");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        // --- Смена формата при выборе в списке ---
        formatBox.valueProperty().addListener((obs, oldVal, newVal) -> applyFormat(newVal));

        // --- Кнопка закрытия ---
        Button closeButton = new Button("Закрыть");
        closeButton.setDefaultButton(true);
        closeButton.setPrefWidth(90);
        closeButton.setOnAction(e -> dialogStage.close());

        HBox buttons = new HBox(closeButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox content = new VBox(10,
                meta,
                new Separator(),
                toolbar,
                textArea,
                errorLabel,
                buttons
        );
        content.setPadding(new Insets(15));
        return content;
    }

    private void applyFormat(String format) {
        String raw = message.getValue();
        switch (format) {
            case FORMAT_JSON -> {
                String formatted = formatJson(raw);
                if (formatted != null) {
                    textArea.setText(formatted);
                    textArea.setWrapText(false);
                    showError(null);
                } else {
                    textArea.setText(raw);
                    textArea.setWrapText(true);
                    showError("Невалидный JSON — показан исходный текст");
                }
            }
            case FORMAT_XML -> {
                String formatted = formatXml(raw);
                if (formatted != null) {
                    textArea.setText(formatted);
                    textArea.setWrapText(false);
                    showError(null);
                } else {
                    textArea.setText(raw);
                    textArea.setWrapText(true);
                    showError("Невалидный XML — показан исходный текст");
                }
            }
            default -> {
                textArea.setText(raw);
                textArea.setWrapText(true);
                showError(null);
            }
        }
    }

    private void showError(String msg) {
        if (msg == null || msg.isBlank()) {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        } else {
            errorLabel.setText(msg);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
    }

    // -----------------------------------------------------------------------
    // JSON pretty-printer (без внешних зависимостей)
    // -----------------------------------------------------------------------

    private String formatJson(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        try {
            String trimmed = raw.strip();
            if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null;
            return prettyPrintJson(trimmed);
        } catch (Exception e) {
            return null;
        }
    }

    private String prettyPrintJson(String json) {
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                sb.append(c);
                escaped = false;
                continue;
            }

            if (inString) {
                sb.append(c);
                if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }

            switch (c) {
                case '"' -> { inString = true; sb.append(c); }
                case '{', '[' -> {
                    sb.append(c);
                    int next = nextNonWhitespace(json, i + 1);
                    if (next != -1 && json.charAt(next) != '}' && json.charAt(next) != ']') {
                        indent++;
                        sb.append('\n').append(indent(indent));
                    }
                }
                case '}', ']' -> {
                    int prev = prevNonWhitespace(json, i - 1);
                    if (prev != -1 && json.charAt(prev) != '{' && json.charAt(prev) != '[') {
                        indent--;
                        sb.append('\n').append(indent(indent));
                    }
                    sb.append(c);
                }
                case ',' -> sb.append(c).append('\n').append(indent(indent));
                case ':' -> sb.append(": ");
                case ' ', '\t', '\n', '\r' -> { /* пропускаем пробелы вне строк */ }
                default -> sb.append(c);
            }
        }

        if (inString) throw new IllegalArgumentException("Незакрытая строка");
        return sb.toString();
    }

    private int nextNonWhitespace(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) return i;
        }
        return -1;
    }

    private int prevNonWhitespace(String s, int from) {
        for (int i = from; i >= 0; i--) {
            if (!Character.isWhitespace(s.charAt(i))) return i;
        }
        return -1;
    }

    private String indent(int level) {
        return "    ".repeat(Math.max(0, level));
    }

    // -----------------------------------------------------------------------
    // XML formatter (стандартный Java XML API)
    // -----------------------------------------------------------------------

    private String formatXml(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        try {
            Source xmlInput = new StreamSource(new StringReader(raw.strip()));
            StringWriter output = new StringWriter();
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setAttribute("indent-number", 4);
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(xmlInput, new StreamResult(output));
            return output.toString().strip();
        } catch (Exception e) {
            return null;
        }
    }

    // -----------------------------------------------------------------------

    private Label bold(String text) {
        Label lbl = new Label(text);
        lbl.setFont(Font.font(null, FontWeight.BOLD, 12));
        return lbl;
    }

    private Label value(String text) {
        return new Label(text);
    }
}
