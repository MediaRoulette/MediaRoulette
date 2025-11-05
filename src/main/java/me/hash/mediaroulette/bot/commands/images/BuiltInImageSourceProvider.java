package me.hash.mediaroulette.bot.commands.images;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.utils.errorHandler;
import me.hash.mediaroulette.content.RandomText;
import me.hash.mediaroulette.content.factory.MediaServiceFactory;
import me.hash.mediaroulette.content.provider.impl.images.FourChanProvider;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.model.content.MediaResult;
import me.hash.mediaroulette.model.content.MediaSource;
import me.hash.mediaroulette.plugins.Images.ImageSource;
import me.hash.mediaroulette.plugins.Images.ImageSourceProvider;
import me.hash.mediaroulette.utils.LocaleManager;
import net.dv8tion.jda.api.interactions.Interaction;

import java.util.Map;

/**
 * Wrapper class that adapts the existing ImageSource enum to the new ImageSourceProvider interface
 * This maintains backward compatibility while allowing the new dynamic system
 */
public class BuiltInImageSourceProvider implements ImageSourceProvider {
    
    private final String sourceName;
    
    public BuiltInImageSourceProvider(String sourceName) {
        this.sourceName = sourceName;
    }
    
    @Override
    public String getName() {
        return sourceName;
    }
    
    @Override
    public String getDisplayName() {
        return sourceName;
    }
    
    @Override
    public String getDescription() {
        return getDescriptionForSource(sourceName);
    }
    
