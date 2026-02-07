package me.hash.mediaroulette.utils.media.ffmpeg.config;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP settings for FFmpeg operations including headers, timeouts, and user agent.
 * These settings are used when FFmpeg/FFprobe makes HTTP requests.
 */
public class HttpSettings {
    
    private final String userAgent;
    private final int connectTimeoutSeconds;
    private final int readTimeoutSeconds;
    private final int writeTimeoutSeconds;
    private final Map<String, String> defaultHeaders;
    
    private HttpSettings(Builder builder) {
        this.userAgent = builder.userAgent;
        this.connectTimeoutSeconds = builder.connectTimeoutSeconds;
        this.readTimeoutSeconds = builder.readTimeoutSeconds;
        this.writeTimeoutSeconds = builder.writeTimeoutSeconds;
        this.defaultHeaders = new HashMap<>(builder.defaultHeaders);
    }
    
    /**
     * Creates default HTTP settings with browser-like User-Agent
     */
    public static HttpSettings defaults() {
        return new Builder().build();
    }
    
    /**
     * Creates a new builder
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public String getUserAgent() { return userAgent; }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
    public int getWriteTimeoutSeconds() { return writeTimeoutSeconds; }
    public Map<String, String> getDefaultHeaders() { return new HashMap<>(defaultHeaders); }
    
    /**
     * Builds FFmpeg input arguments with HTTP headers.
     * Returns arguments like: -headers "User-Agent: ..." -i url
     */
    public String[] buildFFmpegInputArgs(String url) {
        StringBuilder headers = new StringBuilder();
        headers.append("User-Agent: ").append(userAgent).append("\r\n");
        headers.append("Accept: */*\r\n");
        
        // Add referer based on URL domain
        String referer = extractReferer(url);
        if (referer != null) {
            headers.append("Referer: ").append(referer).append("\r\n");
        }
        
        for (Map.Entry<String, String> entry : defaultHeaders.entrySet()) {
            headers.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }
        
        return new String[]{
            "-headers", headers.toString(),
            "-i", url
        };
    }
    
    /**
     * Builds FFprobe arguments with proper headers.
     */
    public String[] buildFFprobeArgs(String url) {
        StringBuilder headers = new StringBuilder();
        headers.append("User-Agent: ").append(userAgent).append("\r\n");
        
        String referer = extractReferer(url);
        if (referer != null) {
            headers.append("Referer: ").append(referer).append("\r\n");
        }
        
        return new String[]{
            "-headers", headers.toString()
        };
    }
    
    private String extractReferer(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            return uri.getScheme() + "://" + uri.getHost() + "/";
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Builder for HttpSettings
     */
    public static class Builder {
        private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        private int connectTimeoutSeconds = 10;
        private int readTimeoutSeconds = 30;
        private int writeTimeoutSeconds = 30;
        private Map<String, String> defaultHeaders = new HashMap<>();
        
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }
        
        public Builder connectTimeoutSeconds(int seconds) {
            this.connectTimeoutSeconds = seconds;
            return this;
        }
        
        public Builder readTimeoutSeconds(int seconds) {
            this.readTimeoutSeconds = seconds;
            return this;
        }
        
        public Builder writeTimeoutSeconds(int seconds) {
            this.writeTimeoutSeconds = seconds;
            return this;
        }
        
        public Builder addHeader(String name, String value) {
            this.defaultHeaders.put(name, value);
            return this;
        }
        
        public Builder headers(Map<String, String> headers) {
            this.defaultHeaders = new HashMap<>(headers);
            return this;
        }
        
        public HttpSettings build() {
            return new HttpSettings(this);
        }
    }
}
