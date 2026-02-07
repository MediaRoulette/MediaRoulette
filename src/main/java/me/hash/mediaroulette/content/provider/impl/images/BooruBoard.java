package me.hash.mediaroulette.content.provider.impl.images;

import me.hash.mediaroulette.content.http.HttpClientWrapper;
import me.hash.mediaroulette.model.content.MediaResult;
import me.hash.mediaroulette.model.content.MediaSource;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enum representing supported booru image boards.
 * Each board defines its own URL and image extraction logic.
 */
public enum BooruBoard {
    RULE34("rule34", "Rule34", "https://rule34.xxx/index.php?page=post&s=random", MediaSource.RULE34),
    GELBOORU("gelbooru", "Gelbooru", "https://gelbooru.com/index.php?page=post&s=random", MediaSource.register("GELBOORU", "Gelbooru")),
    SAFEBOORU("safebooru", "Safebooru", "https://safebooru.org/index.php?page=post&s=random", MediaSource.register("SAFEBOORU", "Safebooru")),
    TBIB("tbib", "TBIB", "https://tbib.org/index.php?page=post&s=random", MediaSource.register("TBIB", "TBIB"));

    private static final Logger logger = LoggerFactory.getLogger(BooruBoard.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    private static final int TIMEOUT_MS = 10000;

    private final String id;
    private final String displayName;
    private final String randomUrl;
    private final MediaSource mediaSource;

    BooruBoard(String id, String displayName, String randomUrl, MediaSource mediaSource) {
        this.id = id;
        this.displayName = displayName;
        this.randomUrl = randomUrl;
        this.mediaSource = mediaSource;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getRandomUrl() { return randomUrl; }
    public MediaSource getMediaSource() { return mediaSource; }

    /**
     * Fetch a random image from this board using og:image extraction.
     * Subclasses/future boards can override this if they need different logic.
     */
    public MediaResult fetch(HttpClientWrapper httpClient) throws Exception {
        String imageUrl = null;
        String finalUrl = null;

        try {
            Connection connection = Jsoup.connect(randomUrl)
                    .followRedirects(true)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS);

            Connection.Response response = connection.execute();
            finalUrl = response.url().toString();
            Document doc = response.parse();

            // Try og:image meta tag first
            Elements ogImage = doc.select("meta[property=og:image]");
            if (!ogImage.isEmpty()) {
                String content = ogImage.attr("content");
                if (!content.isEmpty()) {
                    imageUrl = content;
                }
            }

            // Fallback to #image element
            if (imageUrl == null) {
                Elements image = doc.select("#image");
                if (!image.isEmpty()) {
                    String srcAttr = image.attr("src");
                    if (srcAttr.startsWith("//")) {
                        imageUrl = "https:" + srcAttr;
                    } else if (srcAttr.startsWith("/")) {
                        imageUrl = randomUrl.substring(0, randomUrl.indexOf("/", 8)) + srcAttr;
                    } else {
                        imageUrl = srcAttr;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("{} scraping failed: {}", displayName, e.getMessage());
            throw e;
        }

        String description = "Source: " + displayName + "\nURL: " + (finalUrl != null ? finalUrl : "N/A");
        String title = "Here is your random " + displayName + " picture!";

        return new MediaResult(imageUrl, title, description, mediaSource);
    }

    /**
     * Get a random board with equal probability.
     */
    public static BooruBoard random() {
        BooruBoard[] boards = values();
        return boards[ThreadLocalRandom.current().nextInt(boards.length)];
    }

    /**
     * Find a board by its ID (case-insensitive).
     */
    public static Optional<BooruBoard> byId(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        String lowerId = id.toLowerCase().trim();
        return Arrays.stream(values())
                .filter(b -> b.id.equals(lowerId) || b.displayName.equalsIgnoreCase(lowerId))
                .findFirst();
    }

    /**
     * Get all boards for autocomplete.
     */
    public static List<BooruBoard> getAll() {
        return Arrays.asList(values());
    }
}
