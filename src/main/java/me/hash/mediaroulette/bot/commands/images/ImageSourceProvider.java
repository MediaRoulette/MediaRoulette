package me.hash.mediaroulette.bot.commands.images;

import me.hash.mediaroulette.model.User;
import net.dv8tion.jda.api.interactions.Interaction;

import java.util.Map;

/**
 * Interface for image source providers that can be implemented by plugins
 * to add new image sources dynamically
 */
public interface ImageSourceProvider {
    
    /**
     * Get the unique name/identifier for this image source
     * @return The source name (e.g., "REDDIT", "CUSTOM_API")
     */
    String getName();
    
    /**
     * Get the display name for this image source
     * @return Human-readable display name
     */
    String getDisplayName();
    
    /**
     * Get a description of what this image source provides
     * @return Description of the image source
     */
    String getDescription();
    
    /**
     * Check if this image source is currently enabled/available
     * @return true if the source is enabled and can provide images
     */
    boolean isEnabled();
    
    /**
     * Get a random image from this source
     * @param interaction The Discord interaction context
     * @param user The user requesting the image
     * @param query Optional search query/filter
     * @return Map<String, String> containing the image data (same format as ImageSource.handle())
     * @throws Exception if there's an error fetching the image
     */
    Map<String, String> getRandomImage(Interaction interaction, User user, String query) throws Exception;
    
    /**
     * Get the configuration key used in LocalConfig for this source
     * @return Configuration key string
     */
    String getConfigKey();
    
    /**
     * Check if this source supports search queries
     * @return true if the source can handle search queries
     */
    default boolean supportsSearch() {
        return false;
    }
    
    /**
     * Get the priority of this source (higher = more priority in listings)
     * @return Priority value (0-100, default 50)
     */
    default int getPriority() {
        return 50;
    }
}