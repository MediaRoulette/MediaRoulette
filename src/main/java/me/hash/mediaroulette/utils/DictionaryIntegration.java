package me.hash.mediaroulette.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.hash.mediaroulette.Main;

import java.util.Objects;

/**
 * Utility class to integrate dictionary system with existing providers
 */
public class DictionaryIntegration {
    private static final Logger logger = LoggerFactory.getLogger(DictionaryIntegration.class);
    
    /**
     * Get a random word for a specific source and user
     * Falls back to existing system if no dictionary is assigned
     */
    public static String getRandomWordForSource(String userId, String source) {
        logger.debug("Getting word for user {} and source {}", userId, source);
        if (Main.getDictionaryService() != null) {
            String word = Main.getDictionaryService().getRandomWordForSource(userId, source);
            logger.debug("Dictionary service returned: {}", word);
            if (word != null && !word.equals("random")) {
                logger.debug("Using dictionary word: {}", word);
                return word;
            }
        }
        
        // Fallback to existing system
        logger.debug("Using fallback word");
        if (Objects.equals(source, "reddit"))
            return getDefaultRandomSubreddit();
        return getDefaultRandomWord();
    }
    
    /**
     * Get a random word for a source without user context (fallback)
     */
    public static String getRandomWordForSource(String source) {
        return getDefaultRandomWord();
    }
    
    /**
     * Fallback to the existing basic dictionary system
     */
    private static String getDefaultRandomWord() {
        try {
            RandomDictionaryLineFetcher fetcher = RandomDictionaryLineFetcher.getBasicDictionaryFetcher();
            return fetcher.getRandomLine();
        } catch (Exception e) {
            return "random"; // Ultimate fallback
        }
    }

    /**
     * Fallback to the existing basic dictionary system
     */
    private static String getDefaultRandomSubreddit() {
        try {
            RandomDictionaryLineFetcher fetcher = RandomDictionaryLineFetcher.getRedditDictionaryFetcher();
            return fetcher.getRandomLine();
        } catch (Exception e) {
            return "random"; // Ultimate fallback
        }
    }
}