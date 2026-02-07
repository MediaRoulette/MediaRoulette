package me.hash.mediaroulette.utils.media.ffmpeg.processors;

import me.hash.mediaroulette.utils.media.ffmpeg.config.FFmpegConfig;
import me.hash.mediaroulette.utils.media.ffmpeg.models.VideoInfo;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Processor for thumbnail extraction with multi-strategy fallback approach.
 */
public class ThumbnailProcessor extends BaseProcessor {
    
    private final HttpClient httpClient;
    
    public ThumbnailProcessor(FFmpegConfig config) {
        super(config);
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }
    
    /**
     * Extracts a thumbnail from a video/media URL at the specified timestamp.
     * Uses multi-strategy approach: FFmpeg -> Download-first -> Java fallback
     */
    public CompletableFuture<BufferedImage> extractThumbnail(String url, double timestampSeconds) {
        Path thumbPath = config.getFileManager().generateTempFilePath("thumb", "jpg");
        
        // Strategy 1: Try FFmpeg directly with proper headers
        return tryFFmpegExtraction(url, timestampSeconds, thumbPath)
                .thenCompose(img -> {
                    if (img != null) return CompletableFuture.completedFuture(img);
                    
                    // Strategy 2: For static images, try Java ImageIO directly
                    if (isStaticImage(url)) {
                        return tryJavaImageIO(url);
                    }
                    
                    return CompletableFuture.completedFuture((BufferedImage) null);
                })
                .exceptionally(e -> {
                    logger.warn("All thumbnail extraction strategies failed for {}: {}", url, e.getMessage());
                    return null;
                });
    }
    
    /**
     * Extracts multiple thumbnails at different timestamps.
     */
    public CompletableFuture<List<BufferedImage>> extractMultipleThumbnails(String url, double[] timestamps) {
        List<CompletableFuture<BufferedImage>> futures = new ArrayList<>();
        for (double ts : timestamps) {
            futures.add(extractThumbnail(url, ts)
                    .exceptionally(e -> null)); // Don't fail all if one fails
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(img -> img != null)
                        .toList());
    }
    
    /**
     * Extracts dominant color from a video using multiple thumbnail samples.
     */
    public CompletableFuture<Color> extractDominantColor(String url, VideoInfo info) {
        double duration = info.getDuration();
        
        // Sample at 25%, 50%, 75% of the video
        double[] timestamps = duration > 0 
                ? new double[]{duration * 0.25, duration * 0.5, duration * 0.75}
                : new double[]{0.0}; // For unknown duration or images
        
        return extractMultipleThumbnails(url, timestamps)
                .thenApply(thumbs -> {
                    if (thumbs.isEmpty()) {
                        // Fallback: try to extract color from URL directly for images
                        return tryDirectColorExtraction(url);
                    }
                    
                    try {
                        long r = 0, g = 0, b = 0;
                        int count = 0;
                        
                        for (BufferedImage img : thumbs) {
                            Color c = analyzeColor(img);
                            r += c.getRed();
                            g += c.getGreen();
                            b += c.getBlue();
                            count++;
                        }
                        
                        if (count == 0) return Color.CYAN;
                        
                        return enhanceSaturation(new Color(
                                (int) (r / count),
                                (int) (g / count), 
                                (int) (b / count)
                        ));
                    } finally {
                        // Clean up image resources
                        for (BufferedImage img : thumbs) {
                            if (img != null) img.flush();
                        }
                    }
                });
    }
    
    private CompletableFuture<BufferedImage> tryFFmpegExtraction(String url, double timestamp, Path outputPath) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-ss");
        cmd.add(String.valueOf(timestamp));
        cmd.add("-i");
        cmd.add(url);
        cmd.add("-vframes");
        cmd.add("1");
        cmd.add("-vf");
        cmd.add("scale=64:64:force_original_aspect_ratio=decrease");
        cmd.add("-q:v");
        cmd.add("5");
        cmd.add("-y");
        cmd.add(outputPath.toString());
        
