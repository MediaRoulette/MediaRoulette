package me.hash.mediaroulette.plugins.images;

import me.hash.mediaroulette.bot.commands.images.BuiltInImageSourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for managing both built-in and plugin-provided image sources
 */
public class ImageSourceRegistry {
    private static final Logger logger = LoggerFactory.getLogger(ImageSourceRegistry.class);
    private static ImageSourceRegistry instance;
    
    private final Map<String, ImageSourceProvider> providers = new ConcurrentHashMap<>();
    private final Map<String, ImageSourceProvider> builtInProviders = new ConcurrentHashMap<>();
    
    private ImageSourceRegistry() {
        // Initialize with built-in providers
        initializeBuiltInProviders();
    }
    
    public static ImageSourceRegistry getInstance() {
        if (instance == null) {
            synchronized (ImageSourceRegistry.class) {
                if (instance == null) {
                    instance = new ImageSourceRegistry();
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize built-in image source providers
     */
    private void initializeBuiltInProviders() {
        me.hash.mediaroulette.content.factory.MediaServiceFactory factory = new me.hash.mediaroulette.content.factory.MediaServiceFactory();
        
        registerBuiltIn(ImageSource.GOOGLE, factory.createGoogleProvider(), 85);
        registerBuiltIn(ImageSource.IMGUR, factory.createImgurProvider(), 80);
        registerBuiltIn(ImageSource.YOUTUBE, factory.createYouTubeProvider(), 75);
        registerBuiltIn(ImageSource.TENOR, factory.createTenorProvider(), 70);
        registerBuiltIn(ImageSource._4CHAN, factory.createFourChanProvider(), 65);
        registerBuiltIn(ImageSource.PICSUM, factory.createPicsumProvider(), 60);
        registerBuiltIn(ImageSource.RULE34XXX, factory.createRule34Provider(), 55);
        registerBuiltIn(ImageSource.MOVIE, factory.createTMDBMovieProvider(), 50);
        registerBuiltIn(ImageSource.TVSHOW, factory.createTMDBTvProvider(), 50);
        registerBuiltIn(ImageSource.URBAN, factory.createUrbanDictionaryProvider(), 45);
        registerBuiltIn(ImageSource.SHORT, factory.createYouTubeShortsProvider(), 40);
        
        logger.info("Initialized {} built-in image source providers", builtInProviders.size());
    }
    
    private void registerBuiltIn(String name, me.hash.mediaroulette.content.provider.MediaProvider provider, int priority) {
        BuiltInImageSourceProvider builtIn = new BuiltInImageSourceProvider(name, provider, priority);
        builtInProviders.put(name, builtIn);
        providers.put(name, builtIn);
    }
    
    /**
     * Register a new image source provider (typically from a plugin)
     * @param provider The provider to register
     * @return true if registered successfully, false if name already exists
     */
    public boolean registerProvider(ImageSourceProvider provider) {
        if (provider == null || provider.getName() == null || provider.getName().trim().isEmpty()) {
            logger.warn("Cannot register null provider or provider with null/empty name");
            return false;
        }
        
        String name = provider.getName().toUpperCase();
        
        if (builtInProviders.containsKey(name)) {
            logger.warn("Cannot register provider '{}' - name conflicts with built-in provider", name);
            return false;
        }
        
        if (providers.containsKey(name)) {
            logger.warn("Provider '{}' is already registered", name);
            return false;
        }
        
        providers.put(name, provider);
        logger.info("Registered image source provider: {}", name);
        return true;
    }
    
    /**
     * Unregister an image source provider
     * @param name The name of the provider to unregister
     * @return true if unregistered, false if not found or is built-in
     */
    public boolean unregisterProvider(String name) {
        if (name == null) return false;
        
        name = name.toUpperCase();
        
        if (builtInProviders.containsKey(name)) {
            logger.warn("Cannot unregister built-in provider: {}", name);
            return false;
        }
        
        ImageSourceProvider removed = providers.remove(name);
        if (removed != null) {
            logger.info("Unregistered image source provider: {}", name);
            return true;
        }
        
        return false;
    }
    
    /**
     * Get a provider by name
     * @param name The provider name
     * @return The provider, or null if not found
     */
    public ImageSourceProvider getProvider(String name) {
        if (name == null) return null;
        return providers.get(name.toUpperCase());
    }
    
    /**
     * Get all registered providers
     * @return Collection of all providers
     */
    public Collection<ImageSourceProvider> getAllProviders() {
        return Collections.unmodifiableCollection(providers.values());
    }
    
    /**
     * Get all enabled providers
     * @return Collection of enabled providers
     */
    public Collection<ImageSourceProvider> getEnabledProviders() {
        return providers.values().stream()
                .filter(ImageSourceProvider::isEnabled)
                .collect(Collectors.toList());
    }
    
    /**
     * Get providers sorted by priority (highest first)
     * @return List of providers sorted by priority
     */
    public List<ImageSourceProvider> getProvidersByPriority() {
        return providers.values().stream()
                .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                .collect(Collectors.toList());
    }
    
    /**
     * Get enabled providers sorted by priority
     * @return List of enabled providers sorted by priority
     */
    public List<ImageSourceProvider> getEnabledProvidersByPriority() {
        return providers.values().stream()
                .filter(ImageSourceProvider::isEnabled)
                .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                .collect(Collectors.toList());
    }
    
    /**
     * Check if a provider exists
     * @param name The provider name
     * @return true if the provider exists
     */
    public boolean hasProvider(String name) {
        if (name == null) return false;
        return providers.containsKey(name.toUpperCase());
    }
    
    /**
     * Get the number of registered providers
     * @return Number of providers
     */
    public int getProviderCount() {
        return providers.size();
    }
    
    /**
     * Get the number of built-in providers
     * @return Number of built-in providers
     */
    public int getBuiltInProviderCount() {
        return builtInProviders.size();
    }
    
    /**
     * Get the number of plugin-provided providers
     * @return Number of plugin providers
     */
    public int getPluginProviderCount() {
        return providers.size() - builtInProviders.size();
    }
    
    /**
     * Clear all plugin providers (used during plugin reload)
     */
    public void clearPluginProviders() {
        providers.entrySet().removeIf(entry -> !builtInProviders.containsKey(entry.getKey()));
        logger.info("Cleared all plugin providers");
    }
    
    /**
     * Find provider by name (case-insensitive)
     * @param name The name to search for
     * @return Optional containing the provider if found
     */
    public Optional<ImageSourceProvider> findProvider(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(providers.get(name.toUpperCase()));
    }
}