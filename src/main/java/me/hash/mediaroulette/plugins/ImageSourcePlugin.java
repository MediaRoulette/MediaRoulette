package me.hash.mediaroulette.plugins;

import me.hash.mediaroulette.bot.commands.images.ImageSourceProvider;

import java.util.List;

/**
 * Interface for plugins that want to provide custom image sources
 * Plugins should implement this interface to register their image sources
 */
public interface ImageSourcePlugin {
    
    /**
     * Get the image source providers that this plugin provides
     * This method is called when the plugin is enabled
     * @return List of image source providers
     */
    List<ImageSourceProvider> getImageSourceProviders();
    
    /**
     * Called when the plugin's image sources are being registered
     * Plugins can perform any necessary initialization here
     */
    default void onImageSourcesRegistered() {
        // Default implementation does nothing
    }
    
    /**
     * Called when the plugin's image sources are being unregistered
     * Plugins should clean up any resources here
     */
    default void onImageSourcesUnregistered() {
        // Default implementation does nothing
    }
}