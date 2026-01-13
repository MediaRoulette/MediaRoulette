package me.hash.mediaroulette.utils.media.ffmpeg.resolvers.impl;

import me.hash.mediaroulette.utils.browser.RateLimiter;
import me.hash.mediaroulette.utils.media.ffmpeg.resolvers.UrlResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolver for RedGifs URLs that converts watch/ifr URLs to direct video URLs.
 * Handles token caching, rate limiting, and graceful fallback.
 */
public class RedGifsResolver implements UrlResolver {
    private static final Logger logger = LoggerFactory.getLogger(RedGifsResolver.class);
    
    private static final String API_URL = "https://api.redgifs.com";
    private static final Pattern REDGIFS_PATTERN = Pattern.compile(
            "(?:https?://)?(?:www\\.|v3\\.)?redgifs\\.com/(?:watch|ifr)/([a-zA-Z0-9]+)",
            Pattern.CASE_INSENSITIVE
    );
    
    // Token cache - shared across all instances
    private static final AtomicReference<CachedToken> tokenCache = new AtomicReference<>();
    private static final long TOKEN_CACHE_DURATION_MS = 60 * 60 * 1000; // 1 hour
    
    private final HttpClient httpClient;
    
    public RedGifsResolver() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
    
    @Override
    public boolean canResolve(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        return REDGIFS_PATTERN.matcher(url).find();
    }
    
    @Override
    public CompletableFuture<String> resolve(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check rate limit
                if (!RateLimiter.isRequestAllowed("redgifs", "system")) {
                    logger.warn("RedGifs rate limit exceeded, returning original URL");
                    return url;
                }
                
                // Extract GIF ID from URL
                String gifId = extractGifId(url);
                if (gifId == null) {
                    logger.warn("Could not extract GIF ID from URL: {}", url);
                    return url;
                }
                
                // Get access token (cached)
                String token = getAccessToken();
                if (token == null) {
                    logger.warn("Could not get RedGifs access token, returning original URL");
                    return url;
                }
                
                // Fetch GIF data
                String directUrl = fetchDirectUrl(gifId, token);
                if (directUrl != null) {
                    logger.debug("Resolved RedGifs URL {} -> {}", url, directUrl);
                    return directUrl;
                }
                
                logger.warn("Could not resolve RedGifs URL: {}", url);
                return url;
                
            } catch (Exception e) {
                logger.error("Error resolving RedGifs URL: {} - {}", url, e.getMessage());
                return url; // Graceful fallback
            }
        });
    }
    
    @Override
    public int getPriority() {
        return 10; // Higher priority than DirectUrlResolver (-1)
    }
    
    /**
     * Extracts the GIF ID from a RedGifs URL.
     */
    String extractGifId(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = REDGIFS_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase();
        }
        return null;
    }
    
    /**
     * Gets an access token, using cache if available.
     */
    private String getAccessToken() {
        CachedToken cached = tokenCache.get();
        if (cached != null && !cached.isExpired()) {
            return cached.token;
        }
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "/v2/auth/temporary"))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String body = response.body();
                // Parse token from JSON response: {"token":"..."}
                String token = extractJsonValue(body, "token");
                if (token != null) {
                    tokenCache.set(new CachedToken(token, System.currentTimeMillis() + TOKEN_CACHE_DURATION_MS));
                    logger.debug("Obtained new RedGifs access token");
                    return token;
                }
            } else if (response.statusCode() == 429) {
                logger.warn("RedGifs API rate limited (429)");
                RateLimiter.triggerRateLimit("redgifs", 60);
            } else {
                logger.warn("Failed to get RedGifs token: HTTP {}", response.statusCode());
            }
        } catch (Exception e) {
            logger.error("Error getting RedGifs access token: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Fetches the direct video URL for a GIF ID.
     */
    private String fetchDirectUrl(String gifId, String token) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + "/v2/gifs/" + gifId))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                String body = response.body();
                
                // Check for error in response
                if (body.contains("\"error\"")) {
                    String errorDesc = extractJsonValue(body, "description");
                    logger.warn("RedGifs API error for {}: {}", gifId, errorDesc);
                    return null;
                }
                
                // Try to get HD URL first, then SD
                String hdUrl = extractNestedJsonValue(body, "urls", "hd");
                if (hdUrl != null && !hdUrl.isEmpty()) {
                    return hdUrl;
                }
                
                String sdUrl = extractNestedJsonValue(body, "urls", "sd");
                if (sdUrl != null && !sdUrl.isEmpty()) {
                    return sdUrl;
                }
                
                logger.warn("No video URLs found in RedGifs response for: {}", gifId);
            } else if (response.statusCode() == 404) {
                logger.warn("RedGifs GIF not found: {}", gifId);
            } else if (response.statusCode() == 429) {
                logger.warn("RedGifs API rate limited (429)");
                RateLimiter.triggerRateLimit("redgifs", 60);
            } else {
                logger.warn("Failed to fetch RedGifs data: HTTP {}", response.statusCode());
            }
        } catch (Exception e) {
            logger.error("Error fetching RedGifs data for {}: {}", gifId, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Simple JSON value extractor (avoids adding JSON dependency).
     * Extracts the value of a top-level string field.
     */
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int start = json.indexOf(searchKey);
        if (start == -1) return null;
        
        start += searchKey.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        
        return json.substring(start, end);
    }
    
    /**
     * Extracts a nested JSON value like {"gif":{"urls":{"hd":"..."}}}
     */
    private String extractNestedJsonValue(String json, String parentKey, String childKey) {
        // Find the parent object
        String parentSearch = "\"" + parentKey + "\":{";
        int parentStart = json.indexOf(parentSearch);
        if (parentStart == -1) {
            // Try alternative format: "urls":{"hd":
            parentSearch = "\"" + parentKey + "\":{";
            parentStart = json.indexOf(parentSearch);
            if (parentStart == -1) return null;
        }
        
        // Search for child key within the rest of the string
        String childSearch = "\"" + childKey + "\":\"";
        int childStart = json.indexOf(childSearch, parentStart);
        if (childStart == -1) return null;
        
        childStart += childSearch.length();
        int childEnd = json.indexOf("\"", childStart);
        if (childEnd == -1) return null;
        
        return json.substring(childStart, childEnd);
    }
    
    /**
     * Cached token with expiration.
     */
    private static class CachedToken {
        final String token;
        final long expiresAt;
        
        CachedToken(String token, long expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() >= expiresAt;
        }
    }
}
