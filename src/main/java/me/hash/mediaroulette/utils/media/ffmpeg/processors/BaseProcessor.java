package me.hash.mediaroulette.utils.media.ffmpeg.processors;

import me.hash.mediaroulette.utils.media.ffmpeg.config.FFmpegConfig;
import me.hash.mediaroulette.utils.media.FFmpegDownloader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public abstract class BaseProcessor {
    protected final FFmpegConfig config;

    protected BaseProcessor(FFmpegConfig config) {
        this.config = config;
    }

    protected CompletableFuture<ProcessResult> executeFFmpegCommand(List<String> command, int timeoutSeconds) {
        return FFmpegDownloader.getFFmpegPath().thenCompose(ffmpegPath ->
            CompletableFuture.supplyAsync(() -> {
                try {
                    config.getFileManager().ensureTempDirectoryExists();
                    command.set(0, ffmpegPath.toString());

                    ProcessBuilder pb = new ProcessBuilder(command);
                    pb.redirectErrorStream(true);
                    Process proc = pb.start();

                    StringBuilder out = new StringBuilder();
                    Thread reader = new Thread(() -> {
                        try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                            String line;
                            while ((line = r.readLine()) != null) out.append(line).append("\n");
                        } catch (Exception ignored) {}
                    });
                    reader.start();

                    boolean done = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                    if (!done) {
                        proc.destroyForcibly();
                        reader.interrupt();
                        throw new RuntimeException("FFmpeg timed out after " + timeoutSeconds + "s");
                    }
                    reader.join(500);
                    return new ProcessResult(proc.exitValue(), out.toString(), "");
                } catch (Exception e) {
                    throw new RuntimeException("FFmpeg failed: " + e.getMessage(), e);
                }
            }));
    }

    protected CompletableFuture<ProcessResult> executeFFprobeCommand(List<String> command, int timeoutSeconds) {
        return FFmpegDownloader.getFFprobePath().thenCompose(ffprobePath ->
            CompletableFuture.supplyAsync(() -> {
                try {
                    command.set(0, ffprobePath.toString());

                    ProcessBuilder pb = new ProcessBuilder(command);
                    pb.redirectErrorStream(false);
                    Process proc = pb.start();

                    StringBuilder out = new StringBuilder();
                    StringBuilder err = new StringBuilder();

                    Thread outReader = new Thread(() -> {
                        try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                            String line;
                            while ((line = r.readLine()) != null) out.append(line).append("\n");
                        } catch (Exception ignored) {}
                    });

                    Thread errReader = new Thread(() -> {
                        try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                            String line;
                            while ((line = r.readLine()) != null) err.append(line).append("\n");
                        } catch (Exception ignored) {}
                    });

                    outReader.start();
                    errReader.start();

                    boolean done = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                    if (!done) {
                        proc.destroyForcibly();
                        outReader.interrupt();
                        errReader.interrupt();
                        throw new RuntimeException("FFprobe timed out after " + timeoutSeconds + "s");
                    }

                    outReader.join(500);
                    errReader.join(500);
                    return new ProcessResult(proc.exitValue(), out.toString(), err.toString());
                } catch (Exception e) {
                    throw new RuntimeException("FFprobe failed: " + e.getMessage(), e);
                }
            }));
    }

    protected static class ProcessResult {
        private final int exitCode;
        private final String output;
        private final String error;

        public ProcessResult(int exitCode, String output, String error) {
            this.exitCode = exitCode;
            this.output = output;
            this.error = error;
        }

        public int getExitCode() { return exitCode; }
        public String getOutput() { return output; }
        public String getError() { return error; }
        public boolean isSuccessful() { return exitCode == 0; }
    }
}