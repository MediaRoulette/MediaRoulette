package me.hash.mediaroulette.utils.media.ffmpeg.resolvers;

import me.hash.mediaroulette.utils.media.ffmpeg.resolvers.impl.GfycatResolver;
import me.hash.mediaroulette.utils.media.ffmpeg.resolvers.impl.DirectUrlResolver;

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
        initializeResolvers();
    }

    private void initializeResolvers() {
        // Add resolvers in order of priority
        resolvers.add(new GfycatResolver());
        resolvers.add(new DirectUrlResolver());
        
        resolvers.sort(Comparator.comparingInt(UrlResolver::getPriority).reversed());
    }

    public static void addResolver(UrlResolver resolver) {
        resolvers.add(resolver);
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
     * Checks if a URL is a video URL
     */
    public boolean isVideoUrl(String url) {
        if (url == null) return false;

        String lowerUrl = url.toLowerCase();
        return lowerUrl.matches(".*\\.(mp4|webm|mov|avi|mkv|flv|wmv|m4v|m4s)$") ||
                lowerUrl.contains("redgifs.com") ||
                lowerUrl.contains("gfycat.com") ||
                lowerUrl.contains("youtube.com") ||
                lowerUrl.contains("youtu.be") ||
                lowerUrl.contains("streamable.com") ||
                (lowerUrl.contains("imgur.com/") && (lowerUrl.contains(".mp4") || lowerUrl.contains(".m4s")));
    }

    /**
     * Checks if a URL should be converted to GIF
     */
    public boolean shouldConvertToGif(String url) {
        if (url == null) return false;

        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains("redgifs.com") ||
                lowerUrl.contains("gfycat.com") ||
                lowerUrl.contains("streamable.com") ||
                (lowerUrl.contains("imgur.com/") && lowerUrl.contains(".mp4"));
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