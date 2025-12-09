package me.hash.mediaroulette.plugins;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.service.DictionaryService;
import me.hash.mediaroulette.service.StatsTrackingService;
import me.hash.mediaroulette.database.Database;
import me.hash.mediaroulette.config.LocalConfig;
import me.hash.mediaroulette.utils.user.UserService;
import me.hash.mediaroulette.utils.vault.VaultSecretManager;

/**
 * Public API for plugins to access MediaRoulette services and components.
 * This prevents plugins from accessing private fields directly.
 */
public final class PluginAPI {
    
    private PluginAPI() {
        throw new UnsupportedOperationException("Cannot instantiate PluginAPI");
    }
    
    // Core Services
    
    public static UserService getUserService() {
        return Main.getUserService();
    }
    
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
    
    public static VaultSecretManager getVaultSecretManager() {
        return Main.getVaultSecretManager();
    }
}
