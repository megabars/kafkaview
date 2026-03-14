package com.mezentsev.kafkana.model;

public class AppSettings {

    private final ConnectionSettings connection = new ConnectionSettings();
    private volatile String defaultMessageFormat = "TEXT"; // TEXT | JSON | XML
    private volatile String language = "ru";               // ru | en

    public ConnectionSettings getConnection() {
        return connection;
    }

    public String getDefaultMessageFormat() {
        return defaultMessageFormat;
    }

    public void setDefaultMessageFormat(String fmt) {
        this.defaultMessageFormat = fmt != null ? fmt : "TEXT";
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String lang) {
        this.language = lang != null ? lang : "ru";
    }
}
