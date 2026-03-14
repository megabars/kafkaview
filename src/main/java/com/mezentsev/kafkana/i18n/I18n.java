package com.mezentsev.kafkana.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * Утилита локализации. Перед созданием любого UI необходимо вызвать {@link #init(String)}.
 */
public final class I18n {

    private static ResourceBundle bundle;

    private I18n() {}

    /**
     * Инициализирует ResourceBundle для указанного языка.
     * Вызывается один раз при старте приложения.
     *
     * @param language "ru" или "en"
     */
    public static void init(String language) {
        Locale locale = "en".equals(language) ? Locale.ENGLISH : Locale.forLanguageTag("ru");
        bundle = ResourceBundle.getBundle(
                "com.mezentsev.kafkana.i18n.messages", locale, new Utf8Control());
    }

    /**
     * Возвращает локализованную строку по ключу.
     * Если ключ не найден — возвращает {@code !key!} для быстрой отладки.
     */
    public static String t(String key) {
        if (bundle == null) return "!" + key + "!";
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }

    /**
     * ResourceBundle.Control с поддержкой UTF-8 для корректного чтения кириллицы.
     */
    private static final class Utf8Control extends ResourceBundle.Control {
        @Override
        public ResourceBundle newBundle(String baseName, Locale locale,
                String format, ClassLoader loader, boolean reload)
                throws IOException {
            String bundleName  = toBundleName(baseName, locale);
            String resourceName = toResourceName(bundleName, "properties");
            InputStream stream = loader.getResourceAsStream(resourceName);
            if (stream == null) return null;
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return new PropertyResourceBundle(reader);
            }
        }
    }
}
