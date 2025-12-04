package me.hash.mediaroulette.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Persistent cache utility that saves data to JSON files with automatic cleanup
 */
public class PersistentCache<T> {
    private static final Logger logger = LoggerFactory.getLogger(PersistentCache.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int MAX_CACHE_SIZE = 10000; // Prevent unbounded growth
    private final String cacheFile;
    private final Map<String, T> cache;
    private final TypeReference<Map<String, T>> typeRef;
    private final ScheduledExecutorService cleanupScheduler;
    private volatile boolean shouldSaveOnUpdate = true;
    
    static {
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
    
    public PersistentCache(String filename, TypeReference<Map<String, T>> typeReference) {
        this.cacheFile = "cache/" + filename;
        this.typeRef = typeReference;
        this.cache = new ConcurrentHashMap<>();
        
        // Create cache directory if it doesn't exist
        File cacheDir = new File("cache");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        
        loadCache();
        
        // Schedule periodic cleanup and save (every 5 minutes)
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PersistentCache-" + filename);
            t.setDaemon(true);
            return t;
        });
        cleanupScheduler.scheduleAtFixedRate(this::periodicMaintenance, 5, 5, TimeUnit.MINUTES);
    }
    
    private void loadCache() {
        File file = new File(cacheFile);
        if (file.exists()) {
            try {
                Map<String, T> loadedCache = mapper.readValue(file, typeRef);
                cache.putAll(loadedCache);
                logger.info("Loaded {} items from cache: {}", cache.size(), cacheFile);
            } catch (IOException e) {
                logger.error("Failed to load cache from {}: {}", cacheFile, e.getMessage());
            }
        }
    }
    
    public void saveCache() {
        try {
            mapper.writeValue(new File(cacheFile), cache);
        } catch (IOException e) {
            logger.error("Failed to save cache to {}: {}", cacheFile, e.getMessage());
        }
    }
    
    public T get(String key) {
        return cache.get(key);
    }
    
    public void put(String key, T value) {
        // Check cache size limit
        if (cache.size() >= MAX_CACHE_SIZE && !cache.containsKey(key)) {
            logger.warn("Cache {} has reached maximum size ({}), clearing oldest entries", cacheFile, MAX_CACHE_SIZE);
            clearOldestEntries();
        }
        
        cache.put(key, value);
        if (shouldSaveOnUpdate) {
            saveCache(); // Auto-save on every update
        }
    }
    
    public boolean containsKey(String key) {
        return cache.containsKey(key);
    }
    
    public void remove(String key) {
        cache.remove(key);
        if (shouldSaveOnUpdate) {
            saveCache();
        }
    }
    
    public void clear() {
        cache.clear();
        saveCache();
    }
    
    public int size() {
        return cache.size();
    }
    
    public Map<String, T> getAll() {
        return new HashMap<>(cache);
    }
    
    // Manual save method for batch operations
    public void forceSave() {
        saveCache();
    }
    
    /**
     * Clear oldest 20% of entries when cache is full
     */
    private void clearOldestEntries() {
        int entriesToRemove = MAX_CACHE_SIZE / 5;
        cache.keySet().stream()
                .limit(entriesToRemove)
                .forEach(cache::remove);
        logger.info("Removed {} old entries from cache {}", entriesToRemove, cacheFile);
    }
    
    /**
     * Periodic maintenance: save cache and check size
     */
    private void periodicMaintenance() {
        try {
            saveCache();
            if (cache.size() > MAX_CACHE_SIZE * 0.9) {
                logger.warn("Cache {} is at {}% capacity", cacheFile, (cache.size() * 100 / MAX_CACHE_SIZE));
            }
        } catch (Exception e) {
            logger.error("Error during periodic maintenance for cache {}: {}", cacheFile, e.getMessage());
        }
    }
    
    /**
     * Disable auto-save for batch operations
     */
    public void setBatchMode(boolean batchMode) {
        this.shouldSaveOnUpdate = !batchMode;
    }
    
    /**
     * Shutdown the cleanup scheduler
     */
    public void shutdown() {
        logger.info("Shutting down cache scheduler for {}", cacheFile);
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        forceSave();
    }
}