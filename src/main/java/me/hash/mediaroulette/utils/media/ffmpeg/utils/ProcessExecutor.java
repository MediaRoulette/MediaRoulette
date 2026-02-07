package me.hash.mediaroulette.utils.media.ffmpeg.utils;

import me.hash.mediaroulette.utils.media.ffmpeg.config.FFmpegConfig;
import me.hash.mediaroulette.utils.media.ffmpeg.config.HttpSettings;
import me.hash.mediaroulette.utils.media.FFmpegDownloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Centralized executor for FFmpeg and FFprobe processes with proper error handling,
 * HTTP headers support, and resource cleanup.
 */
public class ProcessExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ProcessExecutor.class);
    
    private final FFmpegConfig config;
    
    public ProcessExecutor(FFmpegConfig config) {
        this.config = config;
    }
    
    /**
     * Executes an FFmpeg command with proper headers and timeout handling.
     * 
     * @param baseCommand Command list (first element should be "ffmpeg" placeholder)
     * @param inputUrl URL to process (headers will be added automatically)
     * @param timeoutSeconds Timeout in seconds
     * @return CompletableFuture with the process result
     */
    public CompletableFuture<ProcessResult> executeFFmpeg(List<String> baseCommand, String inputUrl, int timeoutSeconds) {
        return FFmpegDownloader.getFFmpegPath().thenCompose(ffmpegPath ->
            CompletableFuture.supplyAsync(() -> {
                long startTime = System.currentTimeMillis();
                try {
                    config.getFileManager().ensureTempDirectoryExists();
                    
                    // Build command with headers
                    List<String> command = buildCommandWithHeaders(ffmpegPath, baseCommand, inputUrl);
                    
                    logger.debug("Executing FFmpeg: {}", String.join(" ", command));
                    
                    return executeProcess(command, timeoutSeconds, startTime);
                } catch (Exception e) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    return new ProcessResult(-1, "", e.getMessage(), elapsed, false);
                }
            }));
    }
    
    /**
     * Executes an FFprobe command with proper headers.
     */
    public CompletableFuture<ProcessResult> executeFFprobe(List<String> baseCommand, String inputUrl, int timeoutSeconds) {
        return FFmpegDownloader.getFFprobePath().thenCompose(ffprobePath ->
            CompletableFuture.supplyAsync(() -> {
                long startTime = System.currentTimeMillis();
                try {
                    // Build command with headers for FFprobe
                    List<String> command = buildFFprobeCommand(ffprobePath, baseCommand, inputUrl);
                    
                    logger.debug("Executing FFprobe: {}", String.join(" ", command));
                    
                    return executeProcess(command, timeoutSeconds, startTime);
                } catch (Exception e) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    return new ProcessResult(-1, "", e.getMessage(), elapsed, false);
                }
            }));
    }
    
    /**
     * Executes an FFmpeg command on a local file (no headers needed).
     */
    public CompletableFuture<ProcessResult> executeFFmpegOnFile(List<String> command, Path localFile, int timeoutSeconds) {
        return FFmpegDownloader.getFFmpegPath().thenCompose(ffmpegPath ->
            CompletableFuture.supplyAsync(() -> {
                long startTime = System.currentTimeMillis();
                try {
                    config.getFileManager().ensureTempDirectoryExists();
                    
                    // Replace placeholder and file path
                    List<String> finalCommand = new ArrayList<>(command);
                    finalCommand.set(0, ffmpegPath.toString());
                    
                    // Replace INPUT_FILE placeholder if present
                    for (int i = 0; i < finalCommand.size(); i++) {
                        if ("INPUT_FILE".equals(finalCommand.get(i))) {
                            finalCommand.set(i, localFile.toString());
                        }
                    }
                    
                    return executeProcess(finalCommand, timeoutSeconds, startTime);
                } catch (Exception e) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    return new ProcessResult(-1, "", e.getMessage(), elapsed, false);
                }
            }));
    }
    
    private List<String> buildCommandWithHeaders(Path ffmpegPath, List<String> baseCommand, String inputUrl) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath.toString());
        
        HttpSettings http = config.getHttpSettings();
        
        // Find where -i is in the base command and insert headers before it
        boolean foundInput = false;
        for (int i = 1; i < baseCommand.size(); i++) {
            String arg = baseCommand.get(i);
            
            if ("-i".equals(arg) && !foundInput) {
                // Add headers before -i
                String[] headerArgs = http.buildFFmpegInputArgs(inputUrl);
                for (String headerArg : headerArgs) {
                    command.add(headerArg);
                }
                foundInput = true;
                i++; // Skip the URL placeholder in base command
            } else {
                command.add(arg);
            }
        }
        
        // If no -i found, append headers and input
        if (!foundInput) {
            String[] headerArgs = http.buildFFmpegInputArgs(inputUrl);
            for (String headerArg : headerArgs) {
                command.add(headerArg);
            }
        }
        
        return command;
    }
    
    private List<String> buildFFprobeCommand(Path ffprobePath, List<String> baseCommand, String inputUrl) {
        List<String> command = new ArrayList<>();
        command.add(ffprobePath.toString());
        
        HttpSettings http = config.getHttpSettings();
        
        // Add headers at the start (after ffprobe executable)
        String[] headerArgs = http.buildFFprobeArgs(inputUrl);
        for (String headerArg : headerArgs) {
            command.add(headerArg);
        }
        
        // Add rest of base command (skip first element which is placeholder)
        for (int i = 1; i < baseCommand.size(); i++) {
            String arg = baseCommand.get(i);
            // Replace URL placeholder
            if (arg.equals("URL_PLACEHOLDER") || arg.startsWith("http://") || arg.startsWith("https://")) {
                command.add(inputUrl);
            } else {
                command.add(arg);
            }
        }
        
        // Ensure URL is at the end if not already added
        if (!command.contains(inputUrl)) {
            command.add(inputUrl);
        }
        
        return command;
    }
    
    private ProcessResult executeProcess(List<String> command, int timeoutSeconds, long startTime) {
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            process = pb.start();
            
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            
            // Create final references for lambda capture
            final Process proc = process;
            
            // Read stdout
            Thread stdoutReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdout.append(line).append("\n");
                    }
                } catch (Exception ignored) {}
            });
            
            // Read stderr
            Thread stderrReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderr.append(line).append("\n");
                    }
                } catch (Exception ignored) {}
            });
            
            stdoutReader.start();
            stderrReader.start();
            
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            
            if (!completed) {
                process.destroyForcibly();
                stdoutReader.interrupt();
                stderrReader.interrupt();
                long elapsed = System.currentTimeMillis() - startTime;
                return new ProcessResult(-1, stdout.toString(), 
                        "Process timed out after " + timeoutSeconds + " seconds", elapsed, true);
            }
            
            stdoutReader.join(500);
            stderrReader.join(500);
            
            long elapsed = System.currentTimeMillis() - startTime;
            return new ProcessResult(
                    process.exitValue(), 
                    stdout.toString(), 
                    stderr.toString(), 
                    elapsed, 
                    false
            );
            
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            long elapsed = System.currentTimeMillis() - startTime;
            return new ProcessResult(-1, "", e.getMessage(), elapsed, false);
        }
    }
    
    /**
     * Result of a process execution
     */
    public static class ProcessResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;
        private final long executionTimeMs;
        private final boolean timedOut;
        
        public ProcessResult(int exitCode, String stdout, String stderr, long executionTimeMs, boolean timedOut) {
            this.exitCode = exitCode;
            this.stdout = stdout != null ? stdout : "";
            this.stderr = stderr != null ? stderr : "";
            this.executionTimeMs = executionTimeMs;
            this.timedOut = timedOut;
        }
        
        public int getExitCode() { return exitCode; }
        public String getStdout() { return stdout; }
        public String getStderr() { return stderr; }
        public String getOutput() { return stdout; } // Alias for compatibility
        public String getError() { return stderr; }  // Alias for compatibility
        public long getExecutionTimeMs() { return executionTimeMs; }
        public boolean isTimedOut() { return timedOut; }
        
        public boolean isSuccessful() { 
            return exitCode == 0 && !timedOut; 
        }
        
        /**
         * Determines if this failure suggests the URL needs download-first approach
         */
        public boolean suggestsDownloadFirst() {
            if (isSuccessful()) return false;
            
            String combined = (stdout + stderr).toLowerCase();
            
            // Common indicators of hotlink protection or access issues
            return combined.contains("403") ||
                   combined.contains("forbidden") ||
                   combined.contains("access denied") ||
                   combined.contains("server returned 4") ||
                   combined.contains("server returned 5") ||
                   combined.contains("connection refused") ||
                   combined.contains("invalid data") ||
                   (exitCode == 1 && !timedOut); // Generic failure often means access issue
        }
        
        @Override
        public String toString() {
            return String.format("ProcessResult[exitCode=%d, timedOut=%s, time=%dms, stdout=%d chars, stderr=%d chars]",
                    exitCode, timedOut, executionTimeMs, stdout.length(), stderr.length());
        }
    }
}
