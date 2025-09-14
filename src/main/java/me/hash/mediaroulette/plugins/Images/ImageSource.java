package me.hash.mediaroulette.plugins.Images;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.bot.utils.errorHandler;
import me.hash.mediaroulette.content.RandomText;
import me.hash.mediaroulette.content.factory.MediaServiceFactory;
import me.hash.mediaroulette.content.provider.impl.images.FourChanProvider;
import me.hash.mediaroulette.model.content.MediaResult;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.model.ImageOptions;
import me.hash.mediaroulette.utils.LocaleManager;
import me.hash.mediaroulette.utils.LocalConfig;
import net.dv8tion.jda.api.interactions.Interaction;

import java.util.*;

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
        LocaleManager localeManager = LocaleManager.getInstance(user.getLocale());

        // Check if it's the special "ALL" case
        if (ALL.equals(sourceName)) {
            return getRandomImageRespectingUserChances(event, user);
        }

        // Try to get provider from registry first (supports both built-in and plugin sources)
        ImageSourceProvider provider = ImageSourceRegistry.getInstance().getProvider(sourceName);
        if (provider != null) {
            // Check if provider is enabled
            if (!provider.isEnabled()) {
                errorHandler.sendErrorEmbed(event, localeManager.get("error.no_images_title"), localeManager.get("error.no_images_description"));
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
        LocaleManager localeManager = LocaleManager.getInstance(user.getLocale());

        if (isOptionDisabled(sourceName) || !isSourceEnabledInLocalConfig(sourceName)) {
            errorHandler.sendErrorEmbed(event, localeManager.get("error.no_images_title"), localeManager.get("error.no_images_description"));
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

    private static Map<String, String> handleUrban(Interaction event, String option) throws Exception {
        User user = Main.userService.getOrCreateUser(event.getUser().getId());
        LocaleManager localeManager = LocaleManager.getInstance(user.getLocale());

        Map<String, String> map = RandomText.getRandomUrbanWord(option);
        if (map.containsKey("error")) {
            errorHandler.sendErrorEmbed(event, localeManager.get("error.title"), map.get("error"));
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

    /**
     * Get a random image respecting user-configured chances for both built-in and plugin sources
     * @param interaction The Discord interaction
     * @param user The user requesting the image
     * @return Map<String, String> containing the image data
     */
    private static Map<String, String> getRandomImageRespectingUserChances(Interaction interaction, User user) throws Exception {
        // Get all available sources from registry
        ImageSourceRegistry registry = ImageSourceRegistry.getInstance();
        Collection<ImageSourceProvider> allProviders = registry.getAllProviders();
        
        // Get default options and user options
        List<ImageOptions> defaultImageOptions = ImageOptions.getDefaultOptions();
        Map<String, ImageOptions> userImageOptions = user.getImageOptionsMap();
        LocalConfig config = LocalConfig.getInstance();
        
        // Build a list of weighted sources
        List<WeightedSource> weightedSources = new ArrayList<>();
        double totalWeight = 0;
        
        // Process default sources from config file
        for (ImageOptions defaultOption : defaultImageOptions) {
            String imageType = defaultOption.getImageType();
            
            // Check if source is enabled in admin config
            if (!isSourceEnabledInConfig(config, imageType)) {
                continue;
            }
            
            // Find corresponding provider
            ImageSourceProvider provider = findProviderForImageType(allProviders, imageType);
            if (provider == null || !provider.isEnabled()) {
                continue;
            }
            
            ImageOptions userOption = userImageOptions.get(imageType);
            double weight;
            boolean enabled;
            
            if (userOption != null) {
                // User has explicitly set this option
                enabled = userOption.isEnabled();
                weight = userOption.getChance();
            } else {
                // Use default settings
                enabled = defaultOption.isEnabled();
                weight = defaultOption.getChance();
            }
            
            if (enabled && weight > 0) {
                weightedSources.add(new WeightedSource(provider, weight, imageType));
                totalWeight += weight;
            }
        }
        
        // Also check for plugin sources that have user-configured chances but aren't in defaults
        for (Map.Entry<String, ImageOptions> entry : userImageOptions.entrySet()) {
            String imageType = entry.getKey();
            ImageOptions userOption = entry.getValue();
            
            // Skip if already processed from defaults
            if (defaultImageOptions.stream().anyMatch(opt -> opt.getImageType().equals(imageType))) {
                continue;
            }
            
            // Find corresponding provider
            ImageSourceProvider provider = findProviderForImageType(allProviders, imageType);
            if (provider != null && provider.isEnabled() && userOption.isEnabled() && userOption.getChance() > 0) {
                weightedSources.add(new WeightedSource(provider, userOption.getChance(), imageType));
                totalWeight += userOption.getChance();
            }
        }
        
        if (weightedSources.isEmpty()) {
            throw new Exception("No image sources are enabled or have valid chances configured");
        }
        
        // Select a random source based on weights
        Random random = new Random();
        double randomValue = random.nextDouble() * totalWeight;
        double currentWeight = 0;
        
        for (WeightedSource weightedSource : weightedSources) {
            currentWeight += weightedSource.weight;
            if (randomValue <= currentWeight) {
                try {
                    MediaResult result = weightedSource.provider.getRandomImage(interaction, user, null);
                    return result != null ? result.toMap() : null;
                } catch (Exception e) {
                    // If this source fails, fall back to the old user.getImage() method
                    System.err.println("Selected source failed: " + weightedSource.imageType + ", falling back to legacy method: " + e.getMessage());
                    return user.getImage();
                }
            }
        }
        
        // Fallback - should not reach here, but just in case
        return user.getImage();
    }
    
    /**
     * Find a provider that matches the given image type
     */
    private static ImageSourceProvider findProviderForImageType(Collection<ImageSourceProvider> providers, String imageType) {
        // First try exact match (case insensitive)
        for (ImageSourceProvider provider : providers) {
            if (provider.getName().equalsIgnoreCase(imageType)) {
                return provider;
            }
        }
        
        // Try mapping common image types to provider names
        String mappedName = mapImageTypeToProviderName(imageType);
        if (mappedName != null) {
            for (ImageSourceProvider provider : providers) {
                if (provider.getName().equalsIgnoreCase(mappedName)) {
                    return provider;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Map image types from config to provider names
     */
    private static String mapImageTypeToProviderName(String imageType) {
        return switch (imageType.toLowerCase()) {
            case "4chan" -> _4CHAN;
            case "picsum" -> PICSUM;
            case "imgur" -> IMGUR;
            case "reddit" -> "REDDIT"; // Plugin source
            case "rule34xxx" -> RULE34XXX;
            case "tenor" -> TENOR;
            case "google" -> GOOGLE;
            case "movies" -> MOVIE;
            case "tvshow" -> TVSHOW;
            case "youtube" -> YOUTUBE;
            case "short" -> SHORT;
            case "urban" -> URBAN;
            default -> null;
        };
    }
    
    /**
     * Check if a source is enabled in admin config
     */
    private static boolean isSourceEnabledInConfig(LocalConfig config, String imageType) {
        String configKey = mapImageTypeToConfigKey(imageType);
        return config.isSourceEnabled(configKey);
    }
    
    /**
     * Map image types to config keys
     */
    private static String mapImageTypeToConfigKey(String imageType) {
        return switch (imageType.toLowerCase()) {
            case "4chan" -> "4chan";
            case "picsum" -> "picsum";
            case "imgur" -> "imgur";
            case "reddit" -> "reddit";
            case "rule34xxx" -> "rule34";
            case "tenor" -> "tenor";
            case "google" -> "google";
            case "movies" -> "tmdb_movie";
            case "tvshow" -> "tmdb_tv";
            case "youtube" -> "youtube";
            case "short" -> "youtube_shorts";
            case "urban" -> "urban_dictionary";
            default -> imageType.toLowerCase();
        };
    }
    
    /**
     * Helper class to store weighted source information
     */
    private static class WeightedSource {
        final ImageSourceProvider provider;
        final double weight;
        final String imageType;
        
        WeightedSource(ImageSourceProvider provider, double weight, String imageType) {
            this.provider = provider;
            this.weight = weight;
            this.imageType = imageType;
        }
    }

}