package me.hash.mediaroulette.utils.media.ffmpeg.processors;

import me.hash.mediaroulette.utils.media.ffmpeg.config.FFmpegConfig;
import me.hash.mediaroulette.utils.media.ffmpeg.models.VideoInfo;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Processor for GIF creation operations with optimized FFmpeg commands
 */
public class GifProcessor extends BaseProcessor {
    private static final Logger logger = LoggerFactory.getLogger(GifProcessor.class);

    public GifProcessor(FFmpegConfig config) {
        super(config);
    }

    /**
     * Creates an optimized GIF from a video with smart parameters
     */
    public CompletableFuture<Path> createSmartGif(String videoUrl, VideoInfo videoInfo) {
        double videoDuration = videoInfo.getDuration();
        double gifDuration = Math.min(config.getMaxGifDuration(), videoDuration);

        // Calculate optimal dimensions while maintaining aspect ratio
        int width = Math.min(videoInfo.getWidth(), config.getDefaultGifWidth());
        int height = Math.min(videoInfo.getHeight(), config.getDefaultGifHeight());

        double aspectRatio = videoInfo.getAspectRatio();
        if (width / aspectRatio < height) {
            height = (int) (width / aspectRatio);
        } else {
            width = (int) (height * aspectRatio);
        }

        // Ensure dimensions are even (required by some codecs)
        width = (width / 2) * 2;
        height = (height / 2) * 2;

        return createOptimizedGif(videoUrl, 0.0, gifDuration, width, height);
    }

    /**
     * Creates a video preview GIF (30 seconds, optimized size)
     */
    public CompletableFuture<Path> createVideoPreviewGif(String videoUrl) {
        return createGif(videoUrl, 0.0, 30.0, config.getDefaultGifWidth(), config.getDefaultGifHeight());
    }

    /**
     * Creates a GIF with custom parameters
     */
    public CompletableFuture<Path> createGif(String videoUrl, double startTime, double duration, int width, int height) {
        return createOptimizedGif(videoUrl, startTime, duration, width, height);
    }

    /**
     * Creates an optimized GIF using a two-pass approach for better quality and smaller file size
     */
    private CompletableFuture<Path> createOptimizedGif(String videoUrl, double startTime, double duration, int width, int height) {
        Path gifPath = config.getFileManager().generateTempFilePath("video", "gif");
        Path palettePath = config.getFileManager().generateTempFilePath("palette", "png");

        // First pass: Generate palette
        return generatePalette(videoUrl, startTime, duration, width, height, palettePath)
                .thenCompose(paletteResult -> {
                    if (!paletteResult.isSuccessful()) {
                        config.getFileManager().deleteIfExists(palettePath);
                        throw new RuntimeException("Failed to generate palette: " + paletteResult.getError());
                    }

                    // Second pass: Create GIF using the palette
                    return createGifWithPalette(videoUrl, startTime, duration, width, height, palettePath, gifPath);
                })
                .thenApply(gifResult -> {
                    try {
                        config.getFileManager().deleteIfExists(palettePath);

                        if (!gifResult.isSuccessful()) {
                            throw new RuntimeException("Failed to create GIF: " + gifResult.getError());
                        }

                        if (!config.getFileManager().pathExists(gifPath)) {
                            throw new RuntimeException("GIF file was not created");
                        }

                        return gifPath;

                    } catch (Exception e) {
                        config.getFileManager().deleteIfExists(gifPath);
                        throw new RuntimeException("Failed to create GIF: " + e.getMessage(), e);
                    }
                });
    }

    /**
     * First pass: Generate an optimized color palette
     */
    private CompletableFuture<ProcessResult> generatePalette(String videoUrl, double startTime, double duration, 
                                                           int width, int height, Path palettePath) {
        List<String> command = new ArrayList<>();
        command.add("ffmpeg"); // Will be replaced with actual path
        command.add("-i");
        command.add(videoUrl);
        command.add("-ss");
        command.add(String.valueOf(startTime));
        command.add("-t");
        command.add(String.valueOf(duration));
        command.add("-vf");
        command.add(String.format("scale=%d:%d:flags=lanczos,fps=%d,palettegen=stats_mode=diff", 
                                 width, height, config.getDefaultGifFps()));
        command.add("-y");
        command.add(palettePath.toString());

        return executeFFmpegCommand(command, config.getGifCreationTimeoutSeconds());
    }

