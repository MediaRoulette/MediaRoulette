package me.hash.mediaroulette.utils.media.ffmpeg.config;

import me.hash.mediaroulette.utils.media.ffmpeg.utils.FileManager;
import okhttp3.OkHttpClient;

import java.util.concurrent.TimeUnit;

public class FFmpegConfig {
    private final int defaultTimeoutSeconds = 15;
    private final int gifCreationTimeoutSeconds = 60;
    private final int thumbnailTimeoutSeconds = 10;
    private final int videoInfoTimeoutSeconds = 8;

    private final int defaultGifWidth = 480;
    private final int defaultGifHeight = 270;
    private final int defaultGifFps = 10;
    private final double maxGifDuration = 20.0;

    private final long maxDiscordFileSize = 25 * 1024 * 1024; // 25 MB
    private final long maxDiscordFileSizePremium = 500 * 1024 * 1024; // 500MB

    private final String tempDirectory = "temp";
    private final FileManager fileManager = new FileManager(tempDirectory);

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    public int getDefaultTimeoutSeconds() { return defaultTimeoutSeconds; }
    public int getGifCreationTimeoutSeconds() { return gifCreationTimeoutSeconds; }
    public int getThumbnailTimeoutSeconds() { return thumbnailTimeoutSeconds; }
    public int getVideoInfoTimeoutSeconds() { return videoInfoTimeoutSeconds; }

    public int getDefaultGifWidth() { return defaultGifWidth; }
    public int getDefaultGifHeight() { return defaultGifHeight; }
    public int getDefaultGifFps() { return defaultGifFps; }
    public double getMaxGifDuration() { return maxGifDuration; }

    public long getMaxDiscordFileSize() { return maxDiscordFileSize; }
    public long getMaxDiscordFileSizePremium() { return maxDiscordFileSizePremium; }

    public String getTempDirectory() { return tempDirectory; }
    public FileManager getFileManager() { return fileManager; }
    public OkHttpClient getHttpClient() { return httpClient; }
}