package me.hash.mediaroulette.utils.media;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for parsing M3U8 playlist files and extracting video URLs
 */
public class M3u8Parser {
    private static final Logger logger = LoggerFactory.getLogger(M3u8Parser.class);

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build();
    
    private static final Pattern M3U8_URL_PATTERN = Pattern.compile("^(https?://[^\\s]+\\.(m4s|mp4|ts))$", Pattern.MULTILINE);
    
    /**
     * Parse M3U8 playlist and extract the best quality video URL
     */
    public static String extractVideoUrl(String m3u8Url) {
        try {
            Request request = new Request.Builder()
                .url(m3u8Url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();
            
            try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return null;
                }
                
                String m3u8Content = response.body().string();
                return parseM3u8Content(m3u8Content, m3u8Url);
            }
        } catch (Exception e) {
            logger.error("Failed to parse M3U8: {}", e.getMessage());
            return null;
        }
    }
    
    private static String parseM3u8Content(String content, String baseUrl) {
        // Look for direct video URLs in the playlist
        Matcher matcher = M3U8_URL_PATTERN.matcher(content);
        
        String bestUrl = null;
        while (matcher.find()) {
            String url = matcher.group(1);
            
            // Prefer .mp4 over .m4s over .ts
            if (url.endsWith(".mp4")) {
                return url;
            } else if (url.endsWith(".m4s") && (bestUrl == null || !bestUrl.endsWith(".mp4"))) {
                bestUrl = url;
            } else if (url.endsWith(".ts") && bestUrl == null) {
                bestUrl = url;
            }
        }
        
        // If no absolute URLs found, look for relative URLs and construct them
        if (bestUrl == null) {
            Pattern relativePattern = Pattern.compile("^([^#\\s]+\\.(m4s|mp4|ts))$", Pattern.MULTILINE);
            Matcher relativeMatcher = relativePattern.matcher(content);
            
            String baseUrlPrefix = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1);
            
            while (relativeMatcher.find()) {
                String relativeUrl = relativeMatcher.group(1);
                String fullUrl = baseUrlPrefix + relativeUrl;
                
                if (relativeUrl.endsWith(".mp4")) {
                    return fullUrl;
                } else if (relativeUrl.endsWith(".m4s") && (bestUrl == null || !bestUrl.endsWith(".mp4"))) {
                    bestUrl = fullUrl;
                } else if (relativeUrl.endsWith(".ts") && bestUrl == null) {
                    bestUrl = fullUrl;
                }
            }
        }
        
        return bestUrl;
    }
    
    /**
     * Convert RedGifs ID to potential M3U8 URL
     */
    public static String buildRedGifsM3u8Url(String gifId) {
        return "https://api.redgifs.com/v2/gifs/" + gifId.toLowerCase() + "/sd.m3u8";
    }
    
    /**
     * Extract RedGifs ID from watch URL
     */
    public static String extractGifId(String watchUrl) {
        if (watchUrl.contains("/watch/") || watchUrl.contains("/ifr/")) {
            return watchUrl.substring(watchUrl.lastIndexOf("/") + 1);
        }
        return null;
    }
}