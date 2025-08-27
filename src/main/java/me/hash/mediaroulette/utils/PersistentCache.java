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

/**
 * Persistent cache utility that saves data to JSON files
 */
public class PersistentCache<T> {
    private static final Logger logger = LoggerFactory.getLogger(PersistentCache.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private final String cacheFile;
    private final Map<String, T> cache;
    private final TypeReference<Map<String, T>> typeRef;
    
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
        cache.put(key, value);
        saveCache(); // Auto-save on every update
    }
    
    public boolean containsKey(String key) {
        return cache.containsKey(key);
    }
    
    public void remove(String key) {
        cache.remove(key);
        saveCache();
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
}