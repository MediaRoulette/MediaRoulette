package me.hash.mediaroulette.content.provider;

import me.hash.mediaroulette.content.http.HttpClientWrapper;
import me.hash.mediaroulette.model.content.MediaResult;
import java.io.IOException;

public interface MediaProvider {
    MediaResult getRandomMedia(String query) throws IOException, HttpClientWrapper.RateLimitException, InterruptedException;
    
    /**
     * Get random media with user context (useful for error reporting)
     */
    default MediaResult getRandomMedia(String query, String userId) throws IOException, HttpClientWrapper.RateLimitException, InterruptedException {
        return getRandomMedia(query);
    }

    boolean supportsQuery();
    String getProviderName();
    
    /**
     * Check if this provider produces NSFW content.
     * @return true if the source is NSFW (default), false if SFW
     */
    default boolean isNsfw() {
        return true; // Safe default - assume NSFW unless explicitly marked SFW
    }
}