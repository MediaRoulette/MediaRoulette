package dev.mediaroulette.bot.commands.images;

import dev.mediaroulette.content.provider.MediaProvider;
import dev.mediaroulette.model.User;
import dev.mediaroulette.model.content.MediaResult;
import dev.mediaroulette.plugins.images.ImageSource;
import dev.mediaroulette.plugins.images.ImageSourceProvider;
import net.dv8tion.jda.api.interactions.Interaction;

/**
 * Generic wrapper that adapts any MediaProvider to the ImageSourceProvider interface.
 * This replaces the old static switch-case logic with a clean delegation pattern.
 */
public class BuiltInImageSourceProvider implements ImageSourceProvider {
    
    private final String sourceName;
    private final MediaProvider mediaProvider;
    private final int priority;
    
    public BuiltInImageSourceProvider(String sourceName, MediaProvider mediaProvider, int priority) {
        this.sourceName = sourceName;
        this.mediaProvider = mediaProvider;
        this.priority = priority;
    }
    
    @Override
    public String getName() {
        return sourceName;
    }
    
    @Override
    public String getDisplayName() {
        return mediaProvider.getProviderName();
    }
    
    @Override
    public String getDescription() {
        return getDescriptionForSource(sourceName);
    }
    
    @Override
    public boolean isEnabled() {
        return isSourceEnabledInLocalConfig(sourceName);
    }
    
    @Override
    public MediaResult getRandomImage(Interaction interaction, User user, String query) throws Exception {
        return mediaProvider.getRandomMedia(query, user.getUserId());
    }
    
    @Override
    public String getConfigKey() {
        return ImageSource.getConfigKey(sourceName);
    }
    
    @Override
    public boolean supportsSearch() {
        return mediaProvider.supportsQuery();
    }
    
    @Override
    public int getPriority() {
        return priority;
    }
    
    @Override
    public boolean isNsfw() {
        return mediaProvider.isNsfw();
    }
    
    private boolean isSourceEnabledInLocalConfig(String sourceName) {
        dev.mediaroulette.config.LocalConfig config = dev.mediaroulette.config.LocalConfig.getInstance();
        String configKey = ImageSource.getConfigKey(sourceName);
        return config.isSourceEnabled(configKey);
    }
    
    private String getDescriptionForSource(String sourceName) {
        return switch (sourceName) {
            case ImageSource.TENOR -> "GIFs and animated images from Tenor";
            case ImageSource._4CHAN -> "Images from 4chan boards";
            case ImageSource.GOOGLE -> "Images from Google Image Search";
            case ImageSource.IMGUR -> "Images from Imgur";
            case ImageSource.PICSUM -> "Random placeholder images from Lorem Picsum";
            case ImageSource.RULE34XXX -> "Adult content from Rule34";
            case ImageSource.BOORU -> "Random images from booru boards (Rule34, Gelbooru)";
            case ImageSource.MOVIE -> "Movie-related images from TMDB";
            case ImageSource.TVSHOW -> "TV show images from TMDB";
            case ImageSource.URBAN -> "Images related to Urban Dictionary terms";
            case ImageSource.YOUTUBE -> "YouTube video thumbnails";
            case ImageSource.SHORT -> "YouTube Shorts thumbnails";
            default -> mediaProvider.getProviderName();
        };
    }
}