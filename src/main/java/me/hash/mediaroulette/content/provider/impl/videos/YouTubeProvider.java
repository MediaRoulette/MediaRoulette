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
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class YouTubeProvider implements MediaProvider {
    private final List<MediaResult> videoPool = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> seenVideoIds = Collections.synchronizedSet(new HashSet<>());
    private final HttpClientWrapper httpClient;
    private final String apiKey;

    private static final int POOL_MIN_SIZE = 100;
    private static final int POOL_MAX_SIZE = 500;
    private static final int FETCH_SIZE = 50;

    public YouTubeProvider(HttpClientWrapper httpClient, String apiKey) {
        this.httpClient = httpClient;
        this.apiKey = apiKey;
    }

    @Override
    public MediaResult getRandomMedia(String query) throws IOException, HttpClientWrapper.RateLimitException, InterruptedException {
        if (videoPool.size() < POOL_MIN_SIZE) {
            refillPool();
        }

        if (!videoPool.isEmpty()) {
            int index = ThreadLocalRandom.current().nextInt(videoPool.size());
            return videoPool.remove(index);
        }

        throw new IOException("No YouTube videos available");
    }

    private void refillPool() throws IOException, HttpClientWrapper.RateLimitException, InterruptedException {
        if (videoPool.size() >= POOL_MAX_SIZE) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Mix different search strategies for quality variety
        double roll = random.nextDouble();
        String url;

        if (roll < 0.3) {
            // 30% - Trending/popular videos from random regions
            url = buildTrendingUrl(random);
        } else if (roll < 0.6) {
            // 30% - View count threshold searches for quality
            url = buildViewCountUrl(random);
        } else if (roll < 0.85) {
            // 25% - Random time-based viral videos
            url = buildTimeBasedUrl(random);
        } else {
            // 15% - True chaos mode (weird finds)
            url = buildChaosUrl(random);
        }

        String response = httpClient.getBody(url);
        JSONObject jsonObject = new JSONObject(response);

        if (jsonObject.has("items")) {
            JSONArray itemsArray = jsonObject.getJSONArray("items");
            List<MediaResult> newVideos = new ArrayList<>();

            for (int i = 0; i < itemsArray.length(); i++) {
                JSONObject video = itemsArray.getJSONObject(i);
                String videoId = extractVideoId(video);

                if (videoId != null && seenVideoIds.add(videoId)) {
                    MediaResult result = parseVideo(video);
                    if (result != null) {
                        newVideos.add(result);
                    }
                }
            }

            Collections.shuffle(newVideos);
            videoPool.addAll(newVideos);

            while (videoPool.size() > POOL_MAX_SIZE) {
                videoPool.remove(0);
            }
        }
    }

    private String buildTrendingUrl(ThreadLocalRandom random) {
        // Random region codes for different trending videos
        String[] regions = {"US", "GB", "CA", "AU", "JP", "KR", "BR", "MX", "FR", "DE", "IN", "RU", "ES", "IT"};
        String region = regions[random.nextInt(regions.length)];

        // Random category IDs (10=Music, 20=Gaming, 24=Entertainment, 17=Sports, 28=Tech, 1=Film)
        int[] categories = {0, 10, 20, 24, 17, 28, 1, 15, 19, 22, 23, 25};
        int category = categories[random.nextInt(categories.length)];

        String url = String.format(
                "https://www.googleapis.com/youtube/v3/videos?part=snippet&chart=mostPopular" +
                        "&maxResults=%d&regionCode=%s&key=%s",
                FETCH_SIZE, region, apiKey
        );

        if (category > 0) {
            url += "&videoCategoryId=" + category;
        }

        return url;
    }

    private String buildViewCountUrl(ThreadLocalRandom random) throws IOException {
        // Search with view count thresholds for quality
        String[] qualitySearches = {
                "before:2025", "after:2020", "4k", "hd",
                "official", "trailer", "gameplay", "highlights",
                "full", "best", "top", "epic", "moments"
        };

        String term = qualitySearches[random.nextInt(qualitySearches.length)];
        String order = random.nextBoolean() ? "viewCount" : "relevance";

        // Random time window for variety
        int daysAgo = random.nextInt(30, 365 * 5);
        String afterDate = Instant.now().minus(daysAgo, ChronoUnit.DAYS).toString();

        return String.format(
                "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video" +
                        "&maxResults=%d&order=%s&q=%s&publishedAfter=%s&key=%s",
                FETCH_SIZE, order,
                URLEncoder.encode(term, StandardCharsets.UTF_8),
                afterDate, apiKey
        );
    }

    private String buildTimeBasedUrl(ThreadLocalRandom random) throws IOException {
        // Get videos from specific "golden" time periods
        int year = random.nextInt(2015, 2025);
        int month = random.nextInt(1, 13);

        String afterDate = String.format("%d-%02d-01T00:00:00Z", year, month);
        String beforeDate = String.format("%d-%02d-28T00:00:00Z", year, month);

        // Simple searches that tend to find good content
        String[] terms = {"", "video", "2024", "2023", "new", "viral", "trending"};
        String search = terms[random.nextInt(terms.length)];

        return String.format(
                "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video" +
                        "&maxResults=%d&order=viewCount&publishedAfter=%s&publishedBefore=%s&q=%s&key=%s",
                FETCH_SIZE, afterDate, beforeDate,
                URLEncoder.encode(search, StandardCharsets.UTF_8), apiKey
        );
    }

    private String buildChaosUrl(ThreadLocalRandom random) throws IOException {
        // True random for occasional weird finds
        String[] chaosTerms = {"IMG", "DSC", "MOV", "VID",
                String.valueOf((char)('a' + random.nextInt(26))),
                String.valueOf(random.nextInt(10))
        };

        String term = chaosTerms[random.nextInt(chaosTerms.length)];
        String order = new String[]{"date", "rating", "relevance", "viewCount"}[random.nextInt(4)];

        return String.format(
                "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video" +
                        "&maxResults=%d&order=%s&q=%s&key=%s",
                FETCH_SIZE, order,
                URLEncoder.encode(term, StandardCharsets.UTF_8), apiKey
        );
    }

    private String extractVideoId(JSONObject item) {
        // Handle both video list and search result formats
        if (item.has("id")) {
            JSONObject id = item.get("id") instanceof String ?
                    null : item.getJSONObject("id");

            if (id != null && id.has("videoId")) {
                return id.getString("videoId");
            } else if (item.get("id") instanceof String) {
                return item.getString("id");
            }
        }
        return null;
    }

    private MediaResult parseVideo(JSONObject video) {
        try {
            JSONObject snippet = video.getJSONObject("snippet");

            String videoId = extractVideoId(video);
            if (videoId == null) return null;

            String title = snippet.getString("title");
            String channelTitle = snippet.getString("channelTitle");
            String publishDate = snippet.getString("publishedAt");

            JSONObject thumbnails = snippet.getJSONObject("thumbnails");
            String thumbnailUrl;
            if (thumbnails.has("high")) {
                thumbnailUrl = thumbnails.getJSONObject("high").getString("url");
            } else if (thumbnails.has("medium")) {
                thumbnailUrl = thumbnails.getJSONObject("medium").getString("url");
            } else {
                thumbnailUrl = thumbnails.getJSONObject("default").getString("url");
            }

            String videoUrl = "https://www.youtube.com/watch?v=" + videoId;

            String description = String.format("🎬 **Title:** %s\n📺 **Channel Name:** %s\n📅 **Date Of Release:** %s\n🔗 **Video Link:** <%s>",
                    title, channelTitle,
                    DiscordTimestamp.generateTimestampFromIso8601(publishDate, DiscordTimestampType.SHORT_DATE_TIME),
                    videoUrl);
            String resultTitle = "Here is your random YouTube video!";

            return new MediaResult(thumbnailUrl, resultTitle, description, MediaSource.YOUTUBE);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean supportsQuery() {
        return false;
    }

    @Override
    public String getProviderName() {
        return "YouTube";
    }
    
    @Override
    public boolean isNsfw() {
        return false;
    }
}