package me.hash.mediaroulette.plugins.Images;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.bot.errorHandler;
import me.hash.mediaroulette.content.RandomText;
import me.hash.mediaroulette.content.factory.MediaServiceFactory;
import me.hash.mediaroulette.content.provider.impl.images.FourChanProvider;
import me.hash.mediaroulette.model.content.MediaResult;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.utils.Locale;
import me.hash.mediaroulette.utils.LocalConfig;
import net.dv8tion.jda.api.interactions.Interaction;

import java.util.Map;
import java.util.Optional;
import java.util.Collection;

public class ImageSource {
    
    // Static constants for built-in sources
    public static final String TENOR = "TENOR";
    public static final String _4CHAN = "4CHAN";
    public static final String GOOGLE = "GOOGLE";
    public static final String IMGUR = "IMGUR";
    public static final String PICSUM = "PICSUM";
    public static final String RULE34XXX = "RULEE34XXX";
    public static final String MOVIE = "MOVIE";
    public static final String TVSHOW = "TVSHOW";
    public static final String URBAN = "URBAN";
    public static final String YOUTUBE = "YOUTUBE";
    public static final String SHORT = "SHORT";
    public static final String ALL = "ALL";
    
    // Private constructor to prevent instantiation
    private ImageSource() {}

    /**
     * Handle image request for any source (built-in or plugin-provided)
     * @param sourceName The name of the image source
     * @param event The Discord interaction
     * @param option Optional query/filter parameter
     * @return Map containing image data
     * @throws Exception if source is disabled or error occurs
     */
    public static Map<String, String> handle(String sourceName, Interaction event, String option) throws Exception {
        User user = Main.userService.getOrCreateUser(event.getUser().getId());

        // Check if it's the special "ALL" case
        if (ALL.equals(sourceName)) {
            return user.getImage();
        }

        // Try to get provider from registry first (supports both built-in and plugin sources)
        ImageSourceProvider provider = ImageSourceRegistry.getInstance().getProvider(sourceName);
        if (provider != null) {
            // Check if provider is enabled
            if (!provider.isEnabled()) {
                errorHandler.sendErrorEmbed(event, new Locale(user.getLocale()).get("error.no_images_title"), new Locale(user.getLocale()).get("error.no_images_description"));
                throw new Exception("Source disabled: " + sourceName);
            }
            
            try {
                MediaResult result = provider.getRandomImage(event, user, option);
                return result != null ? result.toMap() : null;
            } catch (Exception e) {
                throw new RuntimeException("Error getting image from provider " + sourceName + ": " + e.getMessage(), e);
            }
        }

        // Fallback for built-in sources that might not be in registry yet
        return handleBuiltInSource(sourceName, event, option, user);
    }
    
    /**
     * Fallback handler for built-in sources
     */
    private static Map<String, String> handleBuiltInSource(String sourceName, Interaction event, String option, User user) throws Exception {
        // Check both old config system and new LocalConfig system
        if (isOptionDisabled(sourceName) || !isSourceEnabledInLocalConfig(sourceName)) {
            errorHandler.sendErrorEmbed(event, new Locale(user.getLocale()).get("error.no_images_title"), new Locale(user.getLocale()).get("error.no_images_description"));
            throw new Exception("Command Disabled");
        }

        return switch (sourceName) {
            case TENOR -> {
                var provider = new MediaServiceFactory().createTenorProvider();
                if (provider instanceof me.hash.mediaroulette.content.provider.impl.gifs.TenorProvider tenorProvider) {
                    yield tenorProvider.getRandomMedia(option, event.getUser().getId()).toMap();
                } else {
                    yield provider.getRandomMedia(option).toMap();
                }
            }
            case IMGUR -> new MediaServiceFactory().createImgurProvider().getRandomMedia(null).toMap();
            case _4CHAN -> handle4Chan(event, option);
            case GOOGLE -> {
                var provider = new MediaServiceFactory().createGoogleProvider();
                if (provider instanceof me.hash.mediaroulette.content.provider.impl.images.GoogleProvider googleProvider) {
                    yield googleProvider.getRandomMedia(option, event.getUser().getId()).toMap();
                } else {
                    yield provider.getRandomMedia(option).toMap();
                }
            }
            case PICSUM -> new MediaServiceFactory().createPicsumProvider().getRandomMedia(null).toMap();
            case RULE34XXX -> new MediaServiceFactory().createRule34Provider().getRandomMedia(null).toMap();
            case MOVIE -> new MediaServiceFactory().createTMDBMovieProvider().getRandomMedia(null).toMap();
            case TVSHOW -> new MediaServiceFactory().createTMDBTvProvider().getRandomMedia(null).toMap();
            case URBAN -> handleUrban(event, option);
            case YOUTUBE -> new MediaServiceFactory().createYouTubeProvider().getRandomMedia(null).toMap();
            case SHORT -> new MediaServiceFactory().createYouTubeShortsProvider().getRandomMedia(null).toMap();
            default -> throw new RuntimeException("Unknown image source: " + sourceName);
        };
    }


