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
import java.util.function.IntConsumer;

/**
 * Сохраняет и загружает {@link AppSettings} в файл ~/.kafkana/settings.properties.
 */
public final class AppSettingsPersistence {

    private static final Logger log = LoggerFactory.getLogger(AppSettingsPersistence.class);

    private static final Path SETTINGS_PATH = Paths.get(
            System.getProperty("user.home"), ".kafkana", "settings.properties");

    private static final String KEY_BOOTSTRAP     = "bootstrap.servers";
    private static final String KEY_MAX           = "max.messages";
    private static final String KEY_FORMAT        = "default.format";
    private static final String KEY_LANGUAGE      = "language";
    private static final String KEY_FETCH_TIMEOUT = "fetch.timeout.sec";
    private static final String KEY_ADMIN_TIMEOUT = "admin.timeout.sec";

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
        parseAndSet(p, KEY_MAX,           ConnectionSettings.DEFAULT_MAX_MESSAGES,      c::setMaxMessages);
        parseAndSet(p, KEY_FETCH_TIMEOUT, ConnectionSettings.DEFAULT_FETCH_TIMEOUT_SEC, c::setFetchTimeoutSec);
        parseAndSet(p, KEY_ADMIN_TIMEOUT, ConnectionSettings.DEFAULT_ADMIN_TIMEOUT_SEC, c::setAdminTimeoutSec);
        s.setDefaultMessageFormat(p.getProperty(KEY_FORMAT, "TEXT"));
        // setLanguage применяет whitelist (только "ru" | "en"), невалидные значения → "ru"
        s.setLanguage(p.getProperty(KEY_LANGUAGE, "ru"));
    }

    /**
     * Сохраняет настройки в файл.
     *
     * @throws IOException если не удалось создать директорию или записать файл;
     *                     вызывающий код должен уведомить пользователя об ошибке.
     */
    public static void save(AppSettings s) throws IOException {
        Files.createDirectories(SETTINGS_PATH.getParent());
        Properties p = new Properties();
        ConnectionSettings c = s.getConnection();
        p.setProperty(KEY_BOOTSTRAP,     c.getBootstrapServers());
        p.setProperty(KEY_MAX,           String.valueOf(c.getMaxMessages()));
        p.setProperty(KEY_FETCH_TIMEOUT, String.valueOf(c.getFetchTimeoutSec()));
        p.setProperty(KEY_ADMIN_TIMEOUT, String.valueOf(c.getAdminTimeoutSec()));
        p.setProperty(KEY_FORMAT,        s.getDefaultMessageFormat());
        p.setProperty(KEY_LANGUAGE,      s.getLanguage());
        try (OutputStream out = Files.newOutputStream(SETTINGS_PATH)) {
            p.store(out, "Kafkana settings");
        }
    }

    /** Читает int-свойство из файла; при невалидном значении использует defaultVal. */
    private static void parseAndSet(Properties p, String key, int defaultVal, IntConsumer setter) {
        try {
            setter.accept(Integer.parseInt(p.getProperty(key, String.valueOf(defaultVal))));
        } catch (NumberFormatException ignored) {
            setter.accept(defaultVal);
        }
    }
}
