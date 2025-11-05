package me.hash.mediaroulette.content.provider.impl.videos;

import me.hash.mediaroulette.model.content.MediaResult;
import me.hash.mediaroulette.model.content.MediaSource;
import me.hash.mediaroulette.content.provider.MediaProvider;
import me.hash.mediaroulette.content.http.HttpClientWrapper;
import me.hash.mediaroulette.utils.discord.DiscordTimestamp;
import me.hash.mediaroulette.utils.discord.DiscordTimestampType;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class YouTubeShortsProvider implements MediaProvider {
    private final List<MediaResult> shortsPool = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> seenShortsIds = Collections.synchronizedSet(new HashSet<>());
    private final HttpClientWrapper httpClient;
    private final String apiKey;

    // Constants for controlling the pool size and fetch quantity
    private static final int POOL_MIN_SIZE = 100;
    private static final int POOL_MAX_SIZE = 500;
    private static final int FETCH_SIZE = 50;

    public YouTubeShortsProvider(HttpClientWrapper httpClient, String apiKey) {
        this.httpClient = httpClient;
        this.apiKey = apiKey;
    }

    @Override
    public MediaResult getRandomMedia(String query) throws IOException, HttpClientWrapper.RateLimitException, InterruptedException {
        // If the pool is running low, trigger a refill.
        if (shortsPool.size() < POOL_MIN_SIZE) {
            refillPool();
        }

        // If the pool is not empty, pull a random Short.
        if (!shortsPool.isEmpty()) {
            int index = ThreadLocalRandom.current().nextInt(shortsPool.size());
            return shortsPool.remove(index);
        }

        // If the pool is empty after a refill attempt, no content is available.
        throw new IOException("No YouTube Shorts available after refill attempt.");
    }

    /**
     * Refills the video pool using one of several randomly selected search strategies.
     */
    private void refillPool() throws IOException, HttpClientWrapper.RateLimitException, InterruptedException {
        // Do not refill if the pool is already full.
        if (shortsPool.size() >= POOL_MAX_SIZE) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double roll = random.nextDouble();
        String url;

        // Randomly select a search strategy to ensure content diversity.
        if (roll < 0.5) {
            // 50% chance: Search for popular topics combined with #shorts.
            url = buildTopicShortsUrl(random);
        } else if (roll < 0.85) {
            // 35% chance: Search for viral Shorts from a specific past time frame.
            url = buildTimeBasedShortsUrl(random);
        } else {
            // 15% chance: Use a "chaos" search to find obscure or unique Shorts.
            url = buildChaosShortsUrl(random);
        }

        String response = httpClient.getBody(url);
        JSONObject jsonObject = new JSONObject(response);

        if (jsonObject.has("items")) {
            JSONArray itemsArray = jsonObject.getJSONArray("items");
            List<MediaResult> newShorts = new ArrayList<>();

            for (int i = 0; i < itemsArray.length(); i++) {
                JSONObject video = itemsArray.getJSONObject(i);
                String videoId = extractVideoId(video);

                // Add the video only if it has a valid ID and has not been seen before.
                if (videoId != null && seenShortsIds.add(videoId)) {
                    MediaResult result = parseVideo(video);
                    if (result != null) {
                        newShorts.add(result);
                    }
                }
            }

            // Shuffle the newly fetched shorts before adding them to the main pool.
            Collections.shuffle(newShorts);
            shortsPool.addAll(newShorts);

            // Trim the pool from the oldest entries if it exceeds the maximum size.
            while (shortsPool.size() > POOL_MAX_SIZE) {
                shortsPool.removeFirst();
            }
        }
    }

    /**
     * Strategy 1: Builds a URL to search for Shorts related to popular topics.
     */
    private String buildTopicShortsUrl(ThreadLocalRandom random) {
        String[] topics = {
                "funny", "gaming", "art", "science", "tech", "lifehack", "comedy", "fail",
                "asmr", "satisfying", "animation", "meme", "news", "diy"
        };
        String topic = topics[random.nextInt(topics.length)];
        String query = URLEncoder.encode(topic + " #shorts", StandardCharsets.UTF_8);

        return String.format(
                "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video" +
                        "&maxResults=%d&order=relevance&q=%s&videoDuration=short&key=%s",
                FETCH_SIZE, query, apiKey
        );
    }

    /**
     * Strategy 2: Builds a URL to find viral Shorts from a specific time period.
     */
    private String buildTimeBasedShortsUrl(ThreadLocalRandom random) {
        int daysAgo = random.nextInt(30, 365 * 2); // Look for videos from the last 2 years
        String afterDate = Instant.now().minus(daysAgo, ChronoUnit.DAYS).toString();
        String query = URLEncoder.encode("#shorts", StandardCharsets.UTF_8);

        return String.format(
                "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video" +
                        "&maxResults=%d&order=viewCount&publishedAfter=%s&q=%s&videoDuration=short&key=%s",
                FETCH_SIZE, afterDate, query, apiKey
        );
    }

    /**
     * Strategy 3: Builds a URL for a "chaos" search to find unconventional content.
     */
    private String buildChaosShortsUrl(ThreadLocalRandom random) {
        String[] chaosTerms = {"_", "-", ".", String.valueOf((char)('a' + random.nextInt(26)))};
        String term = chaosTerms[random.nextInt(chaosTerms.length)];
        String query = URLEncoder.encode(term + " #shorts", StandardCharsets.UTF_8);
        String[] orders = {"date", "relevance", "viewCount"};
        String order = orders[random.nextInt(orders.length)];

        return String.format(
                "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video" +
                        "&maxResults=%d&order=%s&q=%s&videoDuration=short&key=%s",
                FETCH_SIZE, order, query, apiKey
        );
    }

    /**
     * Extracts the video ID from a JSON item, handling different API response formats.
     */
    private String extractVideoId(JSONObject item) {
        if (item.has("id")) {
            JSONObject idObject = item.optJSONObject("id");
            if (idObject != null && idObject.has("videoId")) {
                return idObject.getString("videoId");
            }
        }
        return null;
    }

    /**
     * Parses a video's JSON object into a structured MediaResult.
     */
    private MediaResult parseVideo(JSONObject video) {
        try {
            JSONObject snippet = video.getJSONObject("snippet");
            String videoId = extractVideoId(video);
            if (videoId == null) return null;

            String title = snippet.getString("title");
            String channelTitle = snippet.getString("channelTitle");
            String publishDate = snippet.getString("publishedAt");

            JSONObject thumbnails = snippet.getJSONObject("thumbnails");
            String thumbnailUrl = thumbnails.getJSONObject("high").getString("url");
            String videoUrl = "https://www.youtube.com/shorts/" + videoId;

            String description = String.format(
                    "🎬 **Title:** %s\n📺 **Channel Name:** %s\n📅 **Date Of Release:** %s\n🔗 **Video Link:** <%s>",
                    title, channelTitle,
                    DiscordTimestamp.generateTimestampFromIso8601(publishDate, DiscordTimestampType.SHORT_DATE_TIME),
                    videoUrl
            );
            String resultTitle = "Here is your random YouTube Short!";

            return new MediaResult(thumbnailUrl, resultTitle, description, MediaSource.YOUTUBE);
        } catch (Exception e) {
            // Return null if parsing fails to avoid crashing the loop.
            return null;
        }
    }

    @Override
    public boolean supportsQuery() {
        return false;
    }

    @Override
    public String getProviderName() {
        return "YouTube Shorts";
    }
}