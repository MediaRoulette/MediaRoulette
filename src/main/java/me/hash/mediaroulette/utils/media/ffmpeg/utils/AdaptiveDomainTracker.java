package me.hash.mediaroulette.utils.media.ffmpeg.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adaptive tracker that learns which domains need download-first approach.
 * Instead of hardcoding domains, this tracks failure patterns and automatically
 * switches to download-first for problematic domains.
 */
public class AdaptiveDomainTracker {
    private static final Logger logger = LoggerFactory.getLogger(AdaptiveDomainTracker.class);
    
    // Singleton instance
    private static final AdaptiveDomainTracker INSTANCE = new AdaptiveDomainTracker();
    
    // Domain -> failure count (direct access attempts that failed)
    private final Map<String, AtomicInteger> directFailures = new ConcurrentHashMap<>();
    
    // Domain -> success count (direct access attempts that succeeded)  
    private final Map<String, AtomicInteger> directSuccesses = new ConcurrentHashMap<>();
    
    // Threshold: if a domain has this many more failures than successes, prefer download-first
    private static final int FAILURE_THRESHOLD = 2;
    
    // Maximum entries to prevent memory bloat
    private static final int MAX_TRACKED_DOMAINS = 500;
    
    private AdaptiveDomainTracker() {}
    
    public static AdaptiveDomainTracker getInstance() {
        return INSTANCE;
    }
    
    /**
     * Checks whether download-first should be tried for this URL based on past behavior.
     * 
     * @param url The URL to check
     * @return true if download-first is recommended based on past failures
     */
    public boolean shouldDownloadFirst(String url) {
        String domain = extractDomain(url);
        if (domain == null) return false;
        
        int failures = directFailures.getOrDefault(domain, new AtomicInteger(0)).get();
        int successes = directSuccesses.getOrDefault(domain, new AtomicInteger(0)).get();
        
        // Prefer download-first if we've seen consistent failures
        return failures >= FAILURE_THRESHOLD && failures > successes;
    }
    
    /**
     * Records a successful direct access for a URL's domain.
     */
    public void recordDirectSuccess(String url) {
        String domain = extractDomain(url);
        if (domain == null) return;
        
        ensureCapacity();
        directSuccesses.computeIfAbsent(domain, k -> new AtomicInteger(0)).incrementAndGet();
        
        logger.debug("Recorded direct success for domain: {} (successes: {})", 
                domain, directSuccesses.get(domain).get());
    }
    
    /**
     * Records a failed direct access for a URL's domain.
     */
    public void recordDirectFailure(String url) {
        String domain = extractDomain(url);
        if (domain == null) return;
        
        ensureCapacity();
        int failures = directFailures.computeIfAbsent(domain, k -> new AtomicInteger(0)).incrementAndGet();
        
        logger.debug("Recorded direct failure for domain: {} (failures: {})", domain, failures);
        
        if (failures == FAILURE_THRESHOLD) {
            logger.info("Domain {} has reached failure threshold, will prefer download-first", domain);
        }
    }
    
    /**
     * Records success with download-first approach (confirms the domain is problematic for direct access).
     */
    public void recordDownloadFirstSuccess(String url) {
        // This further confirms the domain needs download-first
        String domain = extractDomain(url);
        if (domain == null) return;
        
        // Boost the failure count to reinforce the learning
        directFailures.computeIfAbsent(domain, k -> new AtomicInteger(0)).incrementAndGet();
        
        logger.debug("Download-first succeeded for domain: {}", domain);
    }
    
    /**
     * Gets statistics for a domain.
     */
    public DomainStats getStats(String url) {
        String domain = extractDomain(url);
        if (domain == null) return new DomainStats(0, 0, false);
        
        int failures = directFailures.getOrDefault(domain, new AtomicInteger(0)).get();
        int successes = directSuccesses.getOrDefault(domain, new AtomicInteger(0)).get();
        boolean downloadFirst = shouldDownloadFirst(url);
        
        return new DomainStats(successes, failures, downloadFirst);
    }
    
    /**
     * Clears all tracked data.
     */
    public void clear() {
        directFailures.clear();
        directSuccesses.clear();
        logger.info("Adaptive domain tracker cleared");
    }
    
    /**
     * Extracts domain from URL.
     */
    private String extractDomain(String url) {
        if (url == null || url.isEmpty()) return null;
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) return null;
            
            // Normalize: remove www. prefix for consistency
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            return host.toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Ensures we don't exceed maximum tracked domains to prevent memory issues.
     */
    private void ensureCapacity() {
        if (directFailures.size() + directSuccesses.size() > MAX_TRACKED_DOMAINS) {
            // Remove domains with lowest failure counts (least problematic)
            directFailures.entrySet().stream()
                    .filter(e -> e.getValue().get() < FAILURE_THRESHOLD)
                    .limit(MAX_TRACKED_DOMAINS / 4)
                    .forEach(e -> {
                        directFailures.remove(e.getKey());
                        directSuccesses.remove(e.getKey());
                    });
            
            logger.debug("Cleaned up domain tracker, current size: failures={}, successes={}",
                    directFailures.size(), directSuccesses.size());
        }
    }
    
    /**
     * Statistics for a domain's access patterns.
     */
    public record DomainStats(int successes, int failures, boolean recommendDownloadFirst) {
        @Override
        public String toString() {
            return String.format("DomainStats[successes=%d, failures=%d, downloadFirst=%s]",
                    successes, failures, recommendDownloadFirst);
        }
    }
}
