package com.kafkaview.model;

import java.util.prefs.Preferences;

/**
 * Сохраняет и загружает {@link ConnectionSettings} через {@link Preferences}
 * (стандартный Java API; на macOS хранится в ~/Library/Preferences,
 * на Windows — в реестре, на Linux — в ~/.java/.userPrefs).
 */
public final class SettingsPersistence {

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(SettingsPersistence.class);

    private static final String KEY_BOOTSTRAP    = "bootstrap.servers";
    private static final String KEY_MAX_MESSAGES = "max.messages";

    private SettingsPersistence() {}

    /** Загружает ранее сохранённые настройки; если их нет — оставляет значения по умолчанию. */
    public static void load(ConnectionSettings settings) {
        settings.setBootstrapServers(
                PREFS.get(KEY_BOOTSTRAP, ConnectionSettings.DEFAULT_BOOTSTRAP));
        settings.setMaxMessages(
                PREFS.getInt(KEY_MAX_MESSAGES, ConnectionSettings.DEFAULT_MAX_MESSAGES));
    }

    /** Сохраняет текущие настройки в постоянное хранилище. */
    public static void save(ConnectionSettings settings) {
        PREFS.put(KEY_BOOTSTRAP, settings.getBootstrapServers());
        PREFS.putInt(KEY_MAX_MESSAGES, settings.getMaxMessages());
    }
}
