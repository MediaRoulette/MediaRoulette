package me.hash.mediaroulette.content.provider.impl.gifs;

import me.hash.mediaroulette.utils.DictionaryIntegration;
import me.hash.mediaroulette.model.content.MediaResult;
import me.hash.mediaroulette.model.content.MediaSource;
import me.hash.mediaroulette.content.provider.MediaProvider;
import me.hash.mediaroulette.content.http.HttpClientWrapper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public class TenorProvider implements MediaProvider {
    private final HttpClientWrapper httpClient;
    private final Random random = new Random();
    private final String apiKey;

    public TenorProvider(HttpClientWrapper httpClient, String apiKey) {
        this.httpClient = httpClient;
        this.apiKey = apiKey;
    }

    @Override
    public MediaResult getRandomMedia(String query) throws IOException, HttpClientWrapper.RateLimitException, InterruptedException {
        return getRandomMedia(query, null);
    }
    
    public MediaResult getRandomMedia(String query, String userId) throws IOException, HttpClientWrapper.RateLimitException, InterruptedException {
        if (query == null || query.isEmpty()) {
            if (userId != null) {
                query = DictionaryIntegration.getRandomWordForSource(userId, "tenor");
            } else {
                query = DictionaryIntegration.getRandomWordForSource("tenor");
            }
        }

        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = String.format("https://tenor.googleapis.com/v2/search?key=%s&q=%s&limit=50",
                apiKey, encodedQuery);

        String response = httpClient.getBody(url);
        JSONObject jsonObject = new JSONObject(response);

        if (!jsonObject.has("results")) {
            throw new IOException("No results found for query: " + query);
        }

        JSONArray resultsArray = jsonObject.getJSONArray("results");
        if (resultsArray.isEmpty()) {
            throw new IOException("No GIFs found for query: " + query);
        }

        int randomIndex = random.nextInt(resultsArray.length());
        JSONObject resultObject = resultsArray.getJSONObject(randomIndex);

        // Check if "media_formats" and "gif" keys exist
        if (!resultObject.has("media_formats")) {
            throw new JSONException("Key 'media_formats' not found in response");
        }

        JSONObject mediaFormats = resultObject.getJSONObject("media_formats");
        if (!mediaFormats.has("gif")) {
            throw new JSONException("Key 'gif' not found in media_formats");
        }

        String gifUrl = mediaFormats.getJSONObject("gif").getString("url");

        String description = String.format("🌐 Source: Tenor\n🔎 Query: %s", query);
        String title = "Here is your random Tenor GIF!";

        return new MediaResult(gifUrl, title, description, MediaSource.TENOR);
    }

    @Override
    public boolean supportsQuery() {
        return true;
    }

    @Override
    public String getProviderName() {
        return "Tenor";
    }
}