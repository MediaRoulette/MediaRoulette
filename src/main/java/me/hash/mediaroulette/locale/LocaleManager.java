package me.hash.mediaroulette.locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

public class LocaleManager {
    private static final Logger logger = LoggerFactory.getLogger(LocaleManager.class);
    private static final ConcurrentHashMap<String, LocaleManager> CACHE = new ConcurrentHashMap<>();
    private static final Path LOCALES_DIR = Path.of("resources", "locales");
    private static final LocaleManager DEFAULT_INSTANCE = new LocaleManager("en_US", true);

    private ResourceBundle bundle;
    private Locale locale;

    // Private constructor to prevent direct instantiation
    private LocaleManager(String localeName, boolean isDefault) {
        initializeLocale(localeName);
    }

    /**
     * Gets a cached LocaleManager instance for the specified locale.
     *
     * @param localeName the locale identifier (e.g., "en_US", "es_ES")
     * @return cached LocaleManager instance
     */
    public static LocaleManager getInstance(String localeName) {
        if (localeName == null || localeName.trim().isEmpty()) {
            return DEFAULT_INSTANCE;
        }

        return CACHE.computeIfAbsent(localeName, key -> new LocaleManager(key, false));
    }

    /**
     * Gets the default LocaleManager instance (en_US).
     *
     * @return default LocaleManager instance
     */
    public static LocaleManager getDefault() {
        return DEFAULT_INSTANCE;
    }

    /**
     * Alternative method that accepts language and country separately.
     *
     * @param language the language code (e.g., "en", "es")
     * @param country the country code (e.g., "US", "ES")
     * @return cached LocaleManager instance
     */
    public static LocaleManager getInstance(String language, String country) {
        return getInstance(language + "_" + country);
    }

    private void initializeLocale(String localeName) {
        // Parse locale string (e.g., "en_US" -> language="en", country="US")
        String[] parts = localeName.split("_");
        if (parts.length >= 2) {
            this.locale = Locale.of(parts[0], parts[1]);
        } else {
            this.locale = Locale.of(parts[0]);
        }

        // Load from external resources folder only
        Path externalFile = LOCALES_DIR.resolve("messages_" + localeName + ".properties");
        if (Files.exists(externalFile)) {
            try (InputStream is = Files.newInputStream(externalFile)) {
                this.bundle = new PropertyResourceBundle(is);
                logger.debug("Loaded locale {} from external resources: {}", localeName, externalFile.toAbsolutePath());
                return;
            } catch (Exception e) {
                logger.error("Failed to load external locale file {}: {}", externalFile, e.getMessage());
            }
        }

        // Try en_US as fallback if requested locale not found
        if (!localeName.equals("en_US")) {
            Path fallbackFile = LOCALES_DIR.resolve("messages_en_US.properties");
            if (Files.exists(fallbackFile)) {
                try (InputStream is = Files.newInputStream(fallbackFile)) {
                    this.bundle = new PropertyResourceBundle(is);
                    this.locale = Locale.of("en", "US");
                    logger.warn("Locale {} not found, loaded en_US fallback from: {}", localeName, fallbackFile.toAbsolutePath());
                    return;
                } catch (Exception e) {
                    logger.error("Failed to load fallback locale file {}: {}", fallbackFile, e.getMessage());
                }
            }
        }

        // If no external file found, create empty bundle and log error
        logger.error("No locale file found for {} at path: {}. Working directory: {}", 
                localeName, externalFile.toAbsolutePath(), System.getProperty("user.dir"));
        try {
            this.bundle = new PropertyResourceBundle(new java.io.StringReader(""));
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to create empty locale bundle", e);
        }
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
            logger.warn("Translation key not found: {}", key);
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
            logger.warn("Translation key not found: {}", key);
            return key;
        } catch (IllegalArgumentException e) {
            logger.error("Error formatting message for key: {} - {}", key, e.getMessage());
            try {
                return bundle.getString(key); // Return unformatted message
            } catch (MissingResourceException mre) {
                return key; // Return key if even unformatted message fails
            }
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

    /**
     * Clears the cache. Use with caution - mainly for testing or memory management.
     */
    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * Gets the current cache size (for monitoring purposes).
     *
     * @return number of cached LocaleManager instances
     */
    public static int getCacheSize() {
        return CACHE.size();
    }
}