package me.hash.mediaroulette.utils.browser;

import me.hash.mediaroulette.utils.ErrorReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Global rate limiter for all API sources
 */
public class RateLimiter {
    private static final Logger logger = LoggerFactory.getLogger(RateLimiter.class);

    // Rate limit tracking per source
    private static final ConcurrentHashMap<String, SourceRateLimit> rateLimits = new ConcurrentHashMap<>();
    
    // Default rate limits per source (requests per minute)
    private static final ConcurrentHashMap<String, Integer> defaultLimits = new ConcurrentHashMap<>();
    
    static {
        // Initialize default rate limits for each source
        defaultLimits.put("reddit", 60);      // Reddit API limit
        defaultLimits.put("4chan", 60);       // 4chan API limit
        defaultLimits.put("tenor", 50);       // Tenor API limit
        defaultLimits.put("google", 100);     // Google Custom Search limit
        defaultLimits.put("youtube", 100);    // YouTube API limit
        defaultLimits.put("tmdb", 40);        // TMDB API limit
        defaultLimits.put("imgur", 50);       // Imgur API limit
        defaultLimits.put("rule34", 0);      // Rule34 API limit
        defaultLimits.put("picsum", 100);     // Picsum has no strict limits
        defaultLimits.put("urban", 60);       // Urban Dictionary limit
    }
    
    /**
     * Check if a request is allowed for the given source
     * @param source The API source name
     * @param userId The user making the request (for logging)
     * @return true if request is allowed, false if rate limited
     */
    public static boolean isRequestAllowed(String source, String userId) {
        SourceRateLimit rateLimit = rateLimits.computeIfAbsent(source, 
            k -> new SourceRateLimit(defaultLimits.getOrDefault(k, 60)));
        
        boolean allowed = rateLimit.tryRequest();
        
        if (!allowed) {
            // Report rate limit hit to monitoring
            ErrorReporter.reportProviderError(source, "rate limit", 
                "Rate limit exceeded", userId);
        }
        
        return allowed;
    }
    
    /**
     * Get the time until the rate limit resets for a source
     * @param source The API source name
     * @return seconds until reset, or 0 if not rate limited
     */
    public static long getTimeUntilReset(String source) {
        SourceRateLimit rateLimit = rateLimits.get(source);
        if (rateLimit == null) {
            return 0;
        }
        return rateLimit.getTimeUntilReset();
    }
    
    /**
     * Get the current request count for a source
     * @param source The API source name
     * @return current request count in the current window
     */
    public static int getCurrentRequestCount(String source) {
        SourceRateLimit rateLimit = rateLimits.get(source);
        if (rateLimit == null) {
            return 0;
        }
        return rateLimit.getCurrentCount();
    }
    
    /**
     * Get the rate limit for a source
     * @param source The API source name
     * @return the rate limit (requests per minute)
     */
    public static int getRateLimit(String source) {
        return defaultLimits.getOrDefault(source, 60);
    }
    
    /**
     * Manually trigger a rate limit for a source (for testing or emergency)
     * @param source The API source name
     * @param durationSeconds How long to rate limit for
     */
    public static void triggerRateLimit(String source, int durationSeconds) {
        SourceRateLimit rateLimit = rateLimits.computeIfAbsent(source, 
            k -> new SourceRateLimit(defaultLimits.getOrDefault(k, 60)));
        rateLimit.triggerManualLimit(durationSeconds);

        ErrorReporter.reportWarning("Manual rate limit",
                "Manual rate limit triggered for source: " + source + ", duration: " + durationSeconds +"s",
                "", source, "global");
    }
    
    /**
     * Reset rate limits for a source (admin function)
     * @param source The API source name
     */
    public static void resetRateLimit(String source) {
        rateLimits.remove(source);
        ErrorReporter.reportInfo("Manual rate removed",
                "Manual rate limit removed for source: " + source + ".",
                "", source, "global");    }
    
    /**
     * Get rate limit status for all sources
     * @return Map of source -> status info
     */
    public static ConcurrentHashMap<String, String> getAllRateLimitStatus() {
        ConcurrentHashMap<String, String> status = new ConcurrentHashMap<>();
        
        for (String source : defaultLimits.keySet()) {
            SourceRateLimit rateLimit = rateLimits.get(source);
            if (rateLimit == null) {
                status.put(source, "OK (0/" + defaultLimits.get(source) + ")");
            } else {
                int current = rateLimit.getCurrentCount();
                int limit = defaultLimits.get(source);
                long resetTime = rateLimit.getTimeUntilReset();
                
                if (current >= limit) {
                    status.put(source, String.format("RATE LIMITED (reset in %ds)", resetTime));
                } else {
                    status.put(source, String.format("OK (%d/%d)", current, limit));
                }
            }
        }
        
        return status;
    }
    
    /**
     * Inner class to track rate limits for a specific source
     */
    private static class SourceRateLimit {
        private final int maxRequests;
        private final long windowSizeMs = TimeUnit.MINUTES.toMillis(1); // 1 minute window
        
        private volatile int requestCount = 0;
        private volatile long windowStart = System.currentTimeMillis();
        private volatile long manualLimitUntil = 0; // Manual rate limit override
        
        public SourceRateLimit(int maxRequests) {
            this.maxRequests = maxRequests;
        }
        
        public synchronized boolean tryRequest() {
            long now = System.currentTimeMillis();
            
            // Check manual rate limit first
            if (manualLimitUntil > now) {
                return false;
            }
            
            // Reset window if needed
            if (now - windowStart >= windowSizeMs) {
                requestCount = 0;
                windowStart = now;
            }
            
            // Check if we can make the request
            if (requestCount >= maxRequests) {
                return false;
            }
            
            requestCount++;
            return true;
        }
        
        public long getTimeUntilReset() {
            long now = System.currentTimeMillis();
            
            // Check manual limit first
            if (manualLimitUntil > now) {
                return (manualLimitUntil - now) / 1000;
            }
            
            // Check window reset
            long windowEnd = windowStart + windowSizeMs;
            if (now >= windowEnd) {
                return 0; // Window has already reset
            }
            
            return (windowEnd - now) / 1000;
        }
        
        public int getCurrentCount() {
            long now = System.currentTimeMillis();
            
            // Reset window if needed
            if (now - windowStart >= windowSizeMs) {
                requestCount = 0;
                windowStart = now;
            }
            
            return requestCount;
        }
        
        public void triggerManualLimit(int durationSeconds) {
            this.manualLimitUntil = System.currentTimeMillis() + (durationSeconds * 1000L);
        }
    }
}