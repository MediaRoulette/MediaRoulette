package me.hash.mediaroulette.utils.media.ffmpeg.processors;

import me.hash.mediaroulette.utils.media.ffmpeg.config.FFmpegConfig;
import me.hash.mediaroulette.utils.media.ffmpeg.utils.AdaptiveDomainTracker;
import me.hash.mediaroulette.utils.media.ffmpeg.utils.MediaDownloader;
import me.hash.mediaroulette.utils.media.ffmpeg.utils.ProcessExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Base processor for FFmpeg operations with adaptive fallback support.
 * Automatically handles download-first approach when direct access fails.
 */
public abstract class BaseProcessor {
    protected static final Logger logger = LoggerFactory.getLogger(BaseProcessor.class);
    
    protected final FFmpegConfig config;
    protected final ProcessExecutor processExecutor;
    protected final MediaDownloader mediaDownloader;
    protected final AdaptiveDomainTracker domainTracker;
    
    protected BaseProcessor(FFmpegConfig config) {
        this.config = config;
        this.processExecutor = new ProcessExecutor(config);
        this.mediaDownloader = new MediaDownloader(config);
        this.domainTracker = AdaptiveDomainTracker.getInstance();
    }
    
    /**
     * Executes an FFmpeg command with automatic fallback to download-first on failure.
     * 
     * @param command Base command (first element is placeholder for ffmpeg path)
     * @param url URL to process
     * @param timeoutSeconds Timeout for the operation
     * @return CompletableFuture with process result
     */
    protected CompletableFuture<ProcessResult> executeFFmpegCommand(List<String> command, int timeoutSeconds) {
        // Extract URL from command (usually after -i flag)
        String url = extractUrlFromCommand(command);
        
        if (url != null && config.isAdaptiveDownloadEnabled() && domainTracker.shouldDownloadFirst(url)) {
            // Try download-first approach
            return executeWithDownloadFirst(command, url, timeoutSeconds);
        }
        
        // Try direct execution first
        return executeDirectFFmpeg(command, timeoutSeconds)
                .thenCompose(result -> {
                    if (result.isSuccessful()) {
                        if (url != null) {
                            domainTracker.recordDirectSuccess(url);
                        }
                        return CompletableFuture.completedFuture(result);
                    }
                    
                    // Check if we should try download-first fallback
                    if (url != null && config.isAdaptiveDownloadEnabled() && result.suggestsDownloadFirst()) {
                        logger.debug("Direct FFmpeg failed for {}, trying download-first fallback", url);
                        domainTracker.recordDirectFailure(url);
                        return executeWithDownloadFirst(command, url, timeoutSeconds);
                    }
                    
                    return CompletableFuture.completedFuture(result);
                });
    }
    
    /**
     * Executes an FFprobe command with automatic fallback.
     */
    protected CompletableFuture<ProcessResult> executeFFprobeCommand(List<String> command, int timeoutSeconds) {
        String url = extractUrlFromCommand(command);
        
        if (url != null && config.isAdaptiveDownloadEnabled() && domainTracker.shouldDownloadFirst(url)) {
            return executeFFprobeWithDownloadFirst(command, url, timeoutSeconds);
        }
        
        return executeDirectFFprobe(command, url, timeoutSeconds)
                .thenCompose(result -> {
                    if (result.isSuccessful()) {
                        if (url != null) {
                            domainTracker.recordDirectSuccess(url);
                        }
                        return CompletableFuture.completedFuture(result);
                    }
                    
                    // Fallback to download-first
                    if (url != null && config.isAdaptiveDownloadEnabled() && result.suggestsDownloadFirst()) {
                        logger.debug("Direct FFprobe failed for {}, trying download-first fallback", url);
                        domainTracker.recordDirectFailure(url);
                        return executeFFprobeWithDownloadFirst(command, url, timeoutSeconds);
                    }
                    
                    return CompletableFuture.completedFuture(result);
                });
    }
    
    /**
     * Executes with retry logic for transient failures.
     */
    protected <T> CompletableFuture<T> executeWithRetry(
            Function<Integer, CompletableFuture<T>> operation,
            Function<T, Boolean> shouldRetry,
            int maxRetries) {
        
        return executeWithRetryInternal(operation, shouldRetry, 0, maxRetries);
    }
    
    private <T> CompletableFuture<T> executeWithRetryInternal(
            Function<Integer, CompletableFuture<T>> operation,
            Function<T, Boolean> shouldRetry,
            int attempt,
            int maxRetries) {
        
        return operation.apply(attempt)
                .thenCompose(result -> {
                    if (!shouldRetry.apply(result) || attempt >= maxRetries) {
                        return CompletableFuture.completedFuture(result);
                    }
                    
                    // Wait before retry with exponential backoff
                    long delay = config.getRetryDelayMs() * (1L << attempt);
                    logger.debug("Retrying operation, attempt {} after {}ms", attempt + 1, delay);
                    
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    return executeWithRetryInternal(operation, shouldRetry, attempt + 1, maxRetries);
                });
    }
    
