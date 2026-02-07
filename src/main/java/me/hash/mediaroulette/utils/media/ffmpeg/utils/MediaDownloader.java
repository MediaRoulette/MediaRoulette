package me.hash.mediaroulette.utils.media.ffmpeg.utils;

import me.hash.mediaroulette.utils.media.ffmpeg.config.FFmpegConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;

/**
 * Downloads media files for processing when direct FFmpeg access fails.
 * Used as fallback when URLs are protected or inaccessible to FFmpeg.
 */
public class MediaDownloader {
    private static final Logger logger = LoggerFactory.getLogger(MediaDownloader.class);
    
    private final FFmpegConfig config;
    private final OkHttpClient httpClient;
    
    // Maximum file size to download (100 MB)
    private static final long MAX_DOWNLOAD_SIZE = 100 * 1024 * 1024;
    
    public MediaDownloader(FFmpegConfig config) {
        this.config = config;
        this.httpClient = config.getHttpClient();
    }
    
    /**
     * Downloads a media file to a temporary location for local processing.
     * 
     * @param url URL to download
     * @return CompletableFuture with path to downloaded file
     */
    public CompletableFuture<DownloadResult> download(String url) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            Path tempFile = null;
            
            try {
                config.getFileManager().ensureTempDirectoryExists();
                
                // Determine file extension from URL
                String extension = extractExtension(url);
                tempFile = config.getFileManager().generateTempFilePath("download", extension);
                
                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", config.getHttpSettings().getUserAgent())
                        .header("Accept", "*/*")
                        .header("Referer", extractReferer(url))
                        .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        throw new IOException("HTTP " + response.code() + ": " + response.message());
                    }
                    
                    ResponseBody body = response.body();
                    if (body == null) {
                        throw new IOException("Empty response body");
                    }
                    
                    // Check content length
                    long contentLength = body.contentLength();
                    if (contentLength > MAX_DOWNLOAD_SIZE) {
                        throw new IOException("File too large: " + contentLength + " bytes (max: " + MAX_DOWNLOAD_SIZE + ")");
                    }
                    
                    // Download to temp file
                    try (InputStream in = body.byteStream()) {
                        Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                    
                    // Verify file was created and has content
                    if (!Files.exists(tempFile) || Files.size(tempFile) == 0) {
                        throw new IOException("Downloaded file is empty or missing");
                    }
                    
                    long elapsed = System.currentTimeMillis() - startTime;
                    long fileSize = Files.size(tempFile);
                    
                    logger.debug("Downloaded {} bytes from {} in {}ms", fileSize, url, elapsed);
                    
                    return DownloadResult.success(tempFile, fileSize, elapsed);
                }
                
            } catch (Exception e) {
                // Clean up on failure
                if (tempFile != null) {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException ignored) {}
                }
                
                long elapsed = System.currentTimeMillis() - startTime;
                logger.warn("Download failed for {}: {}", url, e.getMessage());
                return DownloadResult.failure(e.getMessage(), elapsed);
            }
        });
    }
    
    /**
     * Downloads a file only if adaptive tracking suggests it's needed.
     * Returns null path if direct access should be tried first.
     */
    public CompletableFuture<DownloadResult> downloadIfNeeded(String url) {
        AdaptiveDomainTracker tracker = AdaptiveDomainTracker.getInstance();
        
        if (config.isAdaptiveDownloadEnabled() && tracker.shouldDownloadFirst(url)) {
            logger.debug("Adaptive tracker recommends download-first for: {}", url);
            return download(url);
        }
        
        return CompletableFuture.completedFuture(DownloadResult.skipped());
    }
    
    private String extractExtension(String url) {
        try {
            // Remove query parameters and fragments
            String path = url.split("\\?")[0].split("#")[0];
            int lastDot = path.lastIndexOf('.');
            int lastSlash = path.lastIndexOf('/');
            
            if (lastDot > lastSlash && lastDot < path.length() - 1) {
                String ext = path.substring(lastDot + 1).toLowerCase();
                // Validate extension
                if (ext.matches("[a-z0-9]{2,5}")) {
                    return ext;
                }
            }
        } catch (Exception ignored) {}
        
        return "tmp"; // Default extension
    }
    
    private String extractReferer(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            return uri.getScheme() + "://" + uri.getHost() + "/";
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Result of a download operation
     */
    public static class DownloadResult {
        private final boolean success;
        private final boolean skipped;
        private final Path filePath;
        private final long fileSize;
        private final long downloadTimeMs;
        private final String errorMessage;
        
        private DownloadResult(boolean success, boolean skipped, Path filePath, 
                               long fileSize, long downloadTimeMs, String errorMessage) {
            this.success = success;
            this.skipped = skipped;
            this.filePath = filePath;
            this.fileSize = fileSize;
            this.downloadTimeMs = downloadTimeMs;
            this.errorMessage = errorMessage;
        }
        
        public static DownloadResult success(Path path, long size, long timeMs) {
            return new DownloadResult(true, false, path, size, timeMs, null);
        }
        
        public static DownloadResult failure(String error, long timeMs) {
            return new DownloadResult(false, false, null, 0, timeMs, error);
        }
        
        public static DownloadResult skipped() {
            return new DownloadResult(false, true, null, 0, 0, null);
        }
        
        public boolean isSuccess() { return success; }
        public boolean isSkipped() { return skipped; }
        public Path getFilePath() { return filePath; }
        public long getFileSize() { return fileSize; }
        public long getDownloadTimeMs() { return downloadTimeMs; }
        public String getErrorMessage() { return errorMessage; }
        
        /**
         * Cleans up the downloaded file
         */
        public void cleanup() {
            if (filePath != null) {
                try {
                    Files.deleteIfExists(filePath);
                } catch (IOException e) {
                    logger.warn("Failed to cleanup downloaded file: {}", filePath);
                }
            }
        }
    }
}
