package dev.mediaroulette.plugins;

import dev.mediaroulette.Main;
import dev.mediaroulette.bot.Bot;
import dev.mediaroulette.service.DictionaryService;
import dev.mediaroulette.service.StatsTrackingService;
import dev.mediaroulette.database.Database;
import dev.mediaroulette.config.LocalConfig;
/**
 * Public API for plugins to access MediaRoulette services and components.
 * This prevents plugins from accessing private fields directly.
 */
public final class PluginAPI {
    
    private PluginAPI() {
        throw new UnsupportedOperationException("Cannot instantiate PluginAPI");
    }
    
    // Core Services
    
    public static DictionaryService getDictionaryService() {
        return Main.getDictionaryService();
    }
    
    public static StatsTrackingService getStatsService() {
        return Main.getStatsService();
    }
    
    // Core Components
    
    public static Bot getBot() {
        return Main.getBot();
    }
    
    public static Database getDatabase() {
        return Main.getDatabase();
    }
    
    public static PluginManager getPluginManager() {
        return Main.getPluginManager();
    }
    
    // Configuration
    
    public static LocalConfig getConfig() {
        return Main.getLocalConfig();
    }
    
    public static String getEnv(String key) {
        return Main.getEnv(key);
    }
}
