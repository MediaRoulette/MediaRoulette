package me.hash.mediaroulette.utils.media.ffmpeg;

import me.hash.mediaroulette.utils.media.ffmpeg.config.FFmpegConfig;
import me.hash.mediaroulette.utils.media.ffmpeg.processors.VideoProcessor;
import me.hash.mediaroulette.utils.media.ffmpeg.processors.ThumbnailProcessor;
import me.hash.mediaroulette.utils.media.ffmpeg.processors.GifProcessor;
import me.hash.mediaroulette.utils.media.ffmpeg.resolvers.UrlResolverFactory;
import me.hash.mediaroulette.utils.media.ffmpeg.models.VideoInfo;
import me.hash.mediaroulette.utils.media.ffmpeg.utils.AdaptiveDomainTracker;
import me.hash.mediaroulette.utils.media.FFmpegDownloader;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Main entry point for FFmpeg operations with adaptive fallback support.
 * Provides high-level operations for video processing, thumbnail extraction,
 * color analysis, and GIF creation.
 */
public class FFmpegService {
    private final FFmpegConfig config;
    private final VideoProcessor videoProcessor;
    private final ThumbnailProcessor thumbnailProcessor;
    private final GifProcessor gifProcessor;
    private final UrlResolverFactory urlResolverFactory;
    
    /**
     * Creates an FFmpegService with default configuration.
     */
    public FFmpegService() {
        this(FFmpegConfig.defaults());
    }
    
    /**
     * Creates an FFmpegService with custom configuration.
     */
    public FFmpegService(FFmpegConfig config) {
        this.config = config;
        this.videoProcessor = new VideoProcessor(config);
        this.thumbnailProcessor = new ThumbnailProcessor(config);
        this.gifProcessor = new GifProcessor(config);
        this.urlResolverFactory = new UrlResolverFactory();
    }
    
    /**
     * Creates an FFmpegService using Builder for fluent configuration.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    // === URL Resolution ===
    
    /**
     * Resolves a video URL (handles platforms like RedGifs, Gfycat, etc.)
     */
    public CompletableFuture<String> resolveVideoUrl(String url) {
        return urlResolverFactory.getResolver(url).resolve(url);
    }
    
    // === Video Information ===
    
    /**
     * Gets detailed video information for a URL.
     */
    public CompletableFuture<VideoInfo> getVideoInfo(String videoUrl) {
        return resolveVideoUrl(videoUrl).thenCompose(videoProcessor::getVideoInfo);
    }
    
    // === Thumbnail Extraction ===
    
    /**
     * Extracts a single thumbnail at the specified timestamp.
     */
    public CompletableFuture<BufferedImage> extractThumbnail(String videoUrl, double timestampSeconds) {
        return resolveVideoUrl(videoUrl)
                .thenCompose(url -> thumbnailProcessor.extractThumbnail(url, timestampSeconds));
    }
    
    /**
     * Extracts thumbnails at multiple timestamps.
     */
    public CompletableFuture<List<BufferedImage>> extractMultipleThumbnails(String videoUrl, double[] timestamps) {
        return resolveVideoUrl(videoUrl)
                .thenCompose(url -> thumbnailProcessor.extractMultipleThumbnails(url, timestamps));
    }
    
    // === Color Extraction ===
    
    /**
     * Extracts the dominant color from a video/media URL.
     * Uses multi-strategy approach with automatic fallback.
     */
    public CompletableFuture<Color> extractDominantColor(String videoUrl) {
        return resolveVideoUrl(videoUrl)
                .thenCompose(url -> getVideoInfo(url)
                        .thenCompose(info -> thumbnailProcessor.extractDominantColor(url, info)));
    }
    
    // === GIF Creation ===
    
    /**
     * Creates an optimized GIF from a video with smart parameters.
     */
    public CompletableFuture<Path> createSmartGif(String videoUrl) {
        return resolveVideoUrl(videoUrl)
                .thenCompose(url -> getVideoInfo(url)
                        .thenCompose(info -> gifProcessor.createSmartGif(url, info)));
    }
    
