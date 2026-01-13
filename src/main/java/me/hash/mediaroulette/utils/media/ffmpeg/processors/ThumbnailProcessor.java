package me.hash.mediaroulette.utils.media.ffmpeg.processors;

import me.hash.mediaroulette.utils.media.ffmpeg.config.FFmpegConfig;
import me.hash.mediaroulette.utils.media.ffmpeg.models.VideoInfo;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ThumbnailProcessor extends BaseProcessor {

    public ThumbnailProcessor(FFmpegConfig config) {
        super(config);
    }

    public CompletableFuture<BufferedImage> extractThumbnail(String videoUrl, double timestampSeconds) {
        Path thumbPath = config.getFileManager().generateTempFilePath("thumb", "jpg");

        List<String> cmd = new ArrayList<>();
        cmd.add("ffmpeg");
        cmd.add("-ss");
        cmd.add(String.valueOf(timestampSeconds));
        cmd.add("-i");
        cmd.add(videoUrl);
        cmd.add("-vframes");
        cmd.add("1");
        cmd.add("-vf");
        cmd.add("scale=64:64:force_original_aspect_ratio=decrease");
        cmd.add("-q:v");
        cmd.add("5");
        cmd.add("-y");
        cmd.add(thumbPath.toString());

        return executeFFmpegCommand(cmd, config.getThumbnailTimeoutSeconds())
                .thenApply(result -> {
                    try {
                        if (!result.isSuccessful() || !config.getFileManager().pathExists(thumbPath)) {
                            throw new RuntimeException("Thumbnail extraction failed");
                        }
                        BufferedImage img = ImageIO.read(thumbPath.toFile());
                        if (img == null) throw new RuntimeException("Failed to read thumbnail");
                        return img;
                    } catch (Exception e) {
                        throw new RuntimeException(e.getMessage(), e);
                    } finally {
                        config.getFileManager().deleteIfExists(thumbPath);
                    }
                });
    }

    public CompletableFuture<List<BufferedImage>> extractMultipleThumbnails(String videoUrl, double[] timestamps) {
        List<CompletableFuture<BufferedImage>> futures = new ArrayList<>();
        for (double ts : timestamps) {
            futures.add(extractThumbnail(videoUrl, ts));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).toList());
    }

    public CompletableFuture<Color> extractDominantColor(String videoUrl, VideoInfo info) {
        double d = info.getDuration();
        double[] ts = {d * 0.25, d * 0.5, d * 0.75};

        return extractMultipleThumbnails(videoUrl, ts)
                .thenApply(thumbs -> {
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
                        return enhanceSaturation(new Color((int)(r/count), (int)(g/count), (int)(b/count)));
                    } finally {
                        for (BufferedImage img : thumbs) {
                            if (img != null) img.flush();
                        }
                    }
                });
    }

    private Color analyzeColor(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        long rSum = 0, gSum = 0, bSum = 0;
        int count = 0;

        for (int x = 0; x < w; x += 2) {
            for (int y = 0; y < h; y += 2) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int gr = (rgb >> 8) & 0xFF;
                int bl = rgb & 0xFF;
                int brightness = (r + gr + bl) / 3;
                if (brightness > 30 && brightness < 225) {
                    rSum += r;
                    gSum += gr;
                    bSum += bl;
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