        return executeFFmpegCommand(cmd, config.getThumbnailTimeoutSeconds())
                .thenApply(result -> {
                    try {
                        if (!result.isSuccessful() || !config.getFileManager().pathExists(outputPath)) {
                            logger.debug("FFmpeg thumbnail extraction failed for {}: {}", url, result.getError());
                            return null;
                        }
                        
                        BufferedImage img = ImageIO.read(outputPath.toFile());
                        if (img == null) {
                            logger.debug("Failed to read thumbnail image for {}", url);
                            return null;
                        }
                        
                        return img;
                    } catch (Exception e) {
                        logger.debug("Error reading thumbnail for {}: {}", url, e.getMessage());
                        return null;
                    } finally {
                        config.getFileManager().deleteIfExists(outputPath);
                    }
                });
    }
    
    private CompletableFuture<BufferedImage> tryJavaImageIO(String url) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] imageBytes = downloadImage(url);
                if (imageBytes == null || imageBytes.length == 0) {
                    return null;
                }
                
                try (InputStream is = new ByteArrayInputStream(imageBytes)) {
                    BufferedImage img = ImageIO.read(is);
                    if (img == null) return null;
                    
                    // Scale down for color analysis
                    return scaleImage(img, 64, 64);
                }
            } catch (Exception e) {
                logger.debug("Java ImageIO fallback failed for {}: {}", url, e.getMessage());
                return null;
            }
        });
    }
    
    private byte[] downloadImage(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", config.getHttpSettings().getUserAgent())
                    .header("Accept", "image/*,*/*;q=0.8")
                    .header("Referer", extractReferer(url))
                    .build();
            
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            
            if (response.statusCode() == 200) {
                return response.body();
            }
        } catch (Exception e) {
            logger.debug("Failed to download image {}: {}", url, e.getMessage());
        }
        return null;
    }
    
    private Color tryDirectColorExtraction(String url) {
        try {
            byte[] imageBytes = downloadImage(url);
            if (imageBytes != null && imageBytes.length > 0) {
                try (InputStream is = new ByteArrayInputStream(imageBytes)) {
                    BufferedImage img = ImageIO.read(is);
                    if (img != null) {
                        BufferedImage scaled = scaleImage(img, 64, 64);
                        Color color = analyzeColor(scaled);
                        img.flush();
                        scaled.flush();
                        return enhanceSaturation(color);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Direct color extraction failed for {}: {}", url, e.getMessage());
        }
        return Color.CYAN;
    }
    
    private BufferedImage scaleImage(BufferedImage original, int width, int height) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(original, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return scaled;
    }
    
    private boolean isStaticImage(String url) {
        String lower = url.toLowerCase();
        // Strip query params
        int queryIdx = lower.indexOf('?');
        if (queryIdx > 0) lower = lower.substring(0, queryIdx);
        
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
               lower.endsWith(".png") || lower.endsWith(".bmp") ||
               lower.endsWith(".webp") || lower.endsWith(".gif");
    }
    
    private String extractReferer(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getScheme() + "://" + uri.getHost() + "/";
        } catch (Exception e) {
            return "";
        }
    }
    
    private Color analyzeColor(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        long rSum = 0, gSum = 0, bSum = 0;
        int count = 0;
        
        // Sample every 2nd pixel for performance
        for (int x = 0; x < w; x += 2) {
            for (int y = 0; y < h; y += 2) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                // Skip very dark and very light pixels
                int brightness = (r + g + b) / 3;
                if (brightness > 30 && brightness < 225) {
                    rSum += r;
                    gSum += g;
                    bSum += b;
                    count++;
                }
            }
        }
        
        if (count == 0) return Color.CYAN;
        return new Color((int)(rSum/count), (int)(gSum/count), (int)(bSum/count));
    }
    
    private Color enhanceSaturation(Color c) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        return Color.getHSBColor(hsb[0], Math.min(1f, hsb[1] * 1.3f), Math.min(1f, hsb[2] * 1.1f));
    }
}