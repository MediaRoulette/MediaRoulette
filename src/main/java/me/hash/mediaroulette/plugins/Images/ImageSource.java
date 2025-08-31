package me.hash.mediaroulette.plugins.Images;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.bot.errorHandler;
import me.hash.mediaroulette.content.RandomText;
import me.hash.mediaroulette.content.factory.MediaServiceFactory;
import me.hash.mediaroulette.content.provider.impl.images.FourChanProvider;
import me.hash.mediaroulette.content.provider.impl.images.RedditProvider;
import me.hash.mediaroulette.model.content.MediaResult;
import me.hash.mediaroulette.content.reddit.RedditClient;
import me.hash.mediaroulette.content.reddit.SubredditManager;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.utils.Locale;
import me.hash.mediaroulette.utils.LocalConfig;
import net.dv8tion.jda.api.interactions.Interaction;

import java.util.Map;
import java.util.Optional;
import java.util.Collection;

public enum ImageSource {
    REDDIT("REDDIT"),
    TENOR("TENOR"),
    _4CHAN("4CHAN"),
    GOOGLE("GOOGLE"),
    IMGUR("IMGUR"),
    PICSUM("PICSUM"),
    RULE34XXX("RULEE34XXX"),
    MOVIE("MOVIE"),
    TVSHOW("TVSHOW"),
    URBAN("URBAN"),
    YOUTUBE("YOUTUBE"),
    SHORT("SHORT"),
    ALL("ALL");

    private final String name;

    public static final RedditClient redditClient = new RedditClient();
    public static final SubredditManager subredditManager = new SubredditManager(redditClient);

    ImageSource(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> handle(Interaction event, String option) throws Exception {
        User user = Main.userService.getOrCreateUser(event.getUser().getId());

        // Check both old config system and new LocalConfig system
        if (isOptionDisabled(this.name) || !isSourceEnabledInLocalConfig(this)) {
            errorHandler.sendErrorEmbed(event, new Locale(user.getLocale()).get("error.no_images_title"), new Locale(user.getLocale()).get("error.no_images_description"));
            throw new Exception("Command Disabled");
        }

        return switch (this) {
            case REDDIT -> handleReddit(event, option);
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
            case ALL -> user.getImage();

        };
    }

    private Map<String, String> handleReddit(Interaction event, String option) throws Exception {
        User user = Main.userService.getOrCreateUser(event.getUser().getId());

        // Only use the option if explicitly provided, otherwise let RedditProvider handle dictionary logic
        String subreddit = option; // Don't set random subreddit here!

        // Only validate if a specific subreddit was requested
        if (subreddit != null && !subredditManager.doesSubredditExist(subreddit)) {
            String errorMessage = new Locale(user.getLocale()).get("error.invalid_subreddit_description").replace("{0}", subreddit);
            errorHandler.sendErrorEmbed(event, new Locale(user.getLocale()).get("error.invalid_subreddit_title"), errorMessage);
            throw new Exception("Subreddit doesn't exist: " + subreddit);
        }

        RedditProvider reddit = (RedditProvider) new MediaServiceFactory().createRedditProvider();

        MediaResult redditPost;
        try {
            redditPost = reddit.getRandomReddit(subreddit, event.getUser().getId());
        } catch (Exception e) {
            // Check if it's a subreddit validation error
            if (e.getMessage().contains("No valid subreddits found") || e.getMessage().contains("Unable to find a valid subreddit")) {
                errorHandler.sendErrorEmbed(event, new Locale(user.getLocale()).get("error.title"), new Locale(user.getLocale()).get("error.reddit_no_valid_subreddit"));
                throw new Exception("No valid subreddits available");
            } else {
                errorHandler.sendErrorEmbed(event, new Locale(user.getLocale()).get("error.title"), new Locale(user.getLocale()).get("error.reddit_fetch"));
                throw new Exception("Error fetching Reddit data: " + e.getMessage());
            }
        }

        if (redditPost == null) {
            errorHandler.sendErrorEmbed(event, new Locale(user.getLocale()).get("error.title"), new Locale(user.getLocale()).get("error.reddit_fetch"));
            throw new Exception("Error fetching Reddit data");
        }

        System.out.println(redditPost.toMap().toString());

        return redditPost.toMap();
    }

    private Map<String, String> handle4Chan(Interaction event, String option) throws Exception {
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

    private Map<String, String> handleUrban(Interaction event, String option) throws Exception {
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
    private boolean isSourceEnabledInLocalConfig(ImageSource source) {
        LocalConfig config = LocalConfig.getInstance();
        String configKey = mapSourceToConfigKey(source);
        return config.isSourceEnabled(configKey);
    }
    
    /**
     * Map ImageSource enum values to their corresponding LocalConfig keys
     */
    private String mapSourceToConfigKey(ImageSource source) {
        return switch (source) {
            case REDDIT -> "reddit";
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
        };
    }

    public static Optional<ImageSource> fromName(String name) {
        for (ImageSource source : values()) {
            if (source.getName().equalsIgnoreCase(name)) {
                return Optional.of(source);
            }
        }
        return Optional.empty();
    }

    /**
     * Get the configuration key for this source
     * @return The configuration key used in LocalConfig
     */
    public String getConfigKey() {
        return mapSourceToConfigKey(this);
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