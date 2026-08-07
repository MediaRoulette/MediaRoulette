package dev.mediaroulette.content.provider;

import dev.mediaroulette.content.http.HttpClientWrapper;
import dev.mediaroulette.model.content.MediaResult;
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