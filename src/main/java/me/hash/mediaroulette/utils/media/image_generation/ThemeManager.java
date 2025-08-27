package me.hash.mediaroulette.utils.media.image_generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ThemeManager {
    private static final Logger logger = LoggerFactory.getLogger(ThemeManager.class);
    private final Map<String, Theme> themes;
    private final ObjectMapper objectMapper;

    private ThemeManager() {
        this.objectMapper = new ObjectMapper();
        this.themes = new HashMap<>();
        loadThemes();
    }

    private static final class InstanceHolder {
        private static final ThemeManager instance = new ThemeManager();
    }

    public static ThemeManager getInstance() {
        return InstanceHolder.instance;
    }

    private void loadThemes() {
        try (InputStream inputStream = getClass().getClassLoader()
                .getResourceAsStream("config/themes.json")) {

            if (inputStream == null) {
                logger.error("themes.json not found in resources/config/");
                throw new IOException("themes.json not found in resources/config/");
            }

            TypeFactory typeFactory = objectMapper.getTypeFactory();
            List<Theme> themeList = objectMapper.readValue(inputStream,
                    typeFactory.constructCollectionType(List.class, Theme.class));

            // Clear existing themes and load from config
            themes.clear();
            for (Theme theme : themeList) {
                if (theme.getName() != null && !theme.getName().trim().isEmpty()) {
                    themes.put(theme.getName(), theme);
                } else {
                    logger.warn("Skipped theme with null or empty name");
                }
            }

            logger.info("Successfully loaded {} themes from config", themes.size());

        } catch (IOException e) {
            logger.error("Failed to load themes from config file", e);
            // If config loading fails, we'll have an empty themes map
            // The getTheme method will handle this gracefully
        }
    }

    /**
     * Gets a theme by name. If the theme doesn't exist, returns null.
     * It's the caller's responsibility to handle null themes appropriately.
     */
    public Theme getTheme(String themeName) {
        if (themeName == null || themeName.trim().isEmpty()) {
            logger.warn("Attempted to get theme with null or empty name");
            return getDefaultTheme();
        }

        Theme theme = themes.get(themeName);
        if (theme == null) {
            logger.warn("Theme '{}' not found, returning default theme", themeName);
            return getDefaultTheme();
        }

        return theme;
    }

    /**
     * Returns the first available theme as a fallback, or null if no themes are loaded
     */
    private Theme getDefaultTheme() {
        if (themes.isEmpty()) {
            logger.error("No themes available! Please check your themes.json configuration.");
            return null;
        }

        // Return the first theme as default
        return themes.values().iterator().next();
    }

    /**
     * Gets all available theme names
     */
    public java.util.Set<String> getAvailableThemeNames() {
        return new java.util.HashSet<>(themes.keySet());
    }

    /**
     * Returns a copy of all themes
     */
    public Map<String, Theme> getAllThemes() {
        return new HashMap<>(themes);
    }

    /**
     * Checks if a theme exists
     */
    public boolean hasTheme(String themeName) {
        return themeName != null && themes.containsKey(themeName);
    }

    /**
     * Gets the number of loaded themes
     */
    public int getThemeCount() {
        return themes.size();
    }

    /**
     * Reloads themes from the config file
     */
    public void reloadThemes() {
        logger.info("Reloading themes from config file");
        loadThemes();
    }
}