    /**
     * Second pass: Create GIF using the generated palette
     */
    private CompletableFuture<ProcessResult> createGifWithPalette(String videoUrl, double startTime, double duration,
                                                                int width, int height, Path palettePath, Path gifPath) {
        List<String> command = new ArrayList<>();
        command.add("ffmpeg"); // Will be replaced with actual path
        command.add("-i");
        command.add(videoUrl);
        command.add("-i");
        command.add(palettePath.toString());
        command.add("-ss");
        command.add(String.valueOf(startTime));
        command.add("-t");
        command.add(String.valueOf(duration));
        command.add("-lavfi");
        command.add(String.format("scale=%d:%d:flags=lanczos,fps=%d [x]; [x][1:v] paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle", 
                                 width, height, config.getDefaultGifFps()));
        command.add("-loop");
        command.add("0");
        command.add("-y");
        command.add(gifPath.toString());

        return executeFFmpegCommand(command, config.getGifCreationTimeoutSeconds());
    }

    /**
     * Creates a FileUpload for Discord from a GIF file with size validation
     */
    public FileUpload createFileUpload(Path gifPath) {
        try {
            // Check file size first
            long fileSize = Files.size(gifPath);
            long maxSize = config.getMaxDiscordFileSize(); // Use regular limit for safety
            
            if (fileSize > maxSize) {
                logger.warn("GIF too large ({} bytes), creating smaller version...", fileSize);
                config.getFileManager().deleteIfExists(gifPath);
                throw new RuntimeException("GIF file too large for Discord (" + fileSize + " bytes > " + maxSize + " bytes)");
            }
            
            byte[] gifBytes = Files.readAllBytes(gifPath);
            String fileName = "video_preview_" + System.currentTimeMillis() + ".gif";

            // Clean up the temporary file
            config.getFileManager().deleteIfExists(gifPath);

            return FileUpload.fromData(gifBytes, fileName);
        } catch (Exception e) {
            config.getFileManager().deleteIfExists(gifPath);
            throw new RuntimeException("Failed to create GIF upload: " + e.getMessage(), e);
        }
    }
    
    /**
     * Creates an optimized GIF with aggressive size constraints for Discord (max 25MB)
     */
    public CompletableFuture<Path> createDiscordOptimizedGif(String videoUrl, VideoInfo videoInfo) {
        // Start with conservative parameters to avoid large files
        double maxDuration = Math.min(30.0, videoInfo.getDuration()); // Max 30 seconds
        int maxWidth = Math.min(480, videoInfo.getWidth());
        int maxHeight = Math.min(270, videoInfo.getHeight());
        
        return createOptimizedGifWithSizeLimit(videoUrl, 0.0, maxDuration, maxWidth, maxHeight, 0);
    }
    
    /**
     * Creates GIF with progressive size reduction if needed
     */
    private CompletableFuture<Path> createOptimizedGifWithSizeLimit(String videoUrl, double startTime, 
                                                                   double duration, int width, int height, int attempt) {
        return createOptimizedGif(videoUrl, startTime, duration, width, height)
            .thenCompose(gifPath -> {
                try {
                    long fileSize = Files.size(gifPath);
                    long maxSize = config.getMaxDiscordFileSize(); // 25MB

                    if (fileSize <= maxSize) {
                        return CompletableFuture.completedFuture(gifPath);
                    }
                    
                    // File too large, try smaller parameters
                    if (attempt >= 4) {
                        // Give up after 5 attempts
                        config.getFileManager().deleteIfExists(gifPath);
                        throw new RuntimeException("Unable to create GIF under 25MB after 5 attempts");
                    }

                    logger.warn("GIF too large ({} bytes), reducing size (attempt {})...", fileSize, attempt + 2);
                    config.getFileManager().deleteIfExists(gifPath);
                    
                    // Progressive reduction strategy
                    double newDuration = duration;
                    int newWidth = width;
                    int newHeight = height;
                    
                    switch (attempt) {
                        case 0: // First retry: reduce duration to 20 seconds
                            newDuration = Math.min(20.0, duration);
                            break;
                        case 1: // Second retry: reduce duration to 15 seconds
                            newDuration = Math.min(15.0, duration);
                            break;
                        case 2: // Third retry: reduce resolution and duration to 10 seconds
                            newDuration = Math.min(10.0, duration);
                            newWidth = Math.min(360, width);
                            newHeight = Math.min(200, height);
                            break;
                        case 3: // Fourth retry: very small resolution and 5 seconds
                            newDuration = Math.min(5.0, duration);
                            newWidth = Math.min(240, width);
                            newHeight = Math.min(135, height);
                            break;
                    }
                    
                    // Ensure dimensions are even
                    newWidth = (newWidth / 2) * 2;
                    newHeight = (newHeight / 2) * 2;
                    
                    return createOptimizedGifWithSizeLimit(videoUrl, startTime, newDuration, newWidth, newHeight, attempt + 1);
                    
                } catch (Exception e) {
                    config.getFileManager().deleteIfExists(gifPath);
                    throw new RuntimeException("Failed to check GIF size: " + e.getMessage(), e);
                }
            });
    }
}