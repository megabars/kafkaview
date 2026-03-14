package com.mezentsev.kafkana.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(AppSettingsPersistence.class);

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
            log.warn("Не удалось загрузить настройки из {}: {}", SETTINGS_PATH, e.getMessage());
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
        } catch (IOException e) {
            log.warn("Не удалось создать директорию для настроек {}: {}", SETTINGS_PATH.getParent(), e.getMessage());
        }
        Properties p = new Properties();
        ConnectionSettings c = s.getConnection();
        p.setProperty(KEY_BOOTSTRAP, c.getBootstrapServers());
        p.setProperty(KEY_MAX, String.valueOf(c.getMaxMessages()));
        p.setProperty(KEY_FORMAT, s.getDefaultMessageFormat());
        p.setProperty(KEY_LANGUAGE, s.getLanguage());
        try (OutputStream out = Files.newOutputStream(SETTINGS_PATH)) {
            p.store(out, "Kafkana settings");
        } catch (IOException e) {
            log.warn("Не удалось сохранить настройки в {}: {}", SETTINGS_PATH, e.getMessage());
        }
    }
}
