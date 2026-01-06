package me.hash.mediaroulette.model.content;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MediaResult {
    private final String imageUrl;
    private final String title;
    private final String description;
    private final MediaSource source;
    private final String imageType;
    private final String imageContent;
    private final List<String> galleryUrls; // For multi-image galleries (up to 10)

    public MediaResult(String imageUrl, String title, String description, MediaSource source) {
        this(imageUrl, title, description, source, null, null, null);
    }

    public MediaResult(String imageUrl, String title, String description, MediaSource source, String imageType, String imageContent) {
        this(imageUrl, title, description, source, imageType, imageContent, null);
    }

    public MediaResult(String imageUrl, String title, String description, MediaSource source, String imageType, String imageContent, List<String> galleryUrls) {
        this.imageUrl = imageUrl;
        this.title = title;
        this.description = description;
        this.source = source;
        this.imageType = imageType;
        this.imageContent = imageContent;
        // Limit gallery to 10 items (Discord MediaGallery limit)
        if (galleryUrls != null && !galleryUrls.isEmpty()) {
            this.galleryUrls = galleryUrls.size() > 10 ? new ArrayList<>(galleryUrls.subList(0, 10)) : new ArrayList<>(galleryUrls);
        } else {
            this.galleryUrls = null;
        }
    }

    // Getters
    public String getImageUrl() { return imageUrl; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public MediaSource getSource() { return source; }
    public String getImageType() { return imageType; }
    public String getImageContent() { return imageContent; }
    public List<String> getGalleryUrls() { return galleryUrls; }

    /**
     * Check if this result is a gallery with multiple images
     */
    public boolean isGallery() {
        return galleryUrls != null && galleryUrls.size() > 1;
    }

    /**
     * Get the count of images in this result (1 for single, gallery size for galleries)
     */
    public int getImageCount() {
        return galleryUrls != null ? galleryUrls.size() : 1;
    }

    // For backward compatibility - returns Map format
    public Map<String, String> toMap() {
        Map<String, String> result = new HashMap<>();
        result.put("image", imageUrl);
        result.put("title", title);
        result.put("description", description);
        if (imageType != null) {
            result.put("image_type", imageType);
        }
        if (imageContent != null) {
            result.put("image_content", imageContent);
        }
        // Add gallery URLs as pipe-separated string
        if (galleryUrls != null && !galleryUrls.isEmpty()) {
            result.put("gallery_urls", String.join("|", galleryUrls));
        }
        return result;
    }
}