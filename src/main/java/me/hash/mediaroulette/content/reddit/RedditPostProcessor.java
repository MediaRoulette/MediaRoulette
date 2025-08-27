package me.hash.mediaroulette.content.reddit;

import me.hash.mediaroulette.model.content.MediaResult;
import me.hash.mediaroulette.model.content.MediaSource;
import okhttp3.OkHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RedditPostProcessor {
    private static final Logger logger = LoggerFactory.getLogger(RedditPostProcessor.class);
    private final OkHttpClient httpClient = new OkHttpClient();

    // RedGifs API patterns
    private static final Pattern REDGIFS_URL_PATTERN = Pattern.compile(
            "https?://(?:[a-z0-9]+\\.)?redgifs\\.com/(?:watch/|ifr/)?[a-zA-Z0-9]+(?:\\.[a-z0-9]+)?",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    private static final Pattern GFYCAT_URL_PATTERN = Pattern.compile("https?://(?:www\\.)?gfycat\\.com/([a-zA-Z0-9]+)");

    // Minimum image dimensions for quality filtering
    private static final int MIN_WIDTH = 400;
    private static final int MIN_HEIGHT = 300;
    private static final int MIN_AREA = MIN_WIDTH * MIN_HEIGHT;

    public List<MediaResult> processPosts(JSONArray posts) {
        List<MediaResult> results = new ArrayList<>();
        for (int i = 0; i < posts.length(); i++) {
            try {
                JSONObject postData = posts.getJSONObject(i).getJSONObject("data");
                List<MediaResult> postResults = processPost(postData);
                results.addAll(postResults);
            } catch (Exception e) {
                logger.error("Error processing post: {0}", e.getMessage());
            }
        }
        return results;
    }

    public List<MediaResult> processPost(JSONObject postData) {
        List<MediaResult> results = new ArrayList<>();
        String title = postData.optString("title", "Reddit Post");
        String description = buildDescription(postData);

        // Check if it's a gallery post first
        if (postData.optBoolean("is_gallery", false)) {
            results.addAll(processGalleryPost(postData, title, description));
        } else {
            // Process single media post
            MediaResult singleResult = processSingleMediaPost(postData, title, description);
            results.add(singleResult);
        }

        return results;
    }

    private List<MediaResult> processGalleryPost(JSONObject postData, String title, String description) {
        List<MediaResult> results = new ArrayList<>();

        JSONObject galleryData = postData.optJSONObject("gallery_data");
        JSONObject mediaMetadata = postData.optJSONObject("media_metadata");

        if (galleryData == null || mediaMetadata == null) {
            return results;
        }

        JSONArray items = galleryData.optJSONArray("items");
        if (items == null || items.isEmpty()) {
            logger.warn("Gallery post has no items");
            return results;
        }

        for (int i = 0; i < items.length(); i++) {
            try {
                JSONObject item = items.getJSONObject(i);
                String mediaId = item.optString("media_id");

                if (mediaId.isEmpty() || !mediaMetadata.has(mediaId)) {
                    continue;
                }

                JSONObject media = mediaMetadata.getJSONObject(mediaId);
                String imageUrl = extractUrlFromMediaMetadata(media);

                if (isValidMediaUrl(imageUrl)) {
                    String galleryTitle = String.format("%s (Image %d/%d)", title, i + 1, items.length());
                    results.add(new MediaResult(imageUrl, galleryTitle, description, MediaSource.REDDIT, null, null));
                }
            } catch (Exception e) {
                logger.error("Error processing gallery item {0}: {1}", new Object[]{i, e.getMessage()});
            }
        }

        return results;
    }

    private MediaResult processSingleMediaPost(JSONObject postData, String title, String description) {
        String imageUrl = extractImageUrl(postData);
        String imageContent = null;
        String imageType = null;

        if (imageUrl.equals("attachment://image.png")) {
            // Create a brief text content from the post title or text
            imageContent = generateBriefContent(postData, title);
            imageType = "create";
            imageUrl = "attachment://image.png";
        } else {
            // Handle external media URLs (RedGifs, Gfycat, etc.)
            imageUrl = resolveExternalMediaUrl(imageUrl);
        }

        return new MediaResult(imageUrl, title, description, MediaSource.REDDIT, imageType, imageContent);
    }

    private String generateBriefContent(JSONObject postData, String title) {
        String content = null;

        // Try to get text content from the post
        String selfText = postData.optString("selftext", "").trim();
        if (!selfText.isEmpty()) {
            content = truncateText(selfText);
        }

        // If no selftext, use title
        if (content == null || content.isEmpty()) {
            content = truncateText(title);
        }

        // If still empty, use subreddit info
        if (content.isEmpty()) {
            String subreddit = postData.optString("subreddit", "Reddit");
            content = "From r/" + subreddit;
            content = truncateText(content);
        }

        return content;
    }

    private String truncateText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        text = text.trim();
        if (text.length() <= 30) {
            return text;
        }

        // Find a good breaking point (space, punctuation)
        int breakPoint = 30;
        for (int i = 30 - 1; i > 30 / 2; i--) {
            char c = text.charAt(i);
            if (c == ' ' || c == '.' || c == ',' || c == '!' || c == '?') {
                breakPoint = i;
                break;
            }
        }

        return text.substring(0, breakPoint).trim() + "...";
    }

    private String extractImageUrl(JSONObject postData) {
        // Try external URLs first (RedGifs, Gfycat, etc.)
        String url = postData.optString("url", "");
        if (!url.isEmpty()) {
            String resolvedUrl = resolveExternalMediaUrl(url);
            if (!resolvedUrl.equals(url)) {
                return resolvedUrl; // External URL was resolved
            }
            if (isValidMediaUrl(url)) {
                return url; // Direct media URL
            }
        }

        // Try video post previews
        String imageUrl = extractFromVideoPreview(postData);
        if (imageUrl != null) return imageUrl;

        // Try preview images
        imageUrl = extractFromPreview(postData);
        if (imageUrl != null) return imageUrl;

        // Try thumbnail as last resort
        imageUrl = extractFromThumbnail(postData);
        if (imageUrl != null) return imageUrl;

        // Fallback
        return "attachment://image.png";
    }

    private String extractUrlFromMediaMetadata(JSONObject media) {
        // Try different resolution options
        String[] resolutionKeys = {"s", "p", "m"};

        for (String key : resolutionKeys) {
            JSONObject resObj = media.optJSONObject(key);
            if (resObj != null) {
                String url = resObj.optString("u", "").replaceAll("&amp;", "&");
                int width = resObj.optInt("x", 0);
                int height = resObj.optInt("y", 0);

                if (isValidMediaUrl(url) && width >= MIN_WIDTH && height >= MIN_HEIGHT) {
                    return url;
                }
            }
        }

        return null;
    }

    private String extractFromVideoPreview(JSONObject postData) {
        String postHint = postData.optString("post_hint", "");
        if (!("rich:video".equals(postHint) || "hosted:video".equals(postHint))) {
            return null;
        }

        return extractFromPreview(postData);
    }

    private String extractFromPreview(JSONObject postData) {
        if (!postData.has("preview")) {
            return null;
        }

        JSONObject preview = postData.getJSONObject("preview");
        JSONArray images = preview.optJSONArray("images");
        if (images == null || images.isEmpty()) {
            return null;
        }

        return findBestPreviewImage(images);
    }

    private String extractFromThumbnail(JSONObject postData) {
        String thumbnail = postData.optString("thumbnail", "");
        if (!thumbnail.startsWith("http")) {
            return null;
        }

        return isValidMediaUrl(thumbnail) ? thumbnail : null;
    }

    private String findBestPreviewImage(JSONArray images) {
        double bestArea = 0;
        String bestUrl = null;

        for (int i = 0; i < images.length(); i++) {
            try {
                JSONObject imgObj = images.getJSONObject(i);

                // Try resolutions array first
                JSONArray resolutions = imgObj.optJSONArray("resolutions");
                if (resolutions != null && !resolutions.isEmpty()) {
                    for (int j = resolutions.length() - 1; j >= 0; j--) {
                        JSONObject res = resolutions.getJSONObject(j);
                        String url = res.optString("url", "").replaceAll("&amp;", "&");
                        int width = res.optInt("width", 0);
                        int height = res.optInt("height", 0);
                        double area = width * height;

                        if (isValidMediaUrl(url) && area > bestArea && area >= MIN_AREA) {
                            bestArea = area;
                            bestUrl = url;
                        }
                    }
                }

                // Try source as fallback
                JSONObject source = imgObj.optJSONObject("source");
                if (source != null && bestUrl == null) {
                    String url = source.optString("url", "").replaceAll("&amp;", "&");
                    int width = source.optInt("width", 0);
                    int height = source.optInt("height", 0);
                    double area = width * height;

                    if (isValidMediaUrl(url) && area >= MIN_AREA) {
                        bestUrl = url;
                    }
                }
            } catch (Exception e) {
                logger.error("Error processing preview image: {0}", e.getMessage());
            }
        }

        return bestUrl;
    }

    private String resolveExternalMediaUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }

        try {
            // Handle RedGifs URLs
            Matcher redgifsMatcher = REDGIFS_URL_PATTERN.matcher(url);
            if (redgifsMatcher.find()) {
                return redgifsMatcher.group();
            }

            // Handle Gfycat URLs (many now redirect to RedGifs)
            Matcher gfycatMatcher = GFYCAT_URL_PATTERN.matcher(url);
            if (gfycatMatcher.find()) {
                String redgifsUrl = gfycatMatcher.group();
                // Try RedGifs first, then fallback to Gfycat
                return Objects.requireNonNullElseGet(redgifsUrl, () -> resolveGfycatUrl(redgifsUrl));
            }

            // Handle other external URLs
            if (url.contains("imgur.com") && !url.contains(".gif") && !url.contains(".jpg") && !url.contains(".png")) {
                return resolveImgurUrl(url);
            }

        } catch (Exception e) {
            logger.error("Error resolving external URL {0}: {1}", url, e.getMessage());
        }

        return url;
    }

    private String resolveGfycatUrl(String gifId) {
        // Gfycat direct URLs (many are now redirected to RedGifs)
        return "https://thumbs.gfycat.com/" + gifId + "-mobile.mp4";
    }

    private String resolveImgurUrl(String url) {
        // Convert Imgur gallery/album URLs to direct image URLs
        if (url.contains("/gallery/") || url.contains("/a/")) {
            return url; // Return as-is for now, could be enhanced with Imgur API
        }

        // Add file extension if missing
        if (!url.contains(".")) {
            return url + ".jpg";
        }

        return url;
    }

    private String buildDescription(JSONObject postData) {
        String subreddit = postData.optString("subreddit", "unknown");
        String title = postData.optString("title", "");
        String permalink = postData.optString("permalink", "");

        return String.format("🌐 Source: Reddit\n🔎 Subreddit: %s\n✏️ Title: %s\n🔗 Post Link: <%s>",
                subreddit, title, "https://www.reddit.com" + permalink);
    }

    private boolean isValidMediaUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        // Direct media file extensions
        if (url.endsWith(".jpg") || url.endsWith(".jpeg") || url.endsWith(".png") ||
                url.endsWith(".gif") || url.endsWith(".webp") || url.endsWith(".mp4") ||
                url.endsWith(".webm") || url.endsWith(".mov")) {
            return true;
        }

        // Known media hosting domains
        return url.contains("giphy.com") || url.contains("tenor.com") ||
                url.contains("gfycat.com") || url.contains("redgifs.com") ||
                url.contains("streamable.com") || url.contains("imgur.com") ||
                url.contains("i.redd.it") || url.contains("preview.redd.it") ||
                url.contains("external-preview.redd.it");
    }
}