package me.hash.mediaroulette.utils.media.ffmpeg.resolvers.impl;

import me.hash.mediaroulette.utils.media.ffmpeg.resolvers.UrlResolver;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Resolver for Gfycat URLs with improved error handling
 */
public class GfycatResolver implements UrlResolver {
    private static final Logger logger = LoggerFactory.getLogger(GfycatResolver.class);

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    public boolean canResolve(String url) {
        return url != null && url.contains("gfycat.com");
    }

    @Override
    public CompletableFuture<String> resolve(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String videoId = extractGfycatId(url);
                if (videoId == null || videoId.isEmpty()) {
                    throw new RuntimeException("Could not extract video ID from URL: " + url);
                }

                String[] urlPatterns = {
                        "https://giant.gfycat.com/" + videoId + ".mp4",
                        "https://thumbs.gfycat.com/" + videoId + "-mobile.mp4",
                        "https://zippy.gfycat.com/" + videoId + ".mp4",
                        "https://fat.gfycat.com/" + videoId + ".mp4"
                };

                for (String directUrl : urlPatterns) {
                    if (testVideoUrl(directUrl)) {
                        return directUrl;
                    }
                }

                logger.warn("All Gfycat URL patterns failed, using fallback for: {}", url);
                return urlPatterns[0];

            } catch (Exception e) {
                logger.error("Failed to resolve Gfycat URL: {} - {}", url, e.getMessage());
                return url; // Return original URL as last resort
            }
        });
    }

    @Override
    public int getPriority() {
        return 9; // High priority for Gfycat URLs
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

    private boolean testVideoUrl(String url) {
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .head()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "*/*")
                    .header("Range", "bytes=0-1024")
                    .build();

            try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                boolean isSuccessful = response.isSuccessful();
                String contentType = response.header("Content-Type", "");
                long contentLength = response.body() != null ? response.body().contentLength() : 0;

                boolean isVideo = contentType.startsWith("video/") ||
                        contentType.startsWith("application/octet-stream") ||
                        contentType.contains("mp4") ||
                        contentType.isEmpty();

                boolean hasContent = contentLength > 1000 || contentLength == -1;

                return isSuccessful && isVideo && hasContent;
            }
        } catch (Exception e) {
            return false;
        }
    }
}