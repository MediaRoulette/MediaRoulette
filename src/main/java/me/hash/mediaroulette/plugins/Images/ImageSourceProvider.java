package me.hash.mediaroulette.plugins.images;

import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.model.PreferenceSetting;
import me.hash.mediaroulette.model.content.MediaResult;
import net.dv8tion.jda.api.interactions.Interaction;

import java.util.Collections;
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
     * @return MediaResult containing the image data and metadata
     * @throws Exception if there's an error fetching the image
     */
    MediaResult getRandomImage(Interaction interaction, User user, String query) throws Exception;
    
    /**
     * Get a random image from this source (backward compatibility method)
     * @param interaction The Discord interaction context
     * @param user The user requesting the image
     * @param query Optional search query/filter
     * @return Map<String, String> containing the image data for backward compatibility
     * @throws Exception if there's an error fetching the image
     */
    default Map<String, String> getRandomImageAsMap(Interaction interaction, User user, String query) throws Exception {
        MediaResult result = getRandomImage(interaction, user, query);
        return result != null ? result.toMap() : null;
    }
    
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
    
    /**
     * Get the schema of configurable settings for this source.
     * Providers can override this to expose user-configurable options.
     * 
     * Example for Reddit:
     * <pre>
     * return Map.of(
     *     "sortMethod", PreferenceSetting.choice("sortMethod", "Sort Method", 
     *         "How posts are sorted", "hot", "hot", "top", "new", "rising"),
     *     "timeRange", PreferenceSetting.choice("timeRange", "Time Range",
     *         "Time period for top posts", "week", "hour", "day", "week", "month", "year", "all")
     * );
     * </pre>
     * 
     * @return Map of setting key → PreferenceSetting definition, empty by default
     */
    default Map<String, PreferenceSetting> getPreferencesSchema() {
        return Collections.emptyMap();
    }
    
    /**
     * Check if this source provides NSFW content.
     * If true, the source requires NSFW channel or DM access.
     * If false, the source can be used in any channel.
     * @return true if the source is NSFW (default), false if SFW
     */
    default boolean isNsfw() {
        return true; // Safe default - assume NSFW unless explicitly marked SFW
    }
}
