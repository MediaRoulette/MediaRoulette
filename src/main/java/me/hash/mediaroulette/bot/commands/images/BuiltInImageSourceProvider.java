package me.hash.mediaroulette.bot.commands.images;

import me.hash.mediaroulette.content.provider.MediaProvider;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.model.content.MediaResult;
import me.hash.mediaroulette.plugins.Images.ImageSource;
import me.hash.mediaroulette.plugins.Images.ImageSourceProvider;
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
        return !isOptionDisabled(sourceName) && isSourceEnabledInLocalConfig(sourceName);
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
    
    // --- Legacy/Helper methods ---

    private static boolean isOptionDisabled(String option) {
        return !me.hash.mediaroulette.Main.getBot().getConfig().getOrDefault(option, true, Boolean.class);
    }
    
    private boolean isSourceEnabledInLocalConfig(String sourceName) {
        me.hash.mediaroulette.utils.LocalConfig config = me.hash.mediaroulette.utils.LocalConfig.getInstance();
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
            case ImageSource.MOVIE -> "Movie-related images from TMDB";
            case ImageSource.TVSHOW -> "TV show images from TMDB";
            case ImageSource.URBAN -> "Images related to Urban Dictionary terms";
            case ImageSource.YOUTUBE -> "YouTube video thumbnails";
            case ImageSource.SHORT -> "YouTube Shorts thumbnails";
            default -> mediaProvider.getProviderName();
        };
    }
}