package me.hash.mediaroulette.bot.commands.images;

import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.model.content.MediaResult;
import me.hash.mediaroulette.model.content.MediaSource;
import me.hash.mediaroulette.plugins.Images.ImageSource;
import me.hash.mediaroulette.plugins.Images.ImageSourceProvider;
import net.dv8tion.jda.api.interactions.Interaction;

import java.util.Map;

/**
 * Wrapper class that adapts the existing ImageSource enum to the new ImageSourceProvider interface
 * This maintains backward compatibility while allowing the new dynamic system
 */
public class BuiltInImageSourceProvider implements ImageSourceProvider {
    
    private final ImageSource source;
    
    public BuiltInImageSourceProvider(ImageSource source) {
        this.source = source;
    }
    
    @Override
    public String getName() {
        return source.getName();
    }
    
    @Override
    public String getDisplayName() {
        return source.getName();
    }
    
    @Override
    public String getDescription() {
        return getDescriptionForSource(source);
    }
    
    @Override
    public boolean isEnabled() {
        // Use the same logic as ImageSource.handle() method
        return !isOptionDisabled(source.getName()) && isSourceEnabledInLocalConfig(source);
    }
    
    /**
     * Check if option is disabled in the old config system
     */
    private static boolean isOptionDisabled(String option) {
        return !me.hash.mediaroulette.bot.Bot.config.getOrDefault(option, true, Boolean.class);
    }
    
    /**
     * Check if this source is enabled in LocalConfig (admin toggle system)
     */
    private boolean isSourceEnabledInLocalConfig(ImageSource source) {
        me.hash.mediaroulette.utils.LocalConfig config = me.hash.mediaroulette.utils.LocalConfig.getInstance();
        String configKey = source.getConfigKey();
        return config.isSourceEnabled(configKey);
    }
    
    @Override
    public MediaResult getRandomImage(Interaction interaction, User user, String query) throws Exception {
        Map<String, String> result = source.handle(interaction, query);
        return convertMapToMediaResult(result);
    }
    
    /**
     * Convert the legacy Map format to MediaResult
     */
    private MediaResult convertMapToMediaResult(Map<String, String> map) {
        if (map == null) {
            return null;
        }
        
        String imageUrl = map.get("image");
        String title = map.get("title");
        String description = map.get("description");
        String imageType = map.get("image_type");
        String imageContent = map.get("image_content");
        
        // Map ImageSource to MediaSource
        MediaSource mediaSource = mapImageSourceToMediaSource(source);
        
        return new MediaResult(imageUrl, title, description, mediaSource, imageType, imageContent);
    }
    
    /**
     * Map ImageSource enum to MediaSource enum
     */
    private MediaSource mapImageSourceToMediaSource(ImageSource imageSource) {
        return switch (imageSource) {
            case _4CHAN -> MediaSource.CHAN_4;
            case PICSUM -> MediaSource.PICSUM;
            case IMGUR -> MediaSource.IMGUR;
            case RULE34XXX -> MediaSource.RULE34;
            case GOOGLE -> MediaSource.GOOGLE;
            case TENOR -> MediaSource.TENOR;
            case REDDIT -> MediaSource.REDDIT;
            case MOVIE, TVSHOW -> MediaSource.TMDB;
            case YOUTUBE, SHORT -> MediaSource.YOUTUBE;
            default -> MediaSource.REDDIT; // Default fallback
        };
    }
    
    @Override
    public String getConfigKey() {
        return source.getConfigKey();
    }
    
    @Override
    public boolean supportsSearch() {
        // Most built-in sources support some form of search/filtering
        return switch (source) {
            case REDDIT, GOOGLE, IMGUR, YOUTUBE, SHORT -> true;
            default -> false;
        };
    }
    
    @Override
    public int getPriority() {
        // Assign priorities to built-in sources
        return switch (source) {
            case REDDIT -> 90;
            case GOOGLE -> 85;
            case IMGUR -> 80;
            case YOUTUBE -> 75;
            case TENOR -> 70;
            case _4CHAN -> 65;
            case PICSUM -> 60;
            case RULE34XXX -> 55;
            case MOVIE -> 50;
            case TVSHOW -> 50;
            case URBAN -> 45;
            case SHORT -> 40;
            default -> 50;
        };
    }
    
    /**
     * Get the underlying ImageSource enum value
     * @return The wrapped ImageSource
     */
    public ImageSource getImageSource() {
        return source;
    }
    
    /**
     * Provide descriptions for built-in sources
     */
    private String getDescriptionForSource(ImageSource source) {
        return switch (source) {
            case REDDIT -> "Random images from Reddit subreddits";
            case TENOR -> "GIFs and animated images from Tenor";
            case _4CHAN -> "Images from 4chan boards";
            case GOOGLE -> "Images from Google Image Search";
            case IMGUR -> "Images from Imgur";
            case PICSUM -> "Random placeholder images from Lorem Picsum";
            case RULE34XXX -> "Adult content from Rule34";
            case MOVIE -> "Movie-related images from TMDB";
            case TVSHOW -> "TV show images from TMDB";
            case URBAN -> "Images related to Urban Dictionary terms";
            case YOUTUBE -> "YouTube video thumbnails";
            case SHORT -> "YouTube Shorts thumbnails";
            default -> "Built-in image source";
        };
    }
}