    /**
     * Creates a GIF with custom parameters.
     */
    public CompletableFuture<Path> createGif(String videoUrl, double startTime, double duration, int width, int height) {
        return resolveVideoUrl(videoUrl)
                .thenCompose(url -> gifProcessor.createGif(url, startTime, duration, width, height));
    }
    
    /**
     * Creates a Discord-optimized GIF (respects file size limits).
     */
    public CompletableFuture<net.dv8tion.jda.api.utils.FileUpload> createGifUpload(String videoUrl) {
        return resolveVideoUrl(videoUrl)
                .thenCompose(url -> getVideoInfo(url)
                        .thenCompose(info -> gifProcessor.createDiscordOptimizedGif(url, info))
                        .thenApply(gifProcessor::createFileUpload));
    }
    
    /**
     * Creates a video preview GIF.
     */
    public CompletableFuture<Path> createVideoPreviewGif(String videoUrl) {
        return resolveVideoUrl(videoUrl).thenCompose(gifProcessor::createVideoPreviewGif);
    }
    
    // === URL Utilities ===
    
    /**
     * Checks if a URL points to a video.
     */
    public boolean isVideoUrl(String url) {
        return urlResolverFactory.isVideoUrl(url);
    }
    
    /**
     * Checks if a URL should be converted to GIF.
     */
    public boolean shouldConvertToGif(String url) {
        return urlResolverFactory.shouldConvertToGif(url);
    }
    
    /**
     * Gets a preview image URL for a video platform.
     */
    public String getVideoPreviewUrl(String videoUrl) {
        return urlResolverFactory.getVideoPreviewUrl(videoUrl);
    }
    
    // === Maintenance ===
    
    /**
     * Cleans up temporary files.
     */
    public void cleanupTempFiles() {
        config.getFileManager().cleanupTempFiles();
    }
    
    /**
     * Clears the adaptive domain tracker (resets learned behaviors).
     */
    public void clearDomainTracker() {
        AdaptiveDomainTracker.getInstance().clear();
    }
    
    /**
     * Gets statistics for a domain's access pattern.
     */
    public AdaptiveDomainTracker.DomainStats getDomainStats(String url) {
        return AdaptiveDomainTracker.getInstance().getStats(url);
    }
    
    /**
     * Checks if FFmpeg is ready (downloaded or available in PATH).
     */
    public CompletableFuture<Boolean> isReady() {
        return FFmpegDownloader.getFFmpegPath()
                .thenCompose(ffmpeg -> FFmpegDownloader.getFFprobePath()
                        .thenApply(ffprobe -> config.getFileManager().pathExists(ffmpeg) &&
                                config.getFileManager().pathExists(ffprobe)));
    }
    
    /**
     * Gets the configuration being used.
     */
    public FFmpegConfig getConfig() {
        return config;
    }
    
    /**
     * Builder for FFmpegService with fluent configuration.
     */
    public static class Builder {
        private FFmpegConfig.Builder configBuilder = FFmpegConfig.builder();
        
        public Builder defaultTimeoutSeconds(int seconds) {
            configBuilder.defaultTimeoutSeconds(seconds);
            return this;
        }
        
        public Builder thumbnailTimeoutSeconds(int seconds) {
            configBuilder.thumbnailTimeoutSeconds(seconds);
            return this;
        }
        
        public Builder maxRetries(int retries) {
            configBuilder.maxRetries(retries);
            return this;
        }
        
        public Builder enableAdaptiveDownload(boolean enable) {
            configBuilder.enableAdaptiveDownload(enable);
            return this;
        }
        
        public Builder tempDirectory(String dir) {
            configBuilder.tempDirectory(dir);
            return this;
        }
        
        public Builder config(FFmpegConfig config) {
            return new Builder() {
                @Override
                public FFmpegService build() {
                    return new FFmpegService(config);
                }
            };
        }
        
        public FFmpegService build() {
            return new FFmpegService(configBuilder.build());
        }
    }
}