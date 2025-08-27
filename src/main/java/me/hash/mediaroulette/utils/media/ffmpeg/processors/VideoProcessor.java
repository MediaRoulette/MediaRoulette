package me.hash.mediaroulette.utils.media.ffmpeg.processors;

import me.hash.mediaroulette.utils.media.ffmpeg.config.FFmpegConfig;
import me.hash.mediaroulette.utils.media.ffmpeg.models.VideoInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Processor for video information operations using FFprobe
 */
public class VideoProcessor extends BaseProcessor {
    private static final Logger logger = LoggerFactory.getLogger(VideoProcessor.class);

    public VideoProcessor(FFmpegConfig config) {
        super(config);
    }

    /**
     * Gets video information using ffprobe
     */
    public CompletableFuture<VideoInfo> getVideoInfo(String videoUrl) {
        List<String> command = new ArrayList<>();
        command.add("ffprobe"); // Will be replaced with actual path
        command.add("-v");
        command.add("quiet");
        command.add("-print_format");
        command.add("json");
        command.add("-show_format");
        command.add("-show_streams");
        command.add(videoUrl);

        return executeFFprobeCommand(command, config.getVideoInfoTimeoutSeconds())
                .thenApply(result -> {
                    if (!result.isSuccessful()) {
                        String errorMsg = "FFprobe failed with exit code " + result.getExitCode();
                        if (!result.getError().isEmpty()) {
                            errorMsg += ". Error: " + result.getError();
                        }
                        errorMsg += ". URL: " + videoUrl;
                        throw new RuntimeException(errorMsg);
                    }

                    return parseVideoInfo(result.getOutput());
                });
    }

    /**
     * Parses JSON output from ffprobe into VideoInfo object
     */
    private VideoInfo parseVideoInfo(String jsonOutput) {
        VideoInfo info = new VideoInfo();

        try {
            // Parse duration
            if (jsonOutput.contains("\"duration\"")) {
                String durationStr = extractJsonValue(jsonOutput, "duration");
                if (!durationStr.equals("0")) {
                    info.setDuration(Double.parseDouble(durationStr));
                }
            }

            // Parse video stream information
            if (jsonOutput.contains("\"codec_type\":\"video\"")) {
                // Find the video stream section
                int videoStreamStart = jsonOutput.indexOf("\"codec_type\":\"video\"");
                int videoStreamEnd = jsonOutput.indexOf("}", videoStreamStart);
                String videoStream = jsonOutput.substring(videoStreamStart, videoStreamEnd);

                // Extract width and height from video stream
                String widthStr = extractJsonValue(videoStream, "width");
                String heightStr = extractJsonValue(videoStream, "height");
                String codecStr = extractJsonValue(videoStream, "codec_name");

                if (!widthStr.equals("0")) {
                    info.setWidth(Integer.parseInt(widthStr));
                }
                if (!heightStr.equals("0")) {
                    info.setHeight(Integer.parseInt(heightStr));
                }
                if (!codecStr.isEmpty()) {
                    info.setCodec(codecStr);
                }
            }

            // Parse format information
            if (jsonOutput.contains("\"format\"")) {
                String formatStr = extractJsonValue(jsonOutput, "format_name");
                if (!formatStr.isEmpty()) {
                    info.setFormat(formatStr);
                }

                String bitrateStr = extractJsonValue(jsonOutput, "bit_rate");
                if (!bitrateStr.equals("0")) {
                    try {
                        info.setBitrate(Long.parseLong(bitrateStr));
                    } catch (NumberFormatException e) {
                        // Ignore invalid bitrate
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Failed to parse video info: {}", e.getMessage(), e);
        }

        return info;
    }

    /**
     * Extracts a value from JSON string (simple parser for specific use case)
     */
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) return "0";

        startIndex += searchKey.length();
        
        // Skip whitespace
        while (startIndex < json.length() && Character.isWhitespace(json.charAt(startIndex))) {
            startIndex++;
        }

        // Handle quoted strings
        boolean isQuoted = startIndex < json.length() && json.charAt(startIndex) == '"';
        if (isQuoted) {
            startIndex++; // Skip opening quote
            int endIndex = json.indexOf("\"", startIndex);
            if (endIndex == -1) return "";
            return json.substring(startIndex, endIndex);
        } else {
            // Handle numbers
            int endIndex = startIndex;
            while (endIndex < json.length() && 
                   (Character.isDigit(json.charAt(endIndex)) || 
                    json.charAt(endIndex) == '.' || 
                    json.charAt(endIndex) == '-')) {
                endIndex++;
            }
            
            if (endIndex == startIndex) return "0";
            return json.substring(startIndex, endIndex);
        }
    }
}