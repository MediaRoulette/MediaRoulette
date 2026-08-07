package dev.mediaroulette.utils.media.ffmpeg.models;

/**
 * Model class for video information
 */
public class VideoInfo {
    private double duration;
    private int width;
    private int height;
    private String codec;
    private String format;
    private long bitrate;

    public VideoInfo() {}

    public VideoInfo(double duration, int width, int height, String codec) {
        this.duration = duration;
        this.width = width;
        this.height = height;
        this.codec = codec;
    }

    // Getters and setters
    public double getDuration() { return duration; }
    public void setDuration(double duration) { this.duration = duration; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public String getCodec() { return codec; }
    public void setCodec(String codec) { this.codec = codec; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public long getBitrate() { return bitrate; }
    public void setBitrate(long bitrate) { this.bitrate = bitrate; }

    // Utility methods
    public String getResolution() {
        return width + "x" + height;
    }

    public double getAspectRatio() {
        return height > 0 ? (double) width / height : 1.0;
    }

    public String getFormattedDuration() {
        int minutes = (int) (duration / 60);
        int seconds = (int) (duration % 60);
        return String.format("%d:%02d", minutes, seconds);
    }

    public boolean isValidForGif() {
        return duration > 0 && width > 0 && height > 0;
    }

    @Override
    public String toString() {
        return String.format("VideoInfo{duration=%.2fs, resolution=%dx%d, codec='%s', format='%s'}",
                duration, width, height, codec, format);
    }
}