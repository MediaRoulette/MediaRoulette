package me.hash.mediaroulette.content.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP client wrapper with rate limiting and error handling
 */
public class HttpClientWrapper {
    
    private final HttpClient httpClient;
    private final ConcurrentHashMap<String, AtomicLong> rateLimitMap;
    private static final long DEFAULT_RATE_LIMIT_DELAY = 100;
    
    public HttpClientWrapper() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
        this.rateLimitMap = new ConcurrentHashMap<>();
    }
    
    /**
     * Send a GET request with rate limiting
     */
    public HttpResponse<String> get(String url) throws IOException, InterruptedException, RateLimitException {
        return sendRequest(HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(15))
            .GET()
            .build());
    }
    
    /**
     * Send a GET request with rate limiting (legacy method for compatibility)
     */
    public String get(String url, String source, Object unused) throws IOException, InterruptedException, RateLimitException {
        return get(url).body();
    }
    
    /**
     * Get response body as string (convenience method)
     */
    public String getBody(String url) throws IOException, InterruptedException, RateLimitException {
        return get(url).body();
    }
    
    /**
     * Send a POST request with rate limiting
     */
    public HttpResponse<String> post(String url, String body) throws IOException, InterruptedException, RateLimitException {
        return sendRequest(HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build());
    }
    
    /**
     * Send HTTP request with rate limiting and redirect handling
     */
    private HttpResponse<String> sendRequest(HttpRequest request) throws IOException, InterruptedException, RateLimitException {
        String domain = request.uri().getHost();

        AtomicLong lastRequest = rateLimitMap.computeIfAbsent(domain, k -> new AtomicLong(0));
        long now = System.currentTimeMillis();
        long timeSinceLastRequest = now - lastRequest.get();

        if (timeSinceLastRequest < DEFAULT_RATE_LIMIT_DELAY) {
            long waitTime = DEFAULT_RATE_LIMIT_DELAY - timeSinceLastRequest;
            Thread.sleep(waitTime);
        }

        lastRequest.set(now);
        
        // Add user agent to avoid 403 errors (excluding restricted headers)
        HttpRequest requestWithHeaders = HttpRequest.newBuilder()
            .uri(request.uri())
            .timeout(request.timeout().orElse(Duration.ofSeconds(30)))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.5")
            .method(request.method(), request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()))
            .build();
        
        HttpResponse<String> response = httpClient.send(requestWithHeaders, HttpResponse.BodyHandlers.ofString());
        
        // Handle HTTP error codes
        if (response.statusCode() >= 400) {
            if (response.statusCode() == 429) {
                throw new RateLimitException("Server rate limit exceeded for " + domain);
            }
            throw new IOException("HTTP " + response.statusCode() + " error for " + request.uri());
        }
        
        return response;
    }
    
    /**
     * Get the final URL after following redirects
     */
    public String getFinalUrl(String url) throws IOException, InterruptedException, RateLimitException {
        HttpResponse<String> response = get(url);
        return response.uri().toString();
    }
    
    /**
     * Get response with redirect information (without following redirects)
     */
    public HttpResponse<String> getWithoutRedirects(String url) throws IOException, InterruptedException, RateLimitException {
        String domain = url.substring(url.indexOf("://") + 3, url.indexOf("/", url.indexOf("://") + 3));
        
        // Check rate limit
        AtomicLong lastRequest = rateLimitMap.computeIfAbsent(domain, k -> new AtomicLong(0));
        long now = System.currentTimeMillis();
        long timeSinceLastRequest = now - lastRequest.get();
        
        if (timeSinceLastRequest < DEFAULT_RATE_LIMIT_DELAY) {
            long waitTime = DEFAULT_RATE_LIMIT_DELAY - timeSinceLastRequest;
            throw new RateLimitException("Rate limit exceeded for " + domain + ". Wait " + waitTime + "ms");
        }
        
        lastRequest.set(now);
        
        // Create a client that doesn't follow redirects
        HttpClient noRedirectClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.5")
            .GET()
            .build();
        
        return noRedirectClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
    
    /**
     * Custom exception for rate limiting
     */
    public static class RateLimitException extends Exception {
        public RateLimitException(String message) {
            super(message);
        }
        
        public String getUserFriendlyMessage() {
            return "Rate limit exceeded. Please try again later.";
        }
    }
}