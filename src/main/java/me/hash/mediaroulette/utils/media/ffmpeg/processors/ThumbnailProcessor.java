package me.hash.mediaroulette.utils.media.ffmpeg.processors;

import me.hash.mediaroulette.utils.media.ffmpeg.config.FFmpegConfig;
import me.hash.mediaroulette.utils.media.ffmpeg.models.VideoInfo;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Processor for thumbnail extraction operations
 */
public class ThumbnailProcessor extends BaseProcessor {

    public ThumbnailProcessor(FFmpegConfig config) {
        super(config);
    }

    /**
     * Extracts a single thumbnail from a video at the specified timestamp
     */
    public CompletableFuture<BufferedImage> extractThumbnail(String videoUrl, double timestampSeconds) {
        Path thumbnailPath = config.getFileManager().generateTempFilePath("thumb", "jpg");

        List<String> command = new ArrayList<>();
        command.add("ffmpeg"); // Will be replaced with actual path
        command.add("-i");
        command.add(videoUrl);
        command.add("-ss");
        command.add(String.valueOf(timestampSeconds));
        command.add("-vframes");
        command.add("1");
        command.add("-q:v");
        command.add("2");
        command.add("-y");
        command.add(thumbnailPath.toString());

        return executeFFmpegCommand(command, config.getThumbnailTimeoutSeconds())
                .thenApply(result -> {
                    BufferedImage thumbnail = null;
                    try {
                        if (!result.isSuccessful()) {
                            throw new RuntimeException("FFmpeg thumbnail extraction failed: " + result.getError());
                        }

                        if (!config.getFileManager().pathExists(thumbnailPath)) {
                            throw new RuntimeException("Thumbnail file was not created");
                        }

                        thumbnail = ImageIO.read(thumbnailPath.toFile());

                        if (thumbnail == null) {
                            throw new RuntimeException("Failed to read thumbnail image");
                        }

                        return thumbnail;

                    } catch (Exception e) {
                        // Clean up on error
                        if (thumbnail != null) {
                            thumbnail.flush();
                        }
                        throw new RuntimeException("Failed to extract thumbnail: " + e.getMessage(), e);
                    } finally {
                        // Always delete temp file
                        config.getFileManager().deleteIfExists(thumbnailPath);
                    }
                });
    }

    /**
     * Extracts multiple thumbnails from a video at specified timestamps
     */
    public CompletableFuture<List<BufferedImage>> extractMultipleThumbnails(String videoUrl, double[] timestamps) {
        List<CompletableFuture<BufferedImage>> futures = new ArrayList<>();

        for (double timestamp : timestamps) {
            futures.add(extractThumbnail(videoUrl, timestamp));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    /**
     * Extracts the dominant color from a video by analyzing multiple frames
     */
    public CompletableFuture<Color> extractDominantColor(String videoUrl, VideoInfo videoInfo) {
        double duration = videoInfo.getDuration();
        double[] timestamps = {
                duration * 0.1,
                duration * 0.3,
                duration * 0.5,
                duration * 0.7,
                duration * 0.9
        };

        return extractMultipleThumbnails(videoUrl, timestamps)
                .thenApply(thumbnails -> {
                    try {
                        long totalRed = 0, totalGreen = 0, totalBlue = 0;
                        int totalPixels = 0;

                        for (BufferedImage thumbnail : thumbnails) {
                            Color frameColor = analyzeThumbnailColor(thumbnail);
                            totalRed += frameColor.getRed();
                            totalGreen += frameColor.getGreen();
                            totalBlue += frameColor.getBlue();
                            totalPixels++;
                        }

                        if (totalPixels == 0) {
                            return Color.CYAN;
                        }

                        int avgRed = (int) (totalRed / totalPixels);
                        int avgGreen = (int) (totalGreen / totalPixels);
                        int avgBlue = (int) (totalBlue / totalPixels);

                        return enhanceSaturation(new Color(avgRed, avgGreen, avgBlue));
                    } finally {
                        // Clean up all thumbnails
                        for (BufferedImage thumbnail : thumbnails) {
                            if (thumbnail != null) {
                                thumbnail.flush();
                            }
                        }
                    }
                });
    }

    /**
     * Analyzes a thumbnail to extract its dominant color
     */
    private Color analyzeThumbnailColor(BufferedImage image) {
        int width = Math.min(image.getWidth(), 50);
        int height = Math.min(image.getHeight(), 50);

        BufferedImage scaled = null;
        Graphics2D g2d = null;
        
        try {
            scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            g2d = scaled.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.drawImage(image, 0, 0, width, height, null);

            long redSum = 0, greenSum = 0, blueSum = 0;
            int pixelCount = 0;

            for (int x = 0; x < width; x += 2) {
                for (int y = 0; y < height; y += 2) {
                    int rgb = scaled.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int g = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;

                    int brightness = (r + g + b) / 3;
                    if (brightness > 30 && brightness < 225) {
                        redSum += r;
                        greenSum += g;
                        blueSum += b;
                        pixelCount++;
                    }
                }
            }

            if (pixelCount == 0) {
                return Color.CYAN;
            }

            int avgRed = (int) (redSum / pixelCount);
            int avgGreen = (int) (greenSum / pixelCount);
            int avgBlue = (int) (blueSum / pixelCount);

            return new Color(avgRed, avgGreen, avgBlue);
        } finally {
            // Clean up resources
            if (g2d != null) {
                g2d.dispose();
            }
            if (scaled != null) {
                scaled.flush();
            }
        }
    }

    /**
     * Enhances the saturation of a color for better visual appeal
     */
    private Color enhanceSaturation(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float saturation = Math.min(1.0f, hsb[1] * 1.3f);
        float brightness = Math.min(1.0f, hsb[2] * 1.1f);
        return Color.getHSBColor(hsb[0], saturation, brightness);
    }
}