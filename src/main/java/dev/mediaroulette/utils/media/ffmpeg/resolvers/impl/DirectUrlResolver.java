package dev.mediaroulette.utils.media.ffmpeg.resolvers.impl;

import dev.mediaroulette.utils.media.ffmpeg.resolvers.UrlResolver;

import java.util.concurrent.CompletableFuture;

/**
 * Fallback resolver for direct video URLs
 */
public class DirectUrlResolver implements UrlResolver {

    @Override
    public boolean canResolve(String url) {
        // This is the fallback resolver, so it can handle any URL
        return true;
    }

    @Override
    public CompletableFuture<String> resolve(String url) {
        // For direct URLs, just return the URL as-is
        return CompletableFuture.completedFuture(url);
    }

    @Override
    public int getPriority() {
        return -1; // Lowest priority (fallback)
    }
}