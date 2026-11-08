package com.github.gcolin.platform;

import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Function;

/** Looks up i18n messages from ResourceBundles for Mustache templates. */
public class Messages {

    private final Locale locale;

    public Messages(Locale locale) {
        this.locale = locale;
    }

    public Locale getLocale() {
        return locale;
    }

    public String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return formatDate(dateTime.toLocalDate());
    }

    public String formatDate(LocalDate date) {
        if (date == null) {
            return "";
        }
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date);
    }

    public String formatDate(Date date) {
        if (date == null) {
            return "";
        }
        return formatDate(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
    }

    public String get(String key) {
        return get(key, new Object[0]);
    }

    public String get(String key, Object... params) {
        if (key == null) {
            return "";
        }
        ResourceBundle bundle = ResourceBundle.getBundle("messages", locale);
        if (!bundle.containsKey(key)) {
            return key;
        }
        String message = bundle.getString(key);
        if (params == null || params.length == 0) {
            return message;
        }
        return new MessageFormat(message, locale).format(params);
    }

    /**
     * Mustache function (plain {@link Function}, not {@code TemplateFunction}): section body is
     * rendered first so nested keys work, e.g. {@code {{#i18n}}{{statusMessageKey}}{{/i18n}}}.
     */
    public Function<String, String> asLambda() {
        return key -> get(key == null ? "" : key.trim());
    }
}