    private CompletableFuture<ProcessResult> executeDirectFFmpeg(List<String> command, int timeoutSeconds) {
        String url = extractUrlFromCommand(command);
        
        return processExecutor.executeFFmpeg(command, url != null ? url : "", timeoutSeconds)
                .thenApply(this::convertToProcessResult);
    }
    
    private CompletableFuture<ProcessResult> executeDirectFFprobe(List<String> command, String url, int timeoutSeconds) {
        return processExecutor.executeFFprobe(command, url != null ? url : "", timeoutSeconds)
                .thenApply(this::convertToProcessResult);
    }
    
    private CompletableFuture<ProcessResult> executeWithDownloadFirst(List<String> command, String url, int timeoutSeconds) {
        return mediaDownloader.download(url)
                .thenCompose(downloadResult -> {
                    if (!downloadResult.isSuccess()) {
                        return CompletableFuture.completedFuture(
                                new ProcessResult(1, "", "Download failed: " + downloadResult.getErrorMessage()));
                    }
                    
                    Path localFile = downloadResult.getFilePath();
                    
                    // Replace URL in command with local file path
                    List<String> localCommand = replaceUrlInCommand(command, url, localFile.toString());
                    
                    return processExecutor.executeFFmpegOnFile(localCommand, localFile, timeoutSeconds)
                            .thenApply(result -> {
                                // Clean up downloaded file
                                downloadResult.cleanup();
                                
                                if (result.isSuccessful()) {
                                    domainTracker.recordDownloadFirstSuccess(url);
                                }
                                
                                return convertToProcessResult(result);
                            });
                });
    }
    
    private CompletableFuture<ProcessResult> executeFFprobeWithDownloadFirst(List<String> command, String url, int timeoutSeconds) {
        return mediaDownloader.download(url)
                .thenCompose(downloadResult -> {
                    if (!downloadResult.isSuccess()) {
                        return CompletableFuture.completedFuture(
                                new ProcessResult(1, "", "Download failed: " + downloadResult.getErrorMessage()));
                    }
                    
                    Path localFile = downloadResult.getFilePath();
                    
                    // Replace URL in command with local file path
                    List<String> localCommand = replaceUrlInCommand(command, url, localFile.toString());
                    
                    return processExecutor.executeFFprobe(localCommand, localFile.toString(), timeoutSeconds)
                            .thenApply(result -> {
                                downloadResult.cleanup();
                                
                                if (result.isSuccessful()) {
                                    domainTracker.recordDownloadFirstSuccess(url);
                                }
                                
                                return convertToProcessResult(result);
                            });
                });
    }
    
    private String extractUrlFromCommand(List<String> command) {
        for (int i = 0; i < command.size(); i++) {
            String arg = command.get(i);
            if (arg.startsWith("http://") || arg.startsWith("https://")) {
                return arg;
            }
            // Also check argument after -i flag
            if ("-i".equals(arg) && i + 1 < command.size()) {
                String next = command.get(i + 1);
                if (next.startsWith("http://") || next.startsWith("https://")) {
                    return next;
                }
            }
        }
        return null;
    }
    
    private List<String> replaceUrlInCommand(List<String> command, String url, String replacement) {
        return command.stream()
                .map(arg -> arg.equals(url) ? replacement : arg)
                .toList();
    }
    
    private ProcessResult convertToProcessResult(ProcessExecutor.ProcessResult result) {
        return new ProcessResult(result.getExitCode(), result.getStdout(), result.getStderr());
    }
    
    /**
     * Result of a process execution (compatibility wrapper)
     */
    protected static class ProcessResult {
        private final int exitCode;
        private final String output;
        private final String error;
        
        public ProcessResult(int exitCode, String output, String error) {
            this.exitCode = exitCode;
            this.output = output != null ? output : "";
            this.error = error != null ? error : "";
        }
        
        public int getExitCode() { return exitCode; }
        public String getOutput() { return output; }
        public String getError() { return error; }
        public boolean isSuccessful() { return exitCode == 0; }
        
        public boolean suggestsDownloadFirst() {
            if (isSuccessful()) return false;
            
            String combined = (output + error).toLowerCase();
            return combined.contains("403") ||
                   combined.contains("forbidden") ||
                   combined.contains("access denied") ||
                   combined.contains("server returned 4") ||
                   combined.contains("server returned 5") ||
                   combined.contains("connection refused") ||
                   (exitCode == 1 && error.isEmpty()); // Generic failure
        }
    }
}