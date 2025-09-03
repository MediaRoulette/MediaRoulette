package me.hash.mediaroulette.utils;

import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.InputStream;

public class Locale {
    private JSONObject translations;

    /**
     * Loads the JSON locale file from resources/locales.
     *
     * @param localeName the locale identifier (e.g., "en_US")
     */
    public Locale(String localeName) {
        String path = "/locales/" + localeName + ".json";
        InputStream in = getClass().getResourceAsStream(path);
        if (in == null) {
            // Fallback to en_US if requested locale not found
            path = "/locales/en_US.json";
            in = getClass().getResourceAsStream(path);
            if (in == null) {
                throw new RuntimeException("Default locale file not found: " + path);
            }
        }
        
        try {
            translations = new JSONObject(new JSONTokener(in));
        } catch (Exception e) {
            // Handle corrupted JSON/ZIP files
            System.err.println("Error loading locale file " + path + ": " + e.getMessage());
            // Create a minimal fallback translations object
            translations = new JSONObject();
            translations.put("error.title", "Error");
            translations.put("error.generic_title", "An error occurred");
            translations.put("error.no_images_title", "No Images Available");
            translations.put("error.no_images_description", "No images could be found for this request.");
            translations.put("error.unexpected_error", "Unexpected Error");
            translations.put("error.failed_to_send_image", "Failed to send image");
            translations.put("error.unknown_button_title", "Unknown Button");
            translations.put("error.unknown_button_description", "This button action is not recognized.");
            translations.put("error.no_more_images_title", "No More Images");
            translations.put("error.no_more_images_description", "No more images available from this source.");
            translations.put("error.4chan_invalid_board_title", "Invalid Board");
            translations.put("error.4chan_invalid_board_description", "The board '{0}' is not valid.");
        } finally {
            try {
                in.close();
            } catch (Exception e) {
                // Ignore close errors
            }
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
        return translations.optString(key, key);
    }
}
