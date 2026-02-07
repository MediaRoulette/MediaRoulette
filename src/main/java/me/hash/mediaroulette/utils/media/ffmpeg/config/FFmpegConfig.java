package me.hash.mediaroulette.utils.media.ffmpeg.config;

import me.hash.mediaroulette.utils.media.ffmpeg.utils.FileManager;
import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

/**
 * Configuration for FFmpeg operations with builder pattern for flexibility.
 * Supports customizable timeouts, dimensions, and HTTP settings.
 */
public class FFmpegConfig {
    
    // Timeout configurations
    private final int defaultTimeoutSeconds;
    private final int gifCreationTimeoutSeconds;
    private final int thumbnailTimeoutSeconds;
    private final int videoInfoTimeoutSeconds;
    private final int downloadTimeoutSeconds;
    
    // GIF configurations
    private final int defaultGifWidth;
    private final int defaultGifHeight;
    private final int defaultGifFps;
    private final double maxGifDuration;
    
    // Discord limits
    private final long maxDiscordFileSize;
    private final long maxDiscordFileSizePremium;
    
    // File management
    private final String tempDirectory;
    private final FileManager fileManager;
    
    // HTTP settings
    private final HttpSettings httpSettings;
    private final OkHttpClient httpClient;
    
    // Retry settings
    private final int maxRetries;
    private final long retryDelayMs;
    private final boolean enableAdaptiveDownload;
    
    private FFmpegConfig(Builder builder) {
        this.defaultTimeoutSeconds = builder.defaultTimeoutSeconds;
        this.gifCreationTimeoutSeconds = builder.gifCreationTimeoutSeconds;
        this.thumbnailTimeoutSeconds = builder.thumbnailTimeoutSeconds;
        this.videoInfoTimeoutSeconds = builder.videoInfoTimeoutSeconds;
        this.downloadTimeoutSeconds = builder.downloadTimeoutSeconds;
        
        this.defaultGifWidth = builder.defaultGifWidth;
        this.defaultGifHeight = builder.defaultGifHeight;
        this.defaultGifFps = builder.defaultGifFps;
        this.maxGifDuration = builder.maxGifDuration;
        
        this.maxDiscordFileSize = builder.maxDiscordFileSize;
        this.maxDiscordFileSizePremium = builder.maxDiscordFileSizePremium;
        
        this.tempDirectory = builder.tempDirectory;
        this.fileManager = new FileManager(tempDirectory);
        
        this.httpSettings = builder.httpSettings;
        this.httpClient = createHttpClient();
        
        this.maxRetries = builder.maxRetries;
        this.retryDelayMs = builder.retryDelayMs;
        this.enableAdaptiveDownload = builder.enableAdaptiveDownload;
    }
    
    private OkHttpClient createHttpClient() {
        return new OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(httpSettings.getConnectTimeoutSeconds(), TimeUnit.SECONDS)
                .readTimeout(httpSettings.getReadTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(httpSettings.getWriteTimeoutSeconds(), TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Creates a default configuration
     */
    public static FFmpegConfig defaults() {
        return new Builder().build();
    }
    
    /**
     * Creates a new builder for custom configuration
     */
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public int getDefaultTimeoutSeconds() { return defaultTimeoutSeconds; }
    public int getGifCreationTimeoutSeconds() { return gifCreationTimeoutSeconds; }
    public int getThumbnailTimeoutSeconds() { return thumbnailTimeoutSeconds; }
    public int getVideoInfoTimeoutSeconds() { return videoInfoTimeoutSeconds; }
    public int getDownloadTimeoutSeconds() { return downloadTimeoutSeconds; }
    
    public int getDefaultGifWidth() { return defaultGifWidth; }
    public int getDefaultGifHeight() { return defaultGifHeight; }
    public int getDefaultGifFps() { return defaultGifFps; }
    public double getMaxGifDuration() { return maxGifDuration; }
    
    public long getMaxDiscordFileSize() { return maxDiscordFileSize; }
    public long getMaxDiscordFileSizePremium() { return maxDiscordFileSizePremium; }
    
    public String getTempDirectory() { return tempDirectory; }
    public FileManager getFileManager() { return fileManager; }
    
    public HttpSettings getHttpSettings() { return httpSettings; }
    public OkHttpClient getHttpClient() { return httpClient; }
    
    public int getMaxRetries() { return maxRetries; }
    public long getRetryDelayMs() { return retryDelayMs; }
    public boolean isAdaptiveDownloadEnabled() { return enableAdaptiveDownload; }
    
    /**
     * Builder for FFmpegConfig
     */
    public static class Builder {
        private int defaultTimeoutSeconds = 15;
        private int gifCreationTimeoutSeconds = 60;
        private int thumbnailTimeoutSeconds = 10;
        private int videoInfoTimeoutSeconds = 8;
        private int downloadTimeoutSeconds = 30;
        
        private int defaultGifWidth = 480;
        private int defaultGifHeight = 270;
        private int defaultGifFps = 10;
        private double maxGifDuration = 20.0;
        
        private long maxDiscordFileSize = 25 * 1024 * 1024L; // 25 MB
        private long maxDiscordFileSizePremium = 500 * 1024 * 1024L; // 500 MB
        
        private String tempDirectory = "temp";
        private HttpSettings httpSettings = HttpSettings.defaults();
        
        private int maxRetries = 2;
        private long retryDelayMs = 500;
        private boolean enableAdaptiveDownload = true;
        
        public Builder defaultTimeoutSeconds(int seconds) {
            this.defaultTimeoutSeconds = seconds;
            return this;
        }
        
        public Builder gifCreationTimeoutSeconds(int seconds) {
            this.gifCreationTimeoutSeconds = seconds;
            return this;
        }
        
        public Builder thumbnailTimeoutSeconds(int seconds) {
            this.thumbnailTimeoutSeconds = seconds;
            return this;
        }
        
        public Builder videoInfoTimeoutSeconds(int seconds) {
            this.videoInfoTimeoutSeconds = seconds;
            return this;
        }
        
        public Builder downloadTimeoutSeconds(int seconds) {
            this.downloadTimeoutSeconds = seconds;
            return this;
        }
        
        public Builder defaultGifWidth(int width) {
            this.defaultGifWidth = width;
            return this;
        }
        
        public Builder defaultGifHeight(int height) {
            this.defaultGifHeight = height;
            return this;
        }
        
        public Builder defaultGifFps(int fps) {
            this.defaultGifFps = fps;
            return this;
        }
        
        public Builder maxGifDuration(double duration) {
            this.maxGifDuration = duration;
            return this;
        }
        
        public Builder maxDiscordFileSize(long size) {
            this.maxDiscordFileSize = size;
            return this;
        }
        
        public Builder maxDiscordFileSizePremium(long size) {
            this.maxDiscordFileSizePremium = size;
            return this;
        }
        
        public Builder tempDirectory(String directory) {
            this.tempDirectory = directory;
            return this;
        }
        
        public Builder httpSettings(HttpSettings settings) {
            this.httpSettings = settings;
            return this;
        }
        
        public Builder maxRetries(int retries) {
            this.maxRetries = retries;
            return this;
        }
        
        public Builder retryDelayMs(long delayMs) {
            this.retryDelayMs = delayMs;
            return this;
        }
        
        public Builder enableAdaptiveDownload(boolean enable) {
            this.enableAdaptiveDownload = enable;
            return this;
        }
        
        public FFmpegConfig build() {
            return new FFmpegConfig(this);
        }
    }
}