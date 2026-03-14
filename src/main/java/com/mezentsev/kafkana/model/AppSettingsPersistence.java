package com.mezentsev.kafkana.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Сохраняет и загружает {@link AppSettings} в файл ~/.kafkana/settings.properties.
 */
public final class AppSettingsPersistence {

    private static final Path SETTINGS_PATH = Paths.get(
            System.getProperty("user.home"), ".kafkana", "settings.properties");

    private static final String KEY_BOOTSTRAP = "bootstrap.servers";
    private static final String KEY_MAX       = "max.messages";
    private static final String KEY_FORMAT    = "default.format";
    private static final String KEY_LANGUAGE  = "language";

    private AppSettingsPersistence() {}

    public static void load(AppSettings s) {
        if (!Files.exists(SETTINGS_PATH)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(SETTINGS_PATH)) {
            p.load(in);
        } catch (IOException e) {
            return;
        }
        ConnectionSettings c = s.getConnection();
        c.setBootstrapServers(p.getProperty(KEY_BOOTSTRAP, ConnectionSettings.DEFAULT_BOOTSTRAP));
        try {
            c.setMaxMessages(Integer.parseInt(
                    p.getProperty(KEY_MAX, String.valueOf(ConnectionSettings.DEFAULT_MAX_MESSAGES))));
        } catch (NumberFormatException ignored) {}
        s.setDefaultMessageFormat(p.getProperty(KEY_FORMAT, "TEXT"));
        s.setLanguage(p.getProperty(KEY_LANGUAGE, "ru"));
    }

    public static void save(AppSettings s) {
        try {
            Files.createDirectories(SETTINGS_PATH.getParent());
        } catch (IOException ignored) {}
        Properties p = new Properties();
        ConnectionSettings c = s.getConnection();
        p.setProperty(KEY_BOOTSTRAP, c.getBootstrapServers());
        p.setProperty(KEY_MAX, String.valueOf(c.getMaxMessages()));
        p.setProperty(KEY_FORMAT, s.getDefaultMessageFormat());
        p.setProperty(KEY_LANGUAGE, s.getLanguage());
        try (OutputStream out = Files.newOutputStream(SETTINGS_PATH)) {
            p.store(out, "Kafkana settings");
        } catch (IOException ignored) {}
    }
}
