package me.hash.mediaroulette.utils;

import me.hash.mediaroulette.utils.media.ffmpeg.FFmpegService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Extracts dominant colors from images and videos with multi-strategy fallback.
 * Uses a layered approach:
 * 1. Simple LRU cache for repeated URLs
 * 2. Java ImageIO for standard images  
 * 3. FFmpeg for videos and complex formats (with adaptive download-first)
 * 4. Graceful fallback to default color
 */
public class ColorExtractor {
    private static final Logger logger = LoggerFactory.getLogger(ColorExtractor.class);
    
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    
    private static final FFmpegService ffmpegService = new FFmpegService();
    
    // Simple LRU-like cache for extracted colors
    private static final int MAX_CACHE_SIZE = 500;
    private static final Map<String, Color> colorCache = new ConcurrentHashMap<>();
    
    // Extensions that Java ImageIO can handle natively
    private static final Set<String> NATIVE_IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "bmp", "tiff", "tif"
    );
    
    // Extensions that need FFmpeg processing
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mp4", "webm", "mov", "avi", "mkv", "flv", "wmv", "m4v", "3gp", "ogv", "ts"
    );
    
    // Extensions that might need FFmpeg but can sometimes work with ImageIO
    private static final Set<String> HYBRID_EXTENSIONS = Set.of(
            "gif", "webp"
    );
    
    private static final String DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36";
    private static final Color DEFAULT_COLOR = Color.CYAN;
    
    /**
     * Extracts the dominant color from an image or video URL.
     * Uses multi-strategy approach with automatic fallback.
     */
    public static CompletableFuture<Color> extractDominantColor(String imageUrl) {
        // Quick exit for invalid URLs
        if (imageUrl == null || "none".equals(imageUrl) || imageUrl.startsWith("attachment://")) {
            return CompletableFuture.completedFuture(DEFAULT_COLOR);
        }
        
        // Check cache first
        Color cached = colorCache.get(imageUrl);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String ext = getExtension(imageUrl);
                Color result;
                
                // Strategy 1: For native image formats, try Java ImageIO first
                if (ext != null && NATIVE_IMAGE_EXTENSIONS.contains(ext)) {
                    result = extractFromImage(imageUrl);
                    if (result != null && !result.equals(DEFAULT_COLOR)) {
                        cacheColor(imageUrl, result);
                        return result;
                    }
                }
                
                // Strategy 2: For videos, use FFmpeg
                if (ext != null && VIDEO_EXTENSIONS.contains(ext)) {
                    result = extractWithFFmpeg(imageUrl);
                    cacheColor(imageUrl, result);
                    return result;
                }
                
                // Strategy 3: For hybrid formats (GIF, WebP), try ImageIO then FFmpeg
                if (ext != null && HYBRID_EXTENSIONS.contains(ext)) {
                    result = extractFromImage(imageUrl);
                    if (result != null && !result.equals(DEFAULT_COLOR)) {
                        cacheColor(imageUrl, result);
                        return result;
                    }
                    // Fallback to FFmpeg for animated GIFs
                    result = extractWithFFmpeg(imageUrl);
                    cacheColor(imageUrl, result);
                    return result;
                }
                
                // Strategy 4: Unknown format - try ImageIO first, then FFmpeg
                result = extractFromImage(imageUrl);
                if (result != null && !result.equals(DEFAULT_COLOR)) {
                    cacheColor(imageUrl, result);
                    return result;
                }
                
                result = extractWithFFmpeg(imageUrl);
                cacheColor(imageUrl, result);
                return result;
                
            } catch (Exception e) {
                logger.debug("Color extraction failed for {}: {}", imageUrl, e.getMessage());
                return DEFAULT_COLOR;
            }
        });
    }
    
    private static String getExtension(String url) {
        if (url == null) return null;
        
        // Strip query parameters and fragments
        int queryIdx = url.indexOf('?');
        int fragIdx = url.indexOf('#');
        int end = url.length();
        if (queryIdx != -1) end = Math.min(end, queryIdx);
        if (fragIdx != -1) end = Math.min(end, fragIdx);
        
        String path = url.substring(0, end);
        int dotIdx = path.lastIndexOf('.');
        int slashIdx = path.lastIndexOf('/');
        
        if (dotIdx > slashIdx && dotIdx < path.length() - 1) {
            return path.substring(dotIdx + 1).toLowerCase();
        }
        return null;
    }
    
    private static Color extractWithFFmpeg(String url) {
        try {
            // FFmpegService now handles all the adaptive fallback internally
            return ffmpegService.extractDominantColor(url)
                    .exceptionally(e -> {
                        logger.debug("FFmpeg color extraction fallback for {}: {}", url, e.getMessage());
                        return DEFAULT_COLOR;
                    })
                    .get();
        } catch (Exception e) {
            logger.debug("FFmpeg color extraction failed for {}: {}", url, e.getMessage());
            return DEFAULT_COLOR;
        }
    }
    
    private static Color extractFromImage(String url) {
        try {
            byte[] bytes = fetchBytes(url);
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            
            BufferedImage img = null;
            try (InputStream is = new ByteArrayInputStream(bytes)) {
                img = ImageIO.read(is);
                if (img == null) {
                    return null;
                }
                return getDominantColor(img);
            } finally {
                if (img != null) img.flush();
            }
        } catch (Exception e) {
            logger.debug("Image extraction failed for {}: {}", url, e.getMessage());
            return null;
        }
    }
    
    private static byte[] fetchBytes(String url) {
        try {
            String referer = extractReferer(url);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", DEFAULT_UA)
                    .header("Accept", "image/*,*/*;q=0.8")
                    .header("Accept-Encoding", "identity");
            
            if (referer != null) {
                builder.header("Referer", referer);
            }
            
            HttpResponse<byte[]> resp = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200) {
                return resp.body();
            }
            
            // Log non-200 responses at debug level
            logger.debug("HTTP {} for {}", resp.statusCode(), url);
        } catch (Exception e) {
            logger.debug("Failed to fetch {}: {}", url, e.getMessage());
        }
        return null;
    }
    
    private static String extractReferer(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getScheme() + "://" + uri.getHost() + "/";
        } catch (Exception e) {
            return null;
        }
    }
    
    private static Color getDominantColor(BufferedImage image) {
        int srcW = image.getWidth();
        int srcH = image.getHeight();
        
        // Scale down for faster processing
        int w = Math.min(srcW, 64);
        int h = Math.min(srcH, 64);
        
        BufferedImage scaled = null;
        Graphics2D g = null;
        try {
            scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(image, 0, 0, w, h, null);
            
            long rSum = 0, gSum = 0, bSum = 0;
            int count = 0;
            
            // Sample every 3rd pixel for speed
            for (int x = 0; x < w; x += 3) {
                for (int y = 0; y < h; y += 3) {
                    int rgb = scaled.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int gr = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    
                    // Skip very dark and very light pixels
                    int brightness = (r + gr + b) / 3;
                    if (brightness > 30 && brightness < 225) {
                        rSum += r;
                        gSum += gr;
                        bSum += b;
                        count++;
                    }
                }
            }
            
            if (count == 0) return DEFAULT_COLOR;
            
            int avgR = (int) (rSum / count);
            int avgG = (int) (gSum / count);
            int avgB = (int) (bSum / count);
            return enhanceSaturation(new Color(avgR, avgG, avgB));
            
        } finally {
            if (g != null) g.dispose();
            if (scaled != null) scaled.flush();
        }
    }
    
    private static Color enhanceSaturation(Color c) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        return Color.getHSBColor(hsb[0], Math.min(1f, hsb[1] * 1.3f), Math.min(1f, hsb[2] * 1.1f));
    }
    
    private static void cacheColor(String url, Color color) {
        // Simple cache management - remove oldest entries if too large
        if (colorCache.size() >= MAX_CACHE_SIZE) {
            // Remove first quarter of entries (rough LRU behavior)
            int toRemove = MAX_CACHE_SIZE / 4;
            colorCache.keySet().stream()
                    .limit(toRemove)
                    .toList()
                    .forEach(colorCache::remove);
        }
        colorCache.put(url, color);
    }
    
    /**
     * Clears the color cache.
     */
    public static void clearCache() {
        colorCache.clear();
        logger.debug("Color cache cleared");
    }
    
    /**
     * Gets the current cache size.
     */
    public static int getCacheSize() {
        return colorCache.size();
    }
}