package me.hash.mediaroulette.content.provider.impl.videos;

import me.hash.mediaroulette.model.content.MediaResult;
import me.hash.mediaroulette.model.content.MediaSource;
import me.hash.mediaroulette.content.provider.MediaProvider;
import me.hash.mediaroulette.content.http.HttpClientWrapper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TMDBMovieProvider implements MediaProvider {
    private static final String BASE_URL = "https://api.themoviedb.org/3";
    private static final String BASE_IMAGE_URL = "https://image.tmdb.org/t/p/w500";

    private final Map<Integer, Queue<MediaResult>> yearCache = new ConcurrentHashMap<>();
    private final HttpClientWrapper httpClient;
    private final Random random = new Random();
    private final String apiKey;

    public TMDBMovieProvider(HttpClientWrapper httpClient, String apiKey) {
        this.httpClient = httpClient;
        this.apiKey = apiKey;
    }

    private static final int MIN_YEAR = 1970; // Most TMDB content starts here
    private static final int MAX_RETRIES = 5;

    @Override
    public MediaResult getRandomMedia(String query) throws IOException, HttpClientWrapper.RateLimitException, InterruptedException {
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        
        // Try up to MAX_RETRIES times with different years
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            int year = random.nextInt(currentYear - MIN_YEAR + 1) + MIN_YEAR;
            Queue<MediaResult> cache = yearCache.computeIfAbsent(year, k -> new LinkedList<>());

            if (cache.isEmpty()) {
                try {
                    populateCache(year);
                } catch (IOException e) {
                    // Year might not have movies, try another year
                    continue;
                }
            }

            MediaResult result = cache.poll();
            if (result != null) {
                return result;
            }
            // Cache was empty after population, remove and try another year
            yearCache.remove(year);
        }
        
        throw new IOException("No movies available after " + MAX_RETRIES + " attempts");
    }

    private void populateCache(int year) throws IOException, HttpClientWrapper.RateLimitException, InterruptedException {
        Map<String, String> params = buildRandomDiscoverParams(year);
        // First request to get total pages for the chosen filter set
        String firstPageUrl = buildDiscoverUrl(params, 1);
        String firstResponse = httpClient.getBody(firstPageUrl);
        JSONObject firstJson = new JSONObject(firstResponse);
        int totalPages = Math.max(1, firstJson.optInt("total_pages", 1));
        int maxPages = Math.min(totalPages, 500); // TMDB caps at 500
        int randomPage = 1 + random.nextInt(maxPages);

        String url = buildDiscoverUrl(params, randomPage);
        String response = httpClient.getBody(url);
        JSONObject jsonObject = new JSONObject(response);
        JSONArray results = jsonObject.getJSONArray("results");

        List<MediaResult> movies = new ArrayList<>();
        for (int i = 0; i < results.length(); i++) {
            JSONObject item = results.getJSONObject(i);
            movies.add(parseMedia(item));
        }

        Collections.shuffle(movies, random);

        Queue<MediaResult> cache = yearCache.get(year);
        cache.addAll(movies);
    }

    private Map<String, String> buildRandomDiscoverParams(int suggestedYear) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("api_key", apiKey);
        params.put("include_adult", "false");

        // Maybe include a year (about 60% chance)
        if (random.nextDouble() < 0.6) {
            int currentYear = Calendar.getInstance().get(Calendar.YEAR);
            int year = suggestedYear > 0 ? suggestedYear : (MIN_YEAR + random.nextInt(currentYear - MIN_YEAR + 1));
            params.put("primary_release_year", String.valueOf(year));
        }

        // Maybe include one or two random genres (about 50% chance)
        if (random.nextDouble() < 0.5) {
            int[] genrePool = new int[]{28,12,16,35,80,99,18,10751,14,36,27,10402,9648,10749,878,10770,53,10752,37};
            int picks = 1 + (random.nextDouble() < 0.3 ? 1 : 0); // 70% pick 1, 30% pick 2
            Set<Integer> chosen = new LinkedHashSet<>();
            while (chosen.size() < picks) {
                chosen.add(genrePool[random.nextInt(genrePool.length)]);
            }
            StringBuilder sb = new StringBuilder();
            for (int g : chosen) {
                if (sb.length() > 0) sb.append(",");
                sb.append(g);
            }
            params.put("with_genres", sb.toString());
        }

        // Maybe restrict by original language (about 50% chance)
        if (random.nextDouble() < 0.5) {
            String[] langs = new String[]{"en","es","fr","de","ja","ko","hi","it","pt","ru","zh"};
            params.put("with_original_language", langs[random.nextInt(langs.length)]);
        }

        // Maybe restrict by minimum vote count to include lesser-known titles (about 70% chance)
        if (random.nextDouble() < 0.7) {
            int[] thresholds = new int[]{0,1,2,3,5,10,20,30,50,75,100};
            // Bias toward lower thresholds
            int idx = (int)Math.floor(Math.pow(random.nextDouble(), 2) * thresholds.length);
            if (idx >= thresholds.length) idx = thresholds.length - 1;
            params.put("vote_count.gte", String.valueOf(thresholds[idx]));
        }

        // Random sort to further diversify ordering
        String[] sorts = new String[]{
                "popularity.asc","popularity.desc",
                "primary_release_date.asc","primary_release_date.desc",
                "vote_average.asc","vote_average.desc"
        };
        params.put("sort_by", sorts[random.nextInt(sorts.length)]);

        return params;
    }

    private String buildDiscoverUrl(Map<String, String> params, int page) {
        StringBuilder sb = new StringBuilder(BASE_URL).append("/discover/movie?");
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) sb.append("&");
            first = false;
            sb.append(e.getKey()).append("=").append(e.getValue());
        }
        sb.append("&page=").append(page);
        return sb.toString();
    }

    private MediaResult parseMedia(JSONObject item) {
        String title = item.getString("title");
        String date = item.getString("release_date");
        String posterPath = item.optString("poster_path");
        String imageUrl = posterPath.isEmpty() ? "none" : BASE_IMAGE_URL + posterPath;

        String description = String.format("🌐 Source: TMDB\n✏️ Title: %s\n📅 Release Date: %s\n⭐ Rating: %.1f/10\n🔍 Synopsis: %s",
                title, date, item.getDouble("vote_average"), item.getString("overview"));
        String resultTitle = "Here is your random movie from TMDB!";

        return new MediaResult(imageUrl, resultTitle, description, MediaSource.TMDB);
    }

    @Override
    public boolean supportsQuery() {
        return false; // Uses random year selection instead of query
    }

    @Override
    public String getProviderName() {
        return "TMDB Movies";
    }
}