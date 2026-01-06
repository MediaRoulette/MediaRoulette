package me.hash.mediaroulette.model;

import java.util.List;
import java.util.Collections;

/**
 * Defines the schema for a single configurable preference setting.
 * Providers use this to declare what options users can customize.
 */
public record PreferenceSetting(
    String key,              // Unique identifier, e.g., "sortMethod"
    String displayName,      // Human-readable name, e.g., "Sort Method"
    String description,      // Help text, e.g., "How posts are sorted"
    PreferenceType type,     // The data type of this setting
    Object defaultValue,     // Default value when not set by user
    List<String> choices     // For CHOICE type: allowed values
) {
    
    /**
     * Creates a STRING type preference with no predefined choices.
     */
    public static PreferenceSetting string(String key, String displayName, String description, String defaultValue) {
        return new PreferenceSetting(key, displayName, description, PreferenceType.STRING, defaultValue, Collections.emptyList());
    }
    
    /**
     * Creates an INTEGER type preference.
     */
    public static PreferenceSetting integer(String key, String displayName, String description, int defaultValue) {
        return new PreferenceSetting(key, displayName, description, PreferenceType.INTEGER, defaultValue, Collections.emptyList());
    }
    
    /**
     * Creates a BOOLEAN type preference (toggle on/off).
     */
    public static PreferenceSetting bool(String key, String displayName, String description, boolean defaultValue) {
        return new PreferenceSetting(key, displayName, description, PreferenceType.BOOLEAN, defaultValue, Collections.emptyList());
    }
    
    /**
     * Creates a CHOICE type preference where user must select from predefined options.
     * Example: sortMethod with choices ["hot", "top", "new", "rising"]
     */
    public static PreferenceSetting choice(String key, String displayName, String description, String defaultValue, List<String> choices) {
        if (choices == null || choices.isEmpty()) {
            throw new IllegalArgumentException("CHOICE type requires at least one choice option");
        }
        if (!choices.contains(defaultValue)) {
            throw new IllegalArgumentException("Default value must be one of the choices");
        }
        return new PreferenceSetting(key, displayName, description, PreferenceType.CHOICE, defaultValue, List.copyOf(choices));
    }
    
    /**
     * Creates a CHOICE type preference from varargs for convenience.
     */
    public static PreferenceSetting choice(String key, String displayName, String description, String defaultValue, String... choices) {
        return choice(key, displayName, description, defaultValue, List.of(choices));
    }
    
    /**
     * Validates that a value is acceptable for this preference setting.
     * @param value The value to validate
     * @return true if the value is valid for this preference type
     */
    public boolean isValidValue(Object value) {
        if (value == null) {
            return true; // null means use default
        }
        
        return switch (type) {
            case STRING -> value instanceof String;
            case INTEGER -> value instanceof Integer || value instanceof Long;
            case BOOLEAN -> value instanceof Boolean;
            case CHOICE -> value instanceof String && choices.contains((String) value);
        };
    }
    
    /**
     * The types of preference settings supported.
     */
    public enum PreferenceType {
        STRING,   // Free-form text input
        INTEGER,  // Numeric value
        BOOLEAN,  // True/false toggle
        CHOICE    // Selection from predefined list
    }
}
