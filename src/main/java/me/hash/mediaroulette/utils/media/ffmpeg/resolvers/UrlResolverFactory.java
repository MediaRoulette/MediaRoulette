package me.hash.mediaroulette.utils.media.ffmpeg.resolvers;

import me.hash.mediaroulette.utils.media.ffmpeg.resolvers.impl.DirectUrlResolver;
import me.hash.mediaroulette.utils.media.ffmpeg.resolvers.impl.RedGifsResolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Factory for URL resolvers that manages different video platform resolvers
 */
public class UrlResolverFactory {
    private static List<UrlResolver> resolvers = List.of();

    public UrlResolverFactory() {
        resolvers = new ArrayList<>();
        // Register resolvers - higher priority resolvers are checked first
        resolvers.add(new RedGifsResolver());
        resolvers.sort(Comparator.comparingInt(UrlResolver::getPriority).reversed());
    }

    public static void addResolver(UrlResolver resolver) {
        resolvers.add(resolver);
        resolvers.sort(Comparator.comparingInt(UrlResolver::getPriority).reversed());
    }

    /**
     * Gets the appropriate resolver for a URL
     */
    public UrlResolver getResolver(String url) {
        return resolvers.stream()
                .filter(resolver -> resolver.canResolve(url))
                .findFirst()
                .orElse(new DirectUrlResolver()); // Fallback to direct URL resolver
    }

    /**
     * Checks if a URL is a video URL.
     * Handles URLs with query parameters (e.g., .mp4?12345)
     */
    public boolean isVideoUrl(String url) {
        if (url == null) return false;

        String lowerUrl = url.toLowerCase();
        // Strip query parameters and fragments before checking extension
        String urlPath = stripQueryParams(lowerUrl);
        
        // Check by file extension (supports query params)
        if (urlPath.matches(".*\\.(mp4|webm|mov|avi|mkv|flv|wmv|m4v|m4s|3gp|ogv|ts)$")) {
            return true;
        }
        
        // Check by known video platforms
        return lowerUrl.contains("redgifs.com") ||
                lowerUrl.contains("gfycat.com") ||
                lowerUrl.contains("youtube.com") ||
                lowerUrl.contains("youtu.be") ||
                lowerUrl.contains("streamable.com") ||
                (lowerUrl.contains("imgur.com/") && (urlPath.endsWith(".mp4") || urlPath.endsWith(".m4s")));
    }
    
    /**
     * Strips query parameters and fragments from a URL
     */
    private String stripQueryParams(String url) {
        int queryIndex = url.indexOf('?');
        int fragmentIndex = url.indexOf('#');
        int endIndex = url.length();
        
        if (queryIndex != -1) endIndex = Math.min(endIndex, queryIndex);
        if (fragmentIndex != -1) endIndex = Math.min(endIndex, fragmentIndex);
        
        return url.substring(0, endIndex);
    }

    /**
     * Checks if a URL should be converted to GIF
     */
    public boolean shouldConvertToGif(String url) {
        if (url == null) return false;

        String lowerUrl = url.toLowerCase();
        String urlPath = stripQueryParams(lowerUrl);
        
        return lowerUrl.contains("redgifs.com") ||
                lowerUrl.contains("gfycat.com") ||
                lowerUrl.contains("streamable.com") ||
                (lowerUrl.contains("imgur.com/") && urlPath.endsWith(".mp4"));
    }

    /**
     * Gets a preview URL for a video (thumbnail image)
     */
    public String getVideoPreviewUrl(String videoUrl) {
        if (videoUrl.contains("redgifs.com")) {
            return videoUrl.replace(".com/watch/", ".com/ifr/") + "-preview.jpg";
        } else if (videoUrl.contains("gfycat.com")) {
            String id = extractGfycatId(videoUrl);
            return "https://thumbs.gfycat.com/" + id + "-poster.jpg";
        } else if (videoUrl.contains("imgur.com/") && videoUrl.contains(".mp4")) {
            return videoUrl.replace(".mp4", "h.jpg");
        }

        return null;
    }

    private String extractGfycatId(String url) {
        if (url.contains("gfycat.com/")) {
            String[] parts = url.split("/");
            for (String part : parts) {
                if (part.length() > 5 && !part.contains(".") && !part.equals("gfycat.com")) {
                    return part;
                }
            }
        }

        String lastPart = url.substring(url.lastIndexOf("/") + 1);
        if (lastPart.contains("?")) {
            lastPart = lastPart.substring(0, lastPart.indexOf("?"));
        }
        return lastPart;
    }
}