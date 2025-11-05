package me.hash.mediaroulette.content.provider.impl.images;

import me.hash.mediaroulette.utils.DictionaryIntegration;
import me.hash.mediaroulette.model.content.MediaResult;
import me.hash.mediaroulette.model.content.MediaSource;
import me.hash.mediaroulette.content.provider.MediaProvider;
import me.hash.mediaroulette.content.http.HttpClientWrapper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GoogleProvider implements MediaProvider {
    private final Map<String, Queue<MediaResult>> imageCache = new ConcurrentHashMap<>();
    private final HttpClientWrapper httpClient;
    private final Random random = new Random();
    private final String apiKey;
    private final String cseId;

    public GoogleProvider(HttpClientWrapper httpClient, String apiKey, String cseId) {
        this.httpClient = httpClient;
        this.apiKey = apiKey;
        this.cseId = cseId;
    }

    @Override
    public MediaResult getRandomMedia(String query) throws IOException, HttpClientWrapper.RateLimitException, InterruptedException {
        return getRandomMedia(query, null);
    }
    
    public MediaResult getRandomMedia(String query, String userId) throws IOException, HttpClientWrapper.RateLimitException, InterruptedException {
        if (query == null || query.isEmpty()) {
            if (userId != null) {
                query = DictionaryIntegration.getRandomWordForSource(userId, "google");
            } else {
                query = DictionaryIntegration.getRandomWordForSource("google");
            }
        }

        Queue<MediaResult> cache = imageCache.computeIfAbsent(query, k -> new LinkedList<>());

        if (cache.isEmpty()) {
            populateCache(query);
        }

        MediaResult result = cache.poll();
        if (result == null) {
            throw new IOException("No images available for query: " + query);
        }
        return result;
    }

    private void populateCache(String query) throws IOException, HttpClientWrapper.RateLimitException, InterruptedException {
        int start = random.nextInt(5) + 1;
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = String.format(
                "https://www.googleapis.com/customsearch/v1?key=%s&cx=%s&q=%s&searchType=image&start=%d",
                apiKey, cseId, encodedQuery, start);

        String response = httpClient.getBody(url);
        JSONObject json = new JSONObject(response);

        if (!json.has("items")) {
            throw new IOException("No search results found for query: " + query);
        }

        JSONArray items = json.getJSONArray("items");
        List<MediaResult> images = new ArrayList<>();

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String imageUrl = item.getString("link");
            String snippet = item.optString("snippet", "No description available");

            String description = String.format("Source: Google\nQuery: %s\nTitle: %s",
                    query, snippet);
            String title = "Here is your random Google search image!";

            images.add(new MediaResult(imageUrl, title, description, MediaSource.GOOGLE));
        }

        Queue<MediaResult> cache = imageCache.get(query);
        cache.addAll(images);
    }

    @Override
    public boolean supportsQuery() {
        return true;
    }

    @Override
    public String getProviderName() {
        return "Google Custom Search";
    }
}