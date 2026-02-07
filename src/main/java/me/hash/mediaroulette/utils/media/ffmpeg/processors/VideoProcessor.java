package me.hash.mediaroulette.utils.media.ffmpeg.processors;

import me.hash.mediaroulette.utils.media.ffmpeg.config.FFmpegConfig;
import me.hash.mediaroulette.utils.media.ffmpeg.models.VideoInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processor for extracting video information using FFprobe with adaptive fallback.
 */
public class VideoProcessor extends BaseProcessor {
    
    // Patterns for parsing FFprobe JSON output
    private static final Pattern DURATION_PATTERN = Pattern.compile("\"duration\"\\s*:\\s*\"?([\\d.]+)\"?");
    private static final Pattern WIDTH_PATTERN = Pattern.compile("\"width\"\\s*:\\s*(\\d+)");
    private static final Pattern HEIGHT_PATTERN = Pattern.compile("\"height\"\\s*:\\s*(\\d+)");
    private static final Pattern CODEC_PATTERN = Pattern.compile("\"codec_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern FORMAT_PATTERN = Pattern.compile("\"format_name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern BITRATE_PATTERN = Pattern.compile("\"bit_rate\"\\s*:\\s*\"?(\\d+)\"?");
    
    public VideoProcessor(FFmpegConfig config) {
        super(config);
    }
    
    /**
     * Gets video information for a URL with automatic fallback on failure.
     */
    public CompletableFuture<VideoInfo> getVideoInfo(String videoUrl) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffprobe");
        cmd.add("-v");
        cmd.add("quiet");
        cmd.add("-print_format");
        cmd.add("json");
        cmd.add("-show_format");
        cmd.add("-show_streams");
        cmd.add("-select_streams");
        cmd.add("v:0");
        cmd.add(videoUrl);
        
        return executeWithRetry(
                attempt -> executeFFprobeCommand(cmd, config.getVideoInfoTimeoutSeconds()),
                result -> !result.isSuccessful(),
                config.getMaxRetries()
        ).thenApply(result -> {
            if (!result.isSuccessful()) {
                logger.warn("FFprobe failed for {}: {}", videoUrl, result.getError());
                return createDefaultVideoInfo(videoUrl);
            }
            return parseVideoInfo(result.getOutput(), videoUrl);
        });
    }
    
    /**
     * Creates a minimal VideoInfo with defaults when FFprobe fails.
     * This allows color extraction to continue with fallback strategies.
     */
    private VideoInfo createDefaultVideoInfo(String url) {
        VideoInfo info = new VideoInfo();
        
        // Try to infer some properties from the URL
        String lowerUrl = url.toLowerCase();
        
        // Assume it's a GIF if URL suggests so
        if (lowerUrl.contains(".gif")) {
            info.setFormat("gif");
            info.setDuration(3.0); // Assume short duration for GIFs
        } else if (lowerUrl.contains(".mp4") || lowerUrl.contains(".webm")) {
            info.setFormat(lowerUrl.contains(".mp4") ? "mp4" : "webm");
            info.setDuration(10.0); // Assume medium duration
        } else {
            info.setFormat("unknown");
            info.setDuration(1.0); // Minimal duration for static images
        }
        
        // Default dimensions
        info.setWidth(480);
        info.setHeight(360);
        
        logger.debug("Created default VideoInfo for {}: {}", url, info);
        return info;
    }
    
    /**
     * Parses FFprobe JSON output into VideoInfo object.
     */
    private VideoInfo parseVideoInfo(String json, String url) {
        VideoInfo info = new VideoInfo();
        
        try {
            // Extract duration
            Matcher durationMatcher = DURATION_PATTERN.matcher(json);
            if (durationMatcher.find()) {
                try {
                    info.setDuration(Double.parseDouble(durationMatcher.group(1)));
                } catch (NumberFormatException e) {
                    info.setDuration(0);
                }
            }
            
            // Extract width
            Matcher widthMatcher = WIDTH_PATTERN.matcher(json);
            if (widthMatcher.find()) {
                try {
                    info.setWidth(Integer.parseInt(widthMatcher.group(1)));
                } catch (NumberFormatException e) {
                    info.setWidth(0);
                }
            }
            
            // Extract height
            Matcher heightMatcher = HEIGHT_PATTERN.matcher(json);
            if (heightMatcher.find()) {
                try {
                    info.setHeight(Integer.parseInt(heightMatcher.group(1)));
                } catch (NumberFormatException e) {
                    info.setHeight(0);
                }
            }
            
            // Extract codec
            Matcher codecMatcher = CODEC_PATTERN.matcher(json);
            if (codecMatcher.find()) {
                info.setCodec(codecMatcher.group(1));
            }
            
            // Extract format
            Matcher formatMatcher = FORMAT_PATTERN.matcher(json);
            if (formatMatcher.find()) {
                info.setFormat(formatMatcher.group(1));
            }
            
            // Extract bitrate
            Matcher bitrateMatcher = BITRATE_PATTERN.matcher(json);
            if (bitrateMatcher.find()) {
                try {
                    info.setBitrate(Long.parseLong(bitrateMatcher.group(1)));
                } catch (NumberFormatException ignored) {}
            }
            
            // Apply defaults for missing critical values
            if (info.getDuration() <= 0) info.setDuration(1.0);
            if (info.getWidth() <= 0) info.setWidth(480);
            if (info.getHeight() <= 0) info.setHeight(360);
            
        } catch (Exception e) {
            logger.warn("Failed to parse video info for {}: {}", url, e.getMessage());
            return createDefaultVideoInfo(url);
        }
        
        return info;
    }
    
    /**
     * Quick check if a URL is likely to be accessible by FFprobe.
     * Does not make network requests, just heuristic checks.
     */
    public boolean isLikelyAccessible(String url) {
        if (url == null || url.isEmpty()) return false;
        
        // Check if domain tracker suggests this domain is problematic
        return !domainTracker.shouldDownloadFirst(url);
    }
}