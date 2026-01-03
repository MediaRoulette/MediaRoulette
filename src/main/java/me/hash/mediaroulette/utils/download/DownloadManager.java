package me.hash.mediaroulette.utils.download;

import me.hash.mediaroulette.utils.terminal.ProgressBar;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * Centralized download manager with progress bar support.
 * Provides shared HTTP client and download utilities for ResourceManager and FFmpegDownloader.
 */
public class DownloadManager {
    private static final Logger logger = LoggerFactory.getLogger(DownloadManager.class);
    
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
    
    /**
     * Download a file asynchronously with progress bar.
     */
    public static CompletableFuture<Path> downloadAsync(DownloadRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeDownload(request);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }
    
    /**
     * Download a file synchronously with progress bar.
     */
    public static Path download(DownloadRequest request) throws IOException {
        return executeDownload(request);
    }
    
    private static Path executeDownload(DownloadRequest request) throws IOException {
        Files.createDirectories(request.targetPath().getParent());
        
        Request httpRequest = new Request.Builder()
                .url(request.url())
                .header("User-Agent", "MediaRoulette/1.0")
                .build();
        
        try (Response response = HTTP_CLIENT.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " " + response.message());
            }
            
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Response body is null");
            }
            
            long contentLength = body.contentLength();
            
            if (request.showProgress()) {
                return downloadWithProgress(body, request, contentLength);
            } else {
                return downloadSilent(body, request);
            }
        }
    }
    
    private static Path downloadWithProgress(ResponseBody body, DownloadRequest request, long contentLength) 
            throws IOException {
        
        ProgressBar.Style style = request.style() != null ? request.style() : ProgressBar.Style.DOWNLOAD;
        
        try (ProgressBar progress = ProgressBar.create(request.taskName())
                .withTotal(contentLength)
                .withStyle(style)
                .start();
             InputStream in = body.byteStream();
             OutputStream out = Files.newOutputStream(request.targetPath())) {
            
            byte[] buffer = new byte[8192];
            long totalRead = 0;
            int bytesRead;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                progress.stepTo(totalRead);
            }
            
            progress.complete(ProgressBar.formatBytes(totalRead));
        }
        
        return request.targetPath();
    }
    
    private static Path downloadSilent(ResponseBody body, DownloadRequest request) throws IOException {
        try (InputStream in = body.byteStream();
             OutputStream out = Files.newOutputStream(request.targetPath())) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
        
        return request.targetPath();
    }
    
    /**
     * Get the shared HTTP client for custom requests.
     */
    public static OkHttpClient getHttpClient() {
        return HTTP_CLIENT;
    }
    
    /**
     * Shutdown the HTTP client and release resources.
     */
    public static void shutdown() {
        try {
            HTTP_CLIENT.dispatcher().executorService().shutdown();
            HTTP_CLIENT.connectionPool().evictAll();
            if (HTTP_CLIENT.cache() != null) {
                HTTP_CLIENT.cache().close();
            }
        } catch (Exception e) {
            logger.warn("Error shutting down DownloadManager: {}", e.getMessage());
        }
    }
    
    /**
     * Download request configuration.
     */
    public record DownloadRequest(
        String url,
        Path targetPath,
        String taskName,
        boolean showProgress,
        ProgressBar.Style style
    ) {
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String url;
            private Path targetPath;
            private String taskName = "Downloading";
            private boolean showProgress = true;
            private ProgressBar.Style style = ProgressBar.Style.DOWNLOAD;
            
            public Builder url(String url) {
                this.url = url;
                return this;
            }
            
            public Builder target(Path path) {
                this.targetPath = path;
                return this;
            }
            
            public Builder taskName(String name) {
                this.taskName = name;
                return this;
            }
            
            public Builder showProgress(boolean show) {
                this.showProgress = show;
                return this;
            }
            
            public Builder style(ProgressBar.Style style) {
                this.style = style;
                return this;
            }
            
            public DownloadRequest build() {
                if (url == null || url.isBlank()) {
                    throw new IllegalArgumentException("URL is required");
                }
                if (targetPath == null) {
                    throw new IllegalArgumentException("Target path is required");
                }
                return new DownloadRequest(url, targetPath, taskName, showProgress, style);
            }
        }
    }
}
