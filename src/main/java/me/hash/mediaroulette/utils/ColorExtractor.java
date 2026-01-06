package me.hash.mediaroulette.utils;

import me.hash.mediaroulette.utils.media.ffmpeg.FFmpegService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class ColorExtractor {
    private static final Logger logger = LoggerFactory.getLogger(ColorExtractor.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final FFmpegService ffmpegService = new FFmpegService();
    
    private static final java.util.Set<String> VIDEO_EXTENSIONS = java.util.Set.of(
            "mp4", "webm", "mov", "avi", "mkv", "flv", "wmv", "m4v", "m4s", "3gp", "ogv", "ts"
    );
    
    private static final java.util.Set<String> IMAGE_EXTENSIONS = java.util.Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico", "tiff", "tif"
    );

    public static CompletableFuture<Color> extractDominantColor(String imageUrl) {
        if (imageUrl == null || "none".equals(imageUrl) || imageUrl.startsWith("attachment://")) {
            return CompletableFuture.completedFuture(Color.CYAN);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                MediaType mediaType = detectMediaType(imageUrl);
                logger.debug("Detected media type {} for URL: {}", mediaType, imageUrl);
                
                if (mediaType == MediaType.VIDEO) {
                    return extractColorFromVideo(imageUrl);
                } else {
                    // Handles both Images and GIFs (first frame)
                    return extractColorFromImage(imageUrl);
                }
            } catch (Exception e) {
                logger.error("Failed to extract color from: {} - {}", imageUrl, e.getMessage());
                return Color.CYAN;
            }
        });
    }
    
    /**
     * Detects the media type of a URL based on extension and platform detection.
     */
    private static MediaType detectMediaType(String url) {
        // Extract extension from URL (strip query params and fragments)
        String extension = getExtensionFromUrl(url);
        
        if (extension != null) {
            String lowerExt = extension.toLowerCase();
            if (VIDEO_EXTENSIONS.contains(lowerExt)) {
                return MediaType.VIDEO;
            }
            if (IMAGE_EXTENSIONS.contains(lowerExt)) {
                return MediaType.IMAGE;
            }
        }
        
        // Fallback to FFmpegService platform detection
        if (ffmpegService.isVideoUrl(url)) {
            return MediaType.VIDEO;
        }
        
        return MediaType.IMAGE; // Default to image
    }
    
    /**
     * Extracts the file extension from a URL, handling query parameters and fragments.
     */
    private static String getExtensionFromUrl(String url) {
        if (url == null) return null;
        
        // Strip query params and fragments
        int queryIndex = url.indexOf('?');
        int fragmentIndex = url.indexOf('#');
        int endIndex = url.length();
        
        if (queryIndex != -1) endIndex = Math.min(endIndex, queryIndex);
        if (fragmentIndex != -1) endIndex = Math.min(endIndex, fragmentIndex);
        
        String urlPath = url.substring(0, endIndex);
        
        // Find the last dot
        int lastDotIndex = urlPath.lastIndexOf('.');
        int lastSlashIndex = urlPath.lastIndexOf('/');
        
        // Make sure the dot is after the last slash (in filename, not path)
        if (lastDotIndex > lastSlashIndex && lastDotIndex < urlPath.length() - 1) {
            return urlPath.substring(lastDotIndex + 1);
        }
        
        return null;
    }
    
    /**
     * Media type enumeration
     */
    private enum MediaType {
        VIDEO, IMAGE, UNKNOWN
    }

    private static Color extractColorFromVideo(String url) {
        // Try to get a preview image first (faster)
        String previewUrl = ffmpegService.getVideoPreviewUrl(url);
        if (previewUrl != null) {
            try {
                return extractColorFromImage(previewUrl);
            } catch (Exception e) {
                logger.error("Failed to extract color from preview, falling back to FFmpeg: {}", e.getMessage());
            }
        }
        
        // Fallback to FFmpeg extraction
        try {
            return ffmpegService.extractDominantColor(url).get();
        } catch (Exception e) {
            logger.error("FFmpeg color extraction failed: {}", e.getMessage());
            return Color.CYAN;
        }
    }

    private static Color extractColorFromImage(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10)) // Added timeout
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();

        HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch image: " + response.statusCode());
        }

        BufferedImage image = null;
        try {
            image = ImageIO.read(new ByteArrayInputStream(response.body()));
            if (image == null) {
                throw new IOException("Could not decode image");
            }

            return getDominantColor(image);
        } finally {
            if (image != null) {
                image.flush();
            }
        }
    }

    private static Color getDominantColor(BufferedImage image) {
        int width = Math.min(image.getWidth(), 100);
        int height = Math.min(image.getHeight(), 100);

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

            return enhanceSaturation(new Color(avgRed, avgGreen, avgBlue));
        } finally {
            if (g2d != null) {
                g2d.dispose();
            }
            if (scaled != null) {
                scaled.flush();
            }
        }
    }

    private static Color enhanceSaturation(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float saturation = Math.min(1.0f, hsb[1] * 1.3f);
        float brightness = Math.min(1.0f, hsb[2] * 1.1f);
        return Color.getHSBColor(hsb[0], saturation, brightness);
    }
}