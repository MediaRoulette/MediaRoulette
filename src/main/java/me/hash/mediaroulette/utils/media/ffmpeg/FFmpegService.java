package me.hash.mediaroulette.utils.media.ffmpeg;

import me.hash.mediaroulette.utils.media.ffmpeg.config.FFmpegConfig;
import me.hash.mediaroulette.utils.media.ffmpeg.processors.VideoProcessor;
import me.hash.mediaroulette.utils.media.ffmpeg.processors.ThumbnailProcessor;
import me.hash.mediaroulette.utils.media.ffmpeg.processors.GifProcessor;
import me.hash.mediaroulette.utils.media.ffmpeg.resolvers.UrlResolverFactory;
import me.hash.mediaroulette.utils.media.ffmpeg.models.VideoInfo;
import me.hash.mediaroulette.utils.media.FFmpegDownloader;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FFmpegService {
    private final FFmpegConfig config;
    private final VideoProcessor videoProcessor;
    private final ThumbnailProcessor thumbnailProcessor;
    private final GifProcessor gifProcessor;
    private final UrlResolverFactory urlResolverFactory;

    public FFmpegService() {
        this.config = new FFmpegConfig();
        this.videoProcessor = new VideoProcessor(config);
        this.thumbnailProcessor = new ThumbnailProcessor(config);
        this.gifProcessor = new GifProcessor(config);
        this.urlResolverFactory = new UrlResolverFactory();
    }

    public FFmpegService(FFmpegConfig config) {
        this.config = config;
        this.videoProcessor = new VideoProcessor(config);
        this.thumbnailProcessor = new ThumbnailProcessor(config);
        this.gifProcessor = new GifProcessor(config);
        this.urlResolverFactory = new UrlResolverFactory();
    }

    public CompletableFuture<String> resolveVideoUrl(String url) {
        return urlResolverFactory.getResolver(url).resolve(url);
    }

    public CompletableFuture<VideoInfo> getVideoInfo(String videoUrl) {
        return resolveVideoUrl(videoUrl).thenCompose(videoProcessor::getVideoInfo);
    }

    public CompletableFuture<BufferedImage> extractThumbnail(String videoUrl, double timestampSeconds) {
        return resolveVideoUrl(videoUrl)
                .thenCompose(url -> thumbnailProcessor.extractThumbnail(url, timestampSeconds));
    }

    public CompletableFuture<List<BufferedImage>> extractMultipleThumbnails(String videoUrl, double[] timestamps) {
        return resolveVideoUrl(videoUrl)
                .thenCompose(url -> thumbnailProcessor.extractMultipleThumbnails(url, timestamps));
    }

    public CompletableFuture<Color> extractDominantColor(String videoUrl) {
        return resolveVideoUrl(videoUrl)
                .thenCompose(url -> getVideoInfo(url)
                        .thenCompose(info -> thumbnailProcessor.extractDominantColor(url, info)));
    }

    public CompletableFuture<Path> createSmartGif(String videoUrl) {
        return resolveVideoUrl(videoUrl)
                .thenCompose(url -> getVideoInfo(url)
                        .thenCompose(info -> gifProcessor.createSmartGif(url, info)));
    }

    public CompletableFuture<Path> createGif(String videoUrl, double startTime, double duration, int width, int height) {
        return resolveVideoUrl(videoUrl)
                .thenCompose(url -> gifProcessor.createGif(url, startTime, duration, width, height));
    }

    public CompletableFuture<net.dv8tion.jda.api.utils.FileUpload> createGifUpload(String videoUrl) {
        return resolveVideoUrl(videoUrl)
                .thenCompose(url -> getVideoInfo(url)
                        .thenCompose(info -> gifProcessor.createDiscordOptimizedGif(url, info))
                        .thenApply(gifProcessor::createFileUpload));
    }

    public CompletableFuture<Path> createVideoPreviewGif(String videoUrl) {
        return resolveVideoUrl(videoUrl).thenCompose(gifProcessor::createVideoPreviewGif);
    }

    public boolean isVideoUrl(String url) {
        return urlResolverFactory.isVideoUrl(url);
    }

    public boolean shouldConvertToGif(String url) {
        return urlResolverFactory.shouldConvertToGif(url);
    }

    public String getVideoPreviewUrl(String videoUrl) {
        return urlResolverFactory.getVideoPreviewUrl(videoUrl);
    }

    public void cleanupTempFiles() {
        config.getFileManager().cleanupTempFiles();
    }

    public CompletableFuture<Boolean> isReady() {
        return FFmpegDownloader.getFFmpegPath()
                .thenCompose(ffmpeg -> FFmpegDownloader.getFFprobePath()
                        .thenApply(ffprobe -> config.getFileManager().pathExists(ffmpeg) &&
                                config.getFileManager().pathExists(ffprobe)));
    }
}