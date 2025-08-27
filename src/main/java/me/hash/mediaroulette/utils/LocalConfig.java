package me.hash.mediaroulette.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class LocalConfig {
    private static final Logger logger = LoggerFactory.getLogger(LocalConfig.class);
    private static final String CONFIG_FILE = "config.json";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static LocalConfig instance;
    private Map<String, Object> config; // Fixed: Added generic types

    static {
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    private LocalConfig() {
        loadConfig();
    }

    public static LocalConfig getInstance() {
        if (instance == null) {
            instance = new LocalConfig();
        }
        return instance;
    }

    private void loadConfig() {
        File configFile = new File(CONFIG_FILE);

        if (configFile.exists()) {
            try {
                // Fixed: Added proper generic casting
                config = mapper.readValue(configFile,
                        mapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));
            } catch (IOException e) {
                logger.warn("Failed to load config file, creating new one: {}", e.getMessage());
                config = createDefaultConfig();
                saveConfig();
            }
        } else {
            config = createDefaultConfig();
            saveConfig();
        }
    }

    private Map<String, Object> createDefaultConfig() {
        Map<String, Object> defaultConfig = new HashMap<>();

        // Bot settings (non-sensitive only)
        defaultConfig.put("maintenance_mode", false);

        // Source toggles
        Map<String, Boolean> sources = new HashMap<>();
        sources.put("reddit", true);
        sources.put("imgur", true);
        sources.put("google", true);
        sources.put("picsum", true);
        sources.put("4chan", true);
        sources.put("rule34", true);
        sources.put("tenor", true);
        sources.put("tmdb_movie", true);
        sources.put("tmdb_tv", true);
        sources.put("youtube", true);
        sources.put("youtube_shorts", true);
        sources.put("urban_dictionary", true);
        defaultConfig.put("enabled_sources", sources);

        // Bot configuration
        Map<String, Object> botConfig = new HashMap<>();
        botConfig.put("max_image_size_mb", 8);
        botConfig.put("default_locale", "en_US");
        botConfig.put("cooldown_duration_ms", 2500);
        botConfig.put("max_favorites_per_user", 25);
        botConfig.put("max_inventory_size", 100);
        defaultConfig.put("bot_config", botConfig);

        return defaultConfig;
    }

    private void saveConfig() {
        try {
            mapper.writeValue(new File(CONFIG_FILE), config);
        } catch (IOException e) {
            logger.error("Failed to save config file: {}", e.getMessage());
        }
    }

    // Generic getters and setters
    public Object get(String key) {
        return config.get(key);
    }

    public void set(String key, Object value) {
        config.put(key, value);
        saveConfig();
    }

    // Specific getters for common values
    public boolean getMaintenanceMode() {
        return (Boolean) config.getOrDefault("maintenance_mode", false);
    }

    public void setMaintenanceMode(boolean enabled) {
        set("maintenance_mode", enabled);
    }


    @SuppressWarnings("unchecked")
    public Map<String, Boolean> getEnabledSources() {
        return (Map<String, Boolean>) config.getOrDefault("enabled_sources", new HashMap<>());
    }

    public boolean isSourceEnabled(String source) {
        Map<String, Boolean> sources = getEnabledSources();
        return sources.getOrDefault(source, true);
    }

    public void setSourceEnabled(String source, boolean enabled) {
        Map<String, Boolean> sources = getEnabledSources();
        sources.put(source, enabled);
        set("enabled_sources", sources);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getBotConfig() {
        return (Map<String, Object>) config.getOrDefault("bot_config", new HashMap<>());
    }

    public int getMaxImageSizeMb() {
        Map<String, Object> botConfig = getBotConfig();
        return (Integer) botConfig.getOrDefault("max_image_size_mb", 8);
    }

    public long getCooldownDuration() {
        Map<String, Object> botConfig = getBotConfig();
        return ((Number) botConfig.getOrDefault("cooldown_duration_ms", 2500)).longValue();
    }

    public String getDefaultLocale() {
        Map<String, Object> botConfig = getBotConfig();
        return (String) botConfig.getOrDefault("default_locale", "en_US");
    }

    // Reload config from file
    public void reload() {
        loadConfig();
    }

    // Get all config as string for display
    public String getConfigAsString() {
        try {
            return mapper.writeValueAsString(config);
        } catch (IOException e) {
            return "Error reading config: " + e.getMessage();
        }
    }
}