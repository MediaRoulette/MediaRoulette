package me.hash.mediaroulette.utils.media.ffmpeg.models;

/**
 * Result wrapper for FFmpeg/FFprobe operations with comprehensive outcome information.
 * 
 * @param <T> Type of the result data
 */
public class FFmpegResult<T> {
    
    private final boolean success;
    private final T data;
    private final String errorMessage;
    private final ErrorType errorType;
    private final long executionTimeMs;
    private final String url;
    
    private FFmpegResult(boolean success, T data, String errorMessage, ErrorType errorType, 
                         long executionTimeMs, String url) {
        this.success = success;
        this.data = data;
        this.errorMessage = errorMessage;
        this.errorType = errorType;
        this.executionTimeMs = executionTimeMs;
        this.url = url;
    }
    
    /**
     * Creates a successful result
     */
    public static <T> FFmpegResult<T> success(T data, long executionTimeMs, String url) {
        return new FFmpegResult<>(true, data, null, null, executionTimeMs, url);
    }
    
    /**
     * Creates a failed result
     */
    public static <T> FFmpegResult<T> failure(String errorMessage, ErrorType errorType, 
                                               long executionTimeMs, String url) {
        return new FFmpegResult<>(false, null, errorMessage, errorType, executionTimeMs, url);
    }
    
    /**
     * Creates a failure from an exception
     */
    public static <T> FFmpegResult<T> fromException(Exception e, long executionTimeMs, String url) {
        ErrorType type = classifyException(e);
        return failure(e.getMessage(), type, executionTimeMs, url);
    }
    
    private static ErrorType classifyException(Exception e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        
        if (message.contains("timeout") || message.contains("timed out")) {
            return ErrorType.TIMEOUT;
        }
        if (message.contains("403") || message.contains("forbidden")) {
            return ErrorType.ACCESS_DENIED;
        }
        if (message.contains("404") || message.contains("not found")) {
            return ErrorType.NOT_FOUND;
        }
        if (message.contains("connection") || message.contains("network")) {
            return ErrorType.NETWORK_ERROR;
        }
        if (message.contains("exit code")) {
            return ErrorType.PROCESS_ERROR;
        }
        return ErrorType.UNKNOWN;
    }
    
    public boolean isSuccess() { return success; }
    public T getData() { return data; }
    public String getErrorMessage() { return errorMessage; }
    public ErrorType getErrorType() { return errorType; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public String getUrl() { return url; }
    
    /**
     * Returns whether this failure suggests trying a download-first approach
     */
    public boolean shouldTryDownloadFirst() {
        if (success) return false;
        return errorType == ErrorType.ACCESS_DENIED || 
               errorType == ErrorType.PROCESS_ERROR ||
               errorType == ErrorType.TIMEOUT;
    }
    
    /**
     * Returns whether this failure is retryable
     */
    public boolean isRetryable() {
        if (success) return false;
        return errorType == ErrorType.TIMEOUT ||
               errorType == ErrorType.NETWORK_ERROR;
    }
    
    @Override
    public String toString() {
        if (success) {
            return String.format("FFmpegResult[success=true, data=%s, time=%dms]", 
                    data, executionTimeMs);
        } else {
            return String.format("FFmpegResult[success=false, error=%s, type=%s, time=%dms, url=%s]",
                    errorMessage, errorType, executionTimeMs, url);
        }
    }
    
    /**
     * Types of errors that can occur during FFmpeg operations
     */
    public enum ErrorType {
        /** Request timed out */
        TIMEOUT,
        /** Access denied (403, hotlink protection) */
        ACCESS_DENIED,
        /** Resource not found (404) */
        NOT_FOUND,
        /** Network connectivity issues */
        NETWORK_ERROR,
        /** FFmpeg/FFprobe process error (non-zero exit code) */
        PROCESS_ERROR,
        /** File was not created after processing */
        OUTPUT_MISSING,
        /** Invalid or malformed input */
        INVALID_INPUT,
        /** Unknown error */
        UNKNOWN
    }
}
