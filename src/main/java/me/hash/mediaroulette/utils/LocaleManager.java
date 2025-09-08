package me.hash.mediaroulette.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class LocaleManager {
    private static final Logger logger = LoggerFactory.getLogger(LocaleManager.class);
    private ResourceBundle bundle;
    private Locale locale;

    /**
     * Creates a LocaleManager for the specified locale.
     * Falls back to default locale if the requested one is not found.
     *
     * @param localeName the locale identifier (e.g., "en_US", "es_ES")
     */
    public LocaleManager(String localeName) {
        try {
            // Parse locale string (e.g., "en_US" -> language="en", country="US")
            String[] parts = localeName.split("_");
            if (parts.length >= 2) {
                this.locale = new Locale(parts[0], parts[1]);
            } else {
                this.locale = new Locale(parts[0]);
            }

            // Try to load the requested locale
            this.bundle = ResourceBundle.getBundle("messages", this.locale);

        } catch (MissingResourceException e) {
            // Fallback to default locale (en_US)
            logger.error("Locale {} not found, falling back to en_US", localeName);
            this.locale = new Locale("en", "US");
            try {
                this.bundle = ResourceBundle.getBundle("messages", this.locale);
            } catch (MissingResourceException fallbackError) {
                // If even the fallback fails, try the root bundle
                this.bundle = ResourceBundle.getBundle("locales.messages");
            }
        }
    }

    /**
     * Alternative constructor that accepts language and country separately.
     *
     * @param language the language code (e.g., "en", "es")
     * @param country the country code (e.g., "US", "ES")
     */
    public LocaleManager(String language, String country) {
        this(language + "_" + country);
    }

    /**
     * Retrieves the translation for the given key.
     * If the key is not found, the key itself is returned.
     *
     * @param key the translation key
     * @return the corresponding translation or the key if not found
     */
    public String get(String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            logger.error("Translation key not found: {}", key);
            return key; // Return the key as fallback
        }
    }

    /**
     * Retrieves the translation for the given key with parameter substitution.
     * Uses MessageFormat for proper formatting and escaping.
     *
     * @param key the translation key
     * @param args the parameters to substitute in the message
     * @return the formatted translation or the key if not found
     */
    public String get(String key, Object... args) {
        try {
            String pattern = bundle.getString(key);
            if (args.length == 0) {
                return pattern;
            }
            return MessageFormat.format(pattern, args);
        } catch (MissingResourceException e) {
            logger.error("Translation key not found: {}", key);
            return key;
        } catch (IllegalArgumentException e) {
            logger.error("Error formatting message for key: {} - {}", key, e.getMessage());
            return bundle.getString(key); // Return unformatted message
        }
    }

    /**
     * Gets the current locale being used.
     *
     * @return the current Locale object
     */
    public Locale getLocale() {
        return locale;
    }

    /**
     * Checks if a translation key exists.
     *
     * @param key the translation key to check
     * @return true if the key exists, false otherwise
     */
    public boolean hasKey(String key) {
        try {
            bundle.getString(key);
            return true;
        } catch (MissingResourceException e) {
            return false;
        }
    }
}