    private static Map<String, String> handle4Chan(Interaction event, String option) throws Exception {
        User user = Main.userService.getOrCreateUser(event.getUser().getId());

        FourChanProvider provider = (FourChanProvider) new MediaServiceFactory().createFourChanProvider();

        if (option != null && !provider.isValidBoard(option)) {
            String errorMessage = new Locale(user.getLocale()).get("error.4chan_invalid_board_description").replace("{0}", option);
            errorHandler.sendErrorEmbed(event, new Locale(user.getLocale()).get("error.4chan_invalid_board_title"), errorMessage);
            throw new Exception("Board doesn't exist: " + option);
        }
        
        try {
            return provider.getRandomMedia(option, event.getUser().getId()).toMap();
        } catch (Exception e) {
            // Check if it's a board validation error
            if (e.getMessage().contains("No valid 4chan boards found") || e.getMessage().contains("No images available for board")) {
                errorHandler.sendErrorEmbed(event, new Locale(user.getLocale()).get("error.title"), "No valid 4chan boards available. Please use /support for help.");
                throw new Exception("No valid 4chan boards available");
            } else {
                errorHandler.sendErrorEmbed(event, new Locale(user.getLocale()).get("error.title"), "Error fetching 4chan data. Please use /support for help.");
                throw new Exception("Error fetching 4chan data: " + e.getMessage());
            }
        }
    }

    private static Map<String, String> handleUrban(Interaction event, String option) throws Exception {
        User user = Main.userService.getOrCreateUser(event.getUser().getId());
        Map<String, String> map = RandomText.getRandomUrbanWord(option);
        if (map.containsKey("error")) {
            errorHandler.sendErrorEmbed(event, new Locale(user.getLocale()).get("error.title"), map.get("error"));
            throw new Exception(map.get("error"));
        }

        return map;
    }

    private static boolean isOptionDisabled(String option) {
        return !Bot.config.getOrDefault(option, true, Boolean.class);
    }
    
    /**
     * Check if this source is enabled in LocalConfig (admin toggle system)
     */
    private static boolean isSourceEnabledInLocalConfig(String sourceName) {
        LocalConfig config = LocalConfig.getInstance();
        String configKey = mapSourceToConfigKey(sourceName);
        return config.isSourceEnabled(configKey);
    }
    
    /**
     * Map source names to their corresponding LocalConfig keys
     */
    private static String mapSourceToConfigKey(String sourceName) {
        return switch (sourceName) {
            case TENOR -> "tenor";
            case _4CHAN -> "4chan";
            case GOOGLE -> "google";
            case IMGUR -> "imgur";
            case PICSUM -> "picsum";
            case RULE34XXX -> "rule34";
            case MOVIE -> "tmdb_movie";
            case TVSHOW -> "tmdb_tv";
            case URBAN -> "urban_dictionary";
            case YOUTUBE -> "youtube";
            case SHORT -> "youtube_shorts";
            case ALL -> "all"; // Special case - "all" should always be enabled if any sources are enabled
            default -> sourceName.toLowerCase(); // For plugin sources, use lowercase name as config key
        };
    }

    /**
     * Check if a source name corresponds to a built-in source
     * @param name The source name to check
     * @return true if it's a built-in source
     */
    public static boolean isBuiltInSource(String name) {
        return switch (name.toUpperCase()) {
            case TENOR, _4CHAN, GOOGLE, IMGUR, PICSUM, RULE34XXX, 
                 MOVIE, TVSHOW, URBAN, YOUTUBE, SHORT, ALL -> true;
            default -> false;
        };
    }

    /**
     * Get the configuration key for a source
     * @param sourceName The source name
     * @return The configuration key used in LocalConfig
     */
    public static String getConfigKey(String sourceName) {
        return mapSourceToConfigKey(sourceName);
    }

    /**
     * Get all available image sources (both built-in and plugin-provided)
     * @return Collection of all available image source providers
     */
    public static Collection<ImageSourceProvider> getAllAvailableSources() {
        return ImageSourceRegistry.getInstance().getAllProviders();
    }

    /**
     * Get all enabled image sources (both built-in and plugin-provided)
     * @return Collection of all enabled image source providers
     */
    public static Collection<ImageSourceProvider> getAllEnabledSources() {
        return ImageSourceRegistry.getInstance().getEnabledProviders();
    }

    /**
     * Find an image source provider by name (supports both built-in and plugin sources)
     * @param name The name of the source to find
     * @return Optional containing the provider if found
     */
    public static Optional<ImageSourceProvider> findSourceProvider(String name) {
        return ImageSourceRegistry.getInstance().findProvider(name);
    }

    /**
     * Get a random image from any available source (replacement for ALL)
     * @param interaction The Discord interaction
     * @param user The user requesting the image
     * @return Map<String, String> containing the image data
     */
    public static Map<String, String> getRandomImageFromAnySource(Interaction interaction, User user) throws Exception {
        Collection<ImageSourceProvider> enabledSources = getAllEnabledSources();
        if (enabledSources.isEmpty()) {
            throw new Exception("No image sources are currently enabled");
        }

        // Convert to array for random selection
        ImageSourceProvider[] sources = enabledSources.toArray(new ImageSourceProvider[0]);
        ImageSourceProvider randomSource = sources[(int) (Math.random() * sources.length)];
        
        MediaResult result = randomSource.getRandomImage(interaction, user, null);
        return result != null ? result.toMap() : null;
    }

}