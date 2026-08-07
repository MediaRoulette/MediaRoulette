package dev.mediaroulette.utils.media.ffmpeg.resolvers;

import java.util.concurrent.CompletableFuture;

/**
 * Interface for URL resolvers that handle different video platforms
 */
public interface UrlResolver {
    
    /**
     * Checks if this resolver can handle the given URL
     */
    boolean canResolve(String url);
    
    /**
     * Resolves the URL to a direct video URL
     */
    CompletableFuture<String> resolve(String url);
    
    /**
     * Gets the priority of this resolver (higher = more priority)
     */
    default int getPriority() {
        return 0;
    }
}