package me.hash.mediaroulette.utils.media.ffmpeg.processors;

import me.hash.mediaroulette.utils.media.ffmpeg.config.FFmpegConfig;
import me.hash.mediaroulette.utils.media.ffmpeg.models.VideoInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class VideoProcessor extends BaseProcessor {

    public VideoProcessor(FFmpegConfig config) {
        super(config);
    }

    public CompletableFuture<VideoInfo> getVideoInfo(String videoUrl) {
        List<String> cmd = new ArrayList<>();
        cmd.add("ffprobe");
        cmd.add("-v");
        cmd.add("quiet");
        cmd.add("-print_format");
        cmd.add("json");
        cmd.add("-show_format");
        cmd.add("-show_streams");
        cmd.add("-select_streams");
        cmd.add("v:0");
        cmd.add(videoUrl);

        return executeFFprobeCommand(cmd, config.getVideoInfoTimeoutSeconds())
                .thenApply(result -> {
                    if (!result.isSuccessful()) {
                        throw new RuntimeException("FFprobe failed for: " + videoUrl);
                    }
                    return parseVideoInfo(result.getOutput());
                });
    }

    private VideoInfo parseVideoInfo(String json) {
        VideoInfo info = new VideoInfo();
        try {
            String dur = extractValue(json, "duration");
            if (!dur.equals("0")) info.setDuration(Double.parseDouble(dur));

            String w = extractValue(json, "width");
            if (!w.equals("0")) info.setWidth(Integer.parseInt(w));

            String h = extractValue(json, "height");
            if (!h.equals("0")) info.setHeight(Integer.parseInt(h));

            String codec = extractValue(json, "codec_name");
            if (!codec.isEmpty()) info.setCodec(codec);

            String fmt = extractValue(json, "format_name");
            if (!fmt.isEmpty()) info.setFormat(fmt);

            String br = extractValue(json, "bit_rate");
            if (!br.equals("0")) {
                try { info.setBitrate(Long.parseLong(br)); } catch (NumberFormatException ignored) {}
            }
        } catch (Exception ignored) {}
        return info;
    }

    private String extractValue(String json, String key) {
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx == -1) return "0";

        idx += search.length();
        while (idx < json.length() && Character.isWhitespace(json.charAt(idx))) idx++;

        if (idx < json.length() && json.charAt(idx) == '"') {
            idx++;
            int end = json.indexOf("\"", idx);
            return end == -1 ? "" : json.substring(idx, end);
        } else {
            int end = idx;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.' || json.charAt(end) == '-')) {
                end++;
            }
            return end == idx ? "0" : json.substring(idx, end);
        }
    }
}