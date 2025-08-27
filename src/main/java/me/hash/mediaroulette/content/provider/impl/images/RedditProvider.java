package me.hash.mediaroulette.content.provider.impl.images;

import me.hash.mediaroulette.model.content.MediaResult;
import me.hash.mediaroulette.model.content.CachedMediaResult;
import me.hash.mediaroulette.content.provider.MediaProvider;
import me.hash.mediaroulette.utils.DictionaryIntegration;
import me.hash.mediaroulette.content.reddit.RedditClient;
import me.hash.mediaroulette.content.reddit.SubredditManager;
import me.hash.mediaroulette.content.reddit.RedditPostProcessor;
import me.hash.mediaroulette.utils.ErrorReporter;
import me.hash.mediaroulette.utils.PersistentCache;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public class RedditProvider implements MediaProvider {
    private static final Logger logger = LoggerFactory.getLogger(RedditProvider.class);

    private static final long CACHE_EXPIRATION_TIME = 10 * 60 * 1000; // 10 minutes
    private static final int POST_LIMIT = 50;
    private static final int MAX_RESULTS_PER_SUBREDDIT = 200;
    private static final int MIN_QUEUE_SIZE = 10;

    // In-memory queues for active use
    private final Map<String, Queue<MediaResult>> imageQueues = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUpdated = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> processedPostIds = new ConcurrentHashMap<>();
    
    // Persistent cache for Reddit media results
    private final PersistentCache<List<CachedMediaResult>> persistentCache = 
        new PersistentCache<>("reddit_media_cache.json", new TypeReference<Map<String, List<CachedMediaResult>>>() {});
    private final PersistentCache<Long> timestampCache = 
        new PersistentCache<>("reddit_timestamps.json", new TypeReference<Map<String, Long>>() {});
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(6);

    private final RedditClient redditClient;
    private final SubredditManager subredditManager;
    private final RedditPostProcessor postProcessor;

    public RedditProvider(RedditClient redditClient, SubredditManager subredditManager) {
        this.redditClient = redditClient;
        this.subredditManager = subredditManager;
        this.postProcessor = new RedditPostProcessor();
    }

    @Override
    public MediaResult getRandomMedia(String subreddit) throws IOException {
        return getRandomMedia(subreddit, null);
    }
    
    public MediaResult getRandomMedia(String subreddit, String userId) throws IOException {
        try {
            return getRandomReddit(subreddit, userId);
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Error fetching Reddit media", e);
        }
    }

    public MediaResult getRandomReddit(String subreddit) throws IOException, ExecutionException, InterruptedException {
        return getRandomReddit(subreddit, null);
    }
    
    public MediaResult getRandomReddit(String subreddit, String userId) throws IOException, ExecutionException, InterruptedException {
        // Always try dictionary first if userId is provided and no specific subreddit requested
        if (subreddit == null && userId != null) {
            System.out.println("RedditProvider: Trying to get dictionary subreddit for user: " + userId);
            String dictSubreddit = DictionaryIntegration.getRandomWordForSource(userId, "reddit");
            System.out.println("RedditProvider: Dictionary returned subreddit: " + dictSubreddit);
            if (dictSubreddit != null && subredditManager.doesSubredditExist(dictSubreddit)) {
                subreddit = dictSubreddit;
                System.out.println("RedditProvider: Using dictionary subreddit: " + subreddit);
                logger.info("Using dictionary subreddit: {}", subreddit);
            } else {
                System.out.println("RedditProvider: Dictionary subreddit invalid or null");
            }
        }
        
        // If still no subreddit or invalid subreddit, use fallback logic
        if (subreddit == null || !subredditManager.doesSubredditExist(subreddit)) {
            try {
                subreddit = subredditManager.getRandomSubreddit();
            } catch (IOException e) {
                logger.error("Failed to get random subreddit: {}", e.getMessage());
                ErrorReporter.reportProviderError("reddit", "random subreddit selection", e.getMessage(), userId);
                throw new IOException("Unable to find a valid subreddit. " + e.getMessage());
            }
        }

        initializeCacheIfNeeded(subreddit);
        refreshCacheIfNeeded(subreddit);

        Queue<MediaResult> queue = imageQueues.get(subreddit);
        MediaResult result = queue.poll();

        if (result == null) {
            logger.warn("No images available for subreddit {} after updating.", subreddit);
            throw new IOException("No images available for subreddit: " + subreddit);
        }

        // Update persistent cache to reflect the consumed item
        saveToPersistentCache(subreddit, queue);

        return result;
    }

    private void initializeCacheIfNeeded(String subreddit) {
        imageQueues.computeIfAbsent(subreddit, k -> new ConcurrentLinkedQueue<>());
        processedPostIds.computeIfAbsent(subreddit, k -> ConcurrentHashMap.newKeySet());
        
        // Load from persistent cache if available
        Long cachedTimestamp = timestampCache.get(subreddit);
        if (cachedTimestamp != null) {
            lastUpdated.put(subreddit, cachedTimestamp);
            
            // Load cached media results if they're still valid
            List<CachedMediaResult> cachedResults = persistentCache.get(subreddit);
            if (cachedResults != null && !cachedResults.isEmpty()) {
                Queue<MediaResult> queue = imageQueues.get(subreddit);
                int loadedCount = 0;
                
                for (CachedMediaResult cached : cachedResults) {
                    if (cached.isValid(CACHE_EXPIRATION_TIME)) {
                        queue.offer(cached.toMediaResult());
                        loadedCount++;
                    }
                }
            }
        } else {
            lastUpdated.put(subreddit, 0L);
        }
    }

    private void refreshCacheIfNeeded(String subreddit) throws ExecutionException, InterruptedException {
        Queue<MediaResult> imageQueue = imageQueues.get(subreddit);
        long lastUpdateTime = lastUpdated.get(subreddit);
        boolean needsRefresh = imageQueue.size() < MIN_QUEUE_SIZE ||
                System.currentTimeMillis() - lastUpdateTime > CACHE_EXPIRATION_TIME;

        if (needsRefresh) {
            updateImageQueue(subreddit);
            long currentTime = System.currentTimeMillis();
            lastUpdated.put(subreddit, currentTime);
            timestampCache.put(subreddit, currentTime);
        }
    }

    private void updateImageQueue(String subreddit) throws ExecutionException, InterruptedException {
        List<CompletableFuture<List<MediaResult>>> futures = new ArrayList<>();
        String[] sortMethods = {"hot", "top", "new"}; // Mix different sorting methods

        for (String sortMethod : sortMethods) {
            CompletableFuture<List<MediaResult>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return fetchImagesFromSubreddit(subreddit, sortMethod);
                } catch (IOException | InterruptedException | ExecutionException e) {
                    logger.error( "Error fetching images for subreddit {} with sort {}: {}",
                            subreddit, sortMethod, e.getMessage());
                    Thread.currentThread().interrupt();
                    return Collections.emptyList();
                }
            }, executorService);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        Queue<MediaResult> queue = imageQueues.get(subreddit);
        Set<String> processedIds = processedPostIds.get(subreddit);
        List<MediaResult> allNewResults = new ArrayList<>();

        for (CompletableFuture<List<MediaResult>> future : futures) {
            List<MediaResult> results = future.get();
            allNewResults.addAll(results);
        }

        if (!allNewResults.isEmpty()) {
            Collections.shuffle(allNewResults);

            // Add new results to queue, avoiding duplicates
            int addedCount = 0;
            for (MediaResult result : allNewResults) {
                if (queue.size() >= MAX_RESULTS_PER_SUBREDDIT) {
                    break;
                }

                String resultId = generateResultId(result);
                if (!processedIds.contains(resultId)) {
                    queue.offer(result);
                    processedIds.add(resultId);
                    addedCount++;
                }
            }

            // Save to persistent cache
            saveToPersistentCache(subreddit, queue);
        } else {
            logger.warn("No valid images found for subreddit: {}", subreddit);
        }

        // Clean up old processed IDs if the set gets too large
        if (processedIds.size() > MAX_RESULTS_PER_SUBREDDIT * 2) {
            processedIds.clear();
        }
    }

    private List<MediaResult> fetchImagesFromSubreddit(String subreddit, String sortMethod)
            throws IOException, ExecutionException, InterruptedException {
        String accessToken = redditClient.getAccessToken();
        String timeParam = "top".equals(sortMethod) ? "&t=week" : ""; // For top posts, use weekly
        String url = String.format("https://oauth.reddit.com/r/%s/%s?limit=%d%s",
                subreddit, sortMethod, POST_LIMIT, timeParam);

        Response response = redditClient.sendGetRequestAsync(url, accessToken).get();
        if (!response.isSuccessful()) {
            logger.error("Failed to fetch posts for subreddit: {} with sort: {}",
                    subreddit, sortMethod);
            return Collections.emptyList();
        }

        String responseBody = response.body().string();
        response.close();

        try {
            JSONObject json = new JSONObject(responseBody);
            JSONArray posts = json.getJSONObject("data").getJSONArray("children");

            return postProcessor.processPosts(posts);
        } catch (Exception e) {
            logger.error("Error parsing Reddit response for {}/{}: {}",
                    subreddit, sortMethod, e.getMessage());
            return Collections.emptyList();
        }
    }

    private String generateResultId(MediaResult result) {
        // Create a simple ID based on URL and title to avoid duplicates
        return (result.getImageUrl() + "|" + result.getTitle()).hashCode() + "";
    }

    @Override
    public boolean supportsQuery() {
        return true;
    }

    @Override
    public String getProviderName() {
        return "Reddit Enhanced";
    }

    // Save current queue to persistent cache
    private void saveToPersistentCache(String subreddit, Queue<MediaResult> queue) {
        try {
            List<CachedMediaResult> cachedResults = new ArrayList<>();
            for (MediaResult result : queue) {
                cachedResults.add(new CachedMediaResult(result));
            }
            persistentCache.put(subreddit, cachedResults);
        } catch (Exception e) {
            logger.warn("Failed to save cache for subreddit {}: {}",
                    subreddit, e.getMessage());
        }
    }

    // Cleanup method to prevent memory leaks
    public void cleanup() {
        // Save all current caches before shutdown
        for (Map.Entry<String, Queue<MediaResult>> entry : imageQueues.entrySet()) {
            saveToPersistentCache(entry.getKey(), entry.getValue());
        }
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}