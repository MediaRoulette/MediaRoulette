package dev.mediaroulette.model.content;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Serializable wrapper for MediaResult to enable persistent caching
 */
public class CachedMediaResult {
    private final String imageUrl;
    private final String title;
    private final String description;
    private final String source;
    private final String imageType;
    private final String imageContent;
    private final long timestamp;
    
    @JsonCreator
    public CachedMediaResult(
            @JsonProperty("imageUrl") String imageUrl,
            @JsonProperty("title") String title,
            @JsonProperty("description") String description,
            @JsonProperty("source") String source,
            @JsonProperty("imageType") String imageType,
            @JsonProperty("imageContent") String imageContent,
            @JsonProperty("timestamp") long timestamp) {
        this.imageUrl = imageUrl;
        this.title = title;
        this.description = description;
        this.source = source;
        this.imageType = imageType;
        this.imageContent = imageContent;
        this.timestamp = timestamp;
    }
    
    // Constructor from MediaResult
    public CachedMediaResult(MediaResult mediaResult) {
        this.imageUrl = mediaResult.getImageUrl();
        this.title = mediaResult.getTitle();
        this.description = mediaResult.getDescription();
        this.source = mediaResult.getSource() != null ? mediaResult.getSource().name() : null;
        this.imageType = mediaResult.getImageType();
        this.imageContent = mediaResult.getImageContent();
        this.timestamp = System.currentTimeMillis();
    }
    
    // Convert back to MediaResult
    public MediaResult toMediaResult() {
        MediaSource mediaSource = source != null ? MediaSource.valueOf(source) : null;
        return new MediaResult(imageUrl, title, description, mediaSource, imageType, imageContent);
    }
    
    // Check if this cached result is still valid (not too old)
    public boolean isValid(long maxAgeMs) {
        return (System.currentTimeMillis() - timestamp) < maxAgeMs;
    }
    
    // Getters for Jackson serialization
    public String getImageUrl() { return imageUrl; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getSource() { return source; }
    public String getImageType() { return imageType; }
    public String getImageContent() { return imageContent; }
    public long getTimestamp() { return timestamp; }
}