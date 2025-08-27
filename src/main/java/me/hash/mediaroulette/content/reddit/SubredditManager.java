package me.hash.mediaroulette.content.reddit;

import okhttp3.Response;
import org.json.JSONObject;
import me.hash.mediaroulette.utils.ErrorReporter;
import me.hash.mediaroulette.utils.PersistentCache;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class SubredditManager {
    private static final Logger logger = LoggerFactory.getLogger(SubredditManager.class);

    private static final PersistentCache<Boolean> SUBREDDIT_EXISTS_CACHE =
        new PersistentCache<>("subreddit_exists.json", new TypeReference<Map<String, Boolean>>() {});
    private final RedditClient redditClient;

    public SubredditManager(RedditClient redditClient) {
        this.redditClient = redditClient;
    }

    public boolean doesSubredditExist(String subreddit) throws IOException {
        if (SUBREDDIT_EXISTS_CACHE.containsKey(subreddit)) {
            return SUBREDDIT_EXISTS_CACHE.get(subreddit);
        }

        String url = "https://oauth.reddit.com/r/" + subreddit + "/about";
        Response response = redditClient.sendGetRequestAsync(url, redditClient.getAccessToken()).join();
        String responseBody = response.body().string();
        response.close();

        JSONObject json = new JSONObject(responseBody);

        // If an error key exists, then the subreddit likely does not exist.
        boolean exists = !json.has("error");
        SUBREDDIT_EXISTS_CACHE.put(subreddit, exists);

        if (SUBREDDIT_EXISTS_CACHE.size() > 1000) {
            logger.warn("Subreddit cache size exceeded 1000, clearing cache");
            SUBREDDIT_EXISTS_CACHE.clear();
        }
        return exists;
    }

    public String getRandomSubreddit() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("subreddits.txt"))
        ))) {
            List<String> subreddits = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                subreddits.add(line.trim());
            }
            if (subreddits.isEmpty()) {
                throw new IOException("No subreddits available in the list.");
            }
            Collections.shuffle(subreddits);
            
            // Try to find a valid subreddit, with a maximum of 10 attempts to avoid infinite loops
            int attempts = 0;
            int maxAttempts = Math.min(10, subreddits.size());
            
            for (String subreddit : subreddits) {
                if (attempts >= maxAttempts) {
                    break;
                }
                attempts++;
                
                try {
                    if (doesSubredditExist(subreddit)) {
                        return subreddit;
                    } else {
                        // Report invalid subreddit for monitoring
                        ErrorReporter.reportFailedSubreddit(subreddit, "Subreddit validation failed - does not exist", null);
                    }
                } catch (IOException e) {
                    // Report validation error
                    ErrorReporter.reportFailedSubreddit(subreddit, "Subreddit validation error: " + e.getMessage(), null);
                    // Continue to next subreddit if validation fails
                    continue;
                }
            }
            
            // If no valid subreddit found after attempts, throw an exception with helpful message
            throw new IOException("No valid subreddits found after " + attempts + " attempts. Please use /support for help.");
        }
    }
}