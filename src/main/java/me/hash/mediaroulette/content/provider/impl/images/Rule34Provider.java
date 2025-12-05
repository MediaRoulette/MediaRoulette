package me.hash.mediaroulette.content.provider.impl.images;

import me.hash.mediaroulette.model.content.MediaResult;
import me.hash.mediaroulette.model.content.MediaSource;
import me.hash.mediaroulette.content.provider.MediaProvider;
import me.hash.mediaroulette.content.http.HttpClientWrapper;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Rule34Provider implements MediaProvider {
    private static final Logger logger = LoggerFactory.getLogger(Rule34Provider.class);
    private final HttpClientWrapper httpClient;

    public Rule34Provider(HttpClientWrapper httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public MediaResult getRandomMedia(String query) throws IOException, HttpClientWrapper.RateLimitException, InterruptedException {
        try {
            String apiUrl = "https://rule34.xxx/index.php?page=post&s=random";

            String imageUrl = null;
            String finalUrl = null;

            try {
                // Configure Jsoup to follow redirects and capture the final URL
                Connection connection = Jsoup.connect(apiUrl)
                        .followRedirects(true)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .timeout(10000);

                Connection.Response response = connection.execute();
                finalUrl = response.url().toString(); // Get the final redirected URL

                Document doc = response.parse();

                // First try to get the full image URL from og:image meta tag (more reliable)
                Elements ogImage = doc.select("meta[property=og:image]");
                if (!ogImage.isEmpty()) {
                    String content = ogImage.attr("content");
                    if (!content.isEmpty()) {
                        imageUrl = content;
                    }
                }

                // Fallback to #image element if og:image not found
                if (imageUrl == null) {
                    Elements image = doc.select("#image");
                    if (!image.isEmpty()) {
                        String srcAttr = image.attr("src");

                        // Handle relative URLs that start with //
                        if (srcAttr.startsWith("//")) {
                            imageUrl = "https:" + srcAttr;
                        } else if (srcAttr.startsWith("/")) {
                            imageUrl = "https://rule34.xxx" + srcAttr;
                        } else {
                            imageUrl = srcAttr;
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Rule34 scraping failed {}", e.getMessage());
            }

            String description = "Source: Rule34 - NSFW Content\nURL: " + (finalUrl != null ? finalUrl : "N/A");
            String title = "Here is your random Rule34 (NSFW) picture!";

            return new MediaResult(imageUrl, title, description, MediaSource.RULE34);
        } catch (Exception e) {
            throw new IOException("Failed to get Rule34 image: " + e.getMessage());
        }
    }

    @Override
    public boolean supportsQuery() {
        return false; // Rule34 random endpoint doesn't support queries
    }

    @Override
    public String getProviderName() {
        return "Rule34";
    }
}