    @Override
    public boolean isEnabled() {
        // Use the same logic as ImageSource.handle() method
        return !isOptionDisabled(sourceName) && isSourceEnabledInLocalConfig(sourceName);
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
    private boolean isSourceEnabledInLocalConfig(String sourceName) {
        me.hash.mediaroulette.utils.LocalConfig config = me.hash.mediaroulette.utils.LocalConfig.getInstance();
        String configKey = ImageSource.getConfigKey(sourceName);
        return config.isSourceEnabled(configKey);
    }
    
    @Override
    public MediaResult getRandomImage(Interaction interaction, User user, String query) throws Exception {
        // Call the built-in source handling directly to avoid infinite recursion
        Map<String, String> result = handleBuiltInSourceDirect(sourceName, interaction, query, user);
        return convertMapToMediaResult(result);
    }
    
    /**
     * Handle built-in source directly without going through registry to avoid infinite recursion
     */
    private Map<String, String> handleBuiltInSourceDirect(String sourceName, Interaction event, String option, User user) throws Exception {
        LocaleManager localeManager = LocaleManager.getInstance(user.getLocale());

        if (isOptionDisabled(sourceName) || !isSourceEnabledInLocalConfig(sourceName)) {
            errorHandler.sendErrorEmbed(event, localeManager.get("error.no_images_title"), localeManager.get("error.no_images_description"));
            throw new Exception("Command Disabled");
        }

        return switch (sourceName) {
            case ImageSource.TENOR -> {
                var provider = new MediaServiceFactory().createTenorProvider();
                if (provider instanceof me.hash.mediaroulette.content.provider.impl.gifs.TenorProvider tenorProvider) {
                    yield tenorProvider.getRandomMedia(option, event.getUser().getId()).toMap();
                } else {
                    yield provider.getRandomMedia(option).toMap();
                }
            }
            case ImageSource.IMGUR -> new MediaServiceFactory().createImgurProvider().getRandomMedia(null).toMap();
            case ImageSource._4CHAN -> handle4Chan(event, option);
            case ImageSource.GOOGLE -> {
                var provider = new MediaServiceFactory().createGoogleProvider();
                if (provider instanceof me.hash.mediaroulette.content.provider.impl.images.GoogleProvider googleProvider) {
                    yield googleProvider.getRandomMedia(option, event.getUser().getId()).toMap();
                } else {
                    yield provider.getRandomMedia(option).toMap();
                }
            }
            case ImageSource.PICSUM -> new MediaServiceFactory().createPicsumProvider().getRandomMedia(null).toMap();
            case ImageSource.RULE34XXX -> new MediaServiceFactory().createRule34Provider().getRandomMedia(null).toMap();
            case ImageSource.MOVIE -> new MediaServiceFactory().createTMDBMovieProvider().getRandomMedia(null).toMap();
            case ImageSource.TVSHOW -> new MediaServiceFactory().createTMDBTvProvider().getRandomMedia(null).toMap();
            case ImageSource.URBAN -> handleUrban(event, option);
            case ImageSource.YOUTUBE -> new MediaServiceFactory().createYouTubeProvider().getRandomMedia(null).toMap();
            case ImageSource.SHORT -> new MediaServiceFactory().createYouTubeShortsProvider().getRandomMedia(null).toMap();
            default -> throw new RuntimeException("Unknown image source: " + sourceName);
        };
    }

    private Map<String, String> handle4Chan(Interaction event, String option) throws Exception {
        User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
        LocaleManager localeManager = LocaleManager.getInstance(user.getLocale());

        FourChanProvider provider = (FourChanProvider) new MediaServiceFactory().createFourChanProvider();

        if (option != null && !provider.isValidBoard(option)) {
            String errorMessage = localeManager.get("error.4chan_invalid_board_description").replace("{0}", option);
            errorHandler.sendErrorEmbed(event, localeManager.get("error.4chan_invalid_board_title"), errorMessage);
            throw new Exception("Board doesn't exist: " + option);
        }
        
        try {
            return provider.getRandomMedia(option, event.getUser().getId()).toMap();
        } catch (Exception e) {
            // Check if it's a board validation error
            if (e.getMessage().contains("No valid 4chan boards found") || e.getMessage().contains("No images available for board")) {
                errorHandler.sendErrorEmbed(event, localeManager.get("error.title"), "No valid 4chan boards available. Please use /support for help.");
                throw new Exception("No valid 4chan boards available");
            } else {
                errorHandler.sendErrorEmbed(event, localeManager.get("error.title"), "Error fetching 4chan data. Please use /support for help.");
                throw new Exception("Error fetching 4chan data: " + e.getMessage());
            }
        }
    }

    private Map<String, String> handleUrban(Interaction event, String option) throws Exception {
        User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
        LocaleManager localeManager = LocaleManager.getInstance(user.getLocale());

        Map<String, String> map = RandomText.getRandomUrbanWord(option);
        if (map.containsKey("error")) {
            errorHandler.sendErrorEmbed(event, localeManager.get("error.title"), map.get("error"));
            throw new Exception(map.get("error"));
        }

        return map;
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
        
        // Map source name to MediaSource
        MediaSource mediaSource = mapSourceNameToMediaSource(sourceName);
        
        return new MediaResult(imageUrl, title, description, mediaSource, imageType, imageContent);
    }
    
    /**
     * Map source name to MediaSource enum
     */
    private MediaSource mapSourceNameToMediaSource(String sourceName) {
        return switch (sourceName) {
            case ImageSource._4CHAN -> MediaSource.CHAN_4;
            case ImageSource.PICSUM -> MediaSource.PICSUM;
            case ImageSource.IMGUR -> MediaSource.IMGUR;
            case ImageSource.RULE34XXX -> MediaSource.RULE34;
            case ImageSource.GOOGLE -> MediaSource.GOOGLE;
            case ImageSource.TENOR -> MediaSource.TENOR;
            case ImageSource.MOVIE, ImageSource.TVSHOW -> MediaSource.TMDB;
            case ImageSource.YOUTUBE, ImageSource.SHORT -> MediaSource.YOUTUBE;
            case ImageSource.URBAN -> MediaSource.URBAN_DICTIONARY;
            default -> MediaSource.UNKNOWN; // Default fallback
        };
    }
    
    @Override
    public String getConfigKey() {
        return ImageSource.getConfigKey(sourceName);
    }
    
    @Override
    public boolean supportsSearch() {
        return true;
    }
    
    @Override
    public int getPriority() {
        // Assign priorities to built-in sources
        return switch (sourceName) {
            case ImageSource.GOOGLE -> 85;
            case ImageSource.IMGUR -> 80;
            case ImageSource.YOUTUBE -> 75;
            case ImageSource.TENOR -> 70;
            case ImageSource._4CHAN -> 65;
            case ImageSource.PICSUM -> 60;
            case ImageSource.RULE34XXX -> 55;
            case ImageSource.MOVIE -> 50;
            case ImageSource.TVSHOW -> 50;
            case ImageSource.URBAN -> 45;
            case ImageSource.SHORT -> 40;
            default -> 50;
        };
    }
    
    /**
     * Get the source name
     * @return The source name
     */
    public String getSourceName() {
        return sourceName;
    }
    
    /**
     * Provide descriptions for built-in sources
     */
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
            default -> "Built-in image source";
        };
    }
}