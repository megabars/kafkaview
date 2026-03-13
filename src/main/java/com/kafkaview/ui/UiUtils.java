package com.kafkaview.ui;

import javafx.scene.control.Label;

/** Вспомогательные методы для UI-компонентов. */
public final class UiUtils {

    private UiUtils() {}

    /**
     * Переводит метку статуса в нормальное состояние (синий/серый текст).
     * Удаляет оба класса перед добавлением нужного — избегает дублирования.
     */
    public static void setStatusNormal(Label label, String text) {
        label.getStyleClass().removeAll("status-label", "status-label-error");
        label.getStyleClass().add("status-label");
        label.setText(text);
    }

    /**
     * Переводит метку статуса в состояние ошибки (красный текст).
     */
    public static void setStatusError(Label label, String text) {
        label.getStyleClass().removeAll("status-label", "status-label-error");
        label.getStyleClass().add("status-label-error");
        label.setText(text);
    }
}
