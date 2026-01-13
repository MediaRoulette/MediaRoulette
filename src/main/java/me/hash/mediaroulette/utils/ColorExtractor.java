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
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ColorExtractor {
    private static final Logger logger = LoggerFactory.getLogger(ColorExtractor.class);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final FFmpegService ffmpegService = new FFmpegService();

    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            "mp4", "webm", "mov", "avi", "mkv", "flv", "wmv", "m4v", "3gp", "ogv", "ts"
    );

    private static final Set<String> NATIVE_IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "bmp", "tiff", "tif"
    );

    private static final Set<String> FFMPEG_IMAGE_EXTENSIONS = Set.of(
            "gif", "webp", "svg", "ico"
    );

    private static final String DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36";

    public static CompletableFuture<Color> extractDominantColor(String imageUrl) {
        if (imageUrl == null || "none".equals(imageUrl) || imageUrl.startsWith("attachment://")) {
            return CompletableFuture.completedFuture(Color.CYAN);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String ext = getExtension(imageUrl);

                if (ext != null && VIDEO_EXTENSIONS.contains(ext)) {
                    return extractFromVideo(imageUrl);
                }

                if (ext != null && FFMPEG_IMAGE_EXTENSIONS.contains(ext)) {
                    return extractWithFFmpeg(imageUrl);
                }

                return extractFromImage(imageUrl);
            } catch (Exception e) {
                logger.warn("Color extraction failed for {}: {}", imageUrl, e.getMessage());
                return Color.CYAN;
            }
        });
    }

    private static String getExtension(String url) {
        if (url == null) return null;
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

    private static Color extractFromVideo(String url) {
        String previewUrl = ffmpegService.getVideoPreviewUrl(url);
        if (previewUrl != null) {
            try {
                return extractFromImage(previewUrl);
            } catch (Exception ignored) {
            }
        }
        return extractWithFFmpeg(url);
    }

    private static Color extractWithFFmpeg(String url) {
        try {
            return ffmpegService.extractDominantColor(url).get();
        } catch (Exception e) {
            logger.warn("FFmpeg color extraction failed for {}: {}", url, e.getMessage());
            return Color.CYAN;
        }
    }

    private static Color extractFromImage(String url) throws Exception {
        byte[] bytes = fetchBytes(url);
        if (bytes == null || bytes.length == 0) {
            return extractWithFFmpeg(url);
        }

        BufferedImage img = null;
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            img = ImageIO.read(is);
            if (img == null) {
                return extractWithFFmpeg(url);
            }
            return getDominantColor(img);
        } finally {
            if (img != null) img.flush();
        }
    }

    private static byte[] fetchBytes(String url) {
        try {
            String referer = extractReferer(url);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
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
        } catch (Exception ignored) {
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

            for (int x = 0; x < w; x += 3) {
                for (int y = 0; y < h; y += 3) {
                    int rgb = scaled.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF;
                    int gr = (rgb >> 8) & 0xFF;
                    int b = rgb & 0xFF;
                    int brightness = (r + gr + b) / 3;
                    if (brightness > 30 && brightness < 225) {
                        rSum += r;
                        gSum += gr;
                        bSum += b;
                        count++;
                    }
                }
            }

            if (count == 0) return Color.CYAN;

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
}