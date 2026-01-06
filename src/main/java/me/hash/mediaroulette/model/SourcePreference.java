package me.hash.mediaroulette.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores a user's preference values for a specific source.
 * Each source (reddit, roblox, etc.) can have its own SourcePreference instance.
 */
public class SourcePreference {
    private final String source;
    private final Map<String, Object> settings;
    
    public SourcePreference(String source) {
        this.source = source;
        this.settings = new HashMap<>();
    }
    
    public SourcePreference(String source, Map<String, Object> settings) {
        this.source = source;
        this.settings = new HashMap<>(settings);
    }
    
    /**
     * Get the source name this preference is for.
     */
    public String getSource() {
        return source;
    }
    
    /**
     * Get all settings as a map.
     */
    public Map<String, Object> getSettings() {
        return settings;
    }
    
    /**
     * Get a setting value, or return the default if not set.
     * @param key The setting key
     * @param defaultValue The default value if setting is not present
     * @return The setting value or default
     */
    @SuppressWarnings("unchecked")
    public <T> T getSetting(String key, T defaultValue) {
        Object value = settings.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return (T) value;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }
    
    /**
     * Get a string setting value.
     */
    public String getString(String key, String defaultValue) {
        Object value = settings.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }
    
    /**
     * Get an integer setting value.
     */
    public int getInt(String key, int defaultValue) {
        Object value = settings.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    /**
     * Get a boolean setting value.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = settings.get(key);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }
    
    /**
     * Set a setting value.
     * @param key The setting key
     * @param value The value to set
     */
    public void setSetting(String key, Object value) {
        if (value == null) {
            settings.remove(key);
        } else {
            settings.put(key, value);
        }
    }
    
    /**
     * Remove a setting, reverting to default.
     */
    public void removeSetting(String key) {
        settings.remove(key);
    }
    
    /**
     * Check if a specific setting has been set by the user.
     */
    public boolean hasSetting(String key) {
        return settings.containsKey(key);
    }
    
    /**
     * Clear all settings for this source.
     */
    public void clearSettings() {
        settings.clear();
    }
    
    /**
     * Convert to a map for MongoDB serialization.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("source", source);
        map.put("settings", new HashMap<>(settings));
        return map;
    }
    
    /**
     * Create from a MongoDB document map.
     */
    @SuppressWarnings("unchecked")
    public static SourcePreference fromMap(Map<String, Object> map) {
        String source = (String) map.get("source");
        Map<String, Object> settings = (Map<String, Object>) map.getOrDefault("settings", new HashMap<>());
        return new SourcePreference(source, settings);
    }
    
    @Override
    public String toString() {
        return "SourcePreference{source='" + source + "', settings=" + settings + "}";
    }
}
