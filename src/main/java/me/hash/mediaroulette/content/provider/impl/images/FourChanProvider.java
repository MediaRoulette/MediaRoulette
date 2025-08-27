package me.hash.mediaroulette.content.provider.impl.images;

import me.hash.mediaroulette.model.content.MediaResult;
import me.hash.mediaroulette.model.content.MediaSource;
import me.hash.mediaroulette.content.provider.MediaProvider;
import me.hash.mediaroulette.content.http.HttpClientWrapper;
import me.hash.mediaroulette.utils.ErrorReporter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FourChanProvider implements MediaProvider {
    private static final Logger logger = LoggerFactory.getLogger(FourChanProvider.class);
    private static final List<String> BOARDS = Arrays.asList("a", "c", "w", "m", "cgl", "cm", "n", "jp", "vp", "v", "vg",
            "vr", "co", "g", "tv", "k", "o", "an", "tg", "sp", "asp", "sci", "int", "out", "toy", "biz", "i", "po", "p", "ck", "ic",
            "wg", "mu", "fa", "3", "gd", "diy", "wsg", "s", "hc", "hm", "h", "e", "u", "d", "y", "t", "hr", "gif",
            "trv", "fit", "x", "lit", "adv", "lgbt", "mlp", "b", "r", "r9k", "pol", "soc", "s4s");

    // Cache to track board validation results
    private static final Map<String, Boolean> BOARD_VALIDATION_CACHE = new ConcurrentHashMap<>();
    
    private final Map<String, Queue<MediaResult>> imageCache = new ConcurrentHashMap<>();
    private final HttpClientWrapper httpClient;
    private final Random random = new Random();

    public FourChanProvider(HttpClientWrapper httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public MediaResult getRandomMedia(String board) throws IOException, HttpClientWrapper.RateLimitException, InterruptedException {
        return getRandomMedia(board, null);
    }
    
    public MediaResult getRandomMedia(String board, String userId) throws IOException, HttpClientWrapper.RateLimitException, InterruptedException {
        if (board == null || !isValidBoard(board)) {
            board = getValidRandomBoard(userId);
        }

        Queue<MediaResult> cache = imageCache.computeIfAbsent(board, k -> new LinkedList<>());

        if (cache.isEmpty()) {
            populateCache(board, userId);
        }

        MediaResult result = cache.poll();
        if (result == null) {
            String errorMsg = "No images available for board: " + board;
            logger.warn(errorMsg);
            ErrorReporter.reportFailed4ChanBoard(board, errorMsg, userId);
            throw new IOException(errorMsg + ". Please use /support for help.");
        }
        return result;
    }
    
    /**
     * Get a valid random board with validation
     */
    private String getValidRandomBoard(String userId) throws IOException {
        List<String> shuffledBoards = new ArrayList<>(BOARDS);
        Collections.shuffle(shuffledBoards);
        
        // Try to find a valid board, with a maximum of 10 attempts
        int attempts = 0;
        int maxAttempts = Math.min(10, shuffledBoards.size());
        
        for (String board : shuffledBoards) {
            if (attempts >= maxAttempts) {
                break;
            }
            attempts++;
            
            try {
                if (validateBoardExists(board)) {
                    return board;
                }
            } catch (IOException e) {
                logger.warn("Board validation failed for {}: {}", board, e.getMessage(), e);
            }
        }
        
        // If no valid board found, report error and throw exception
        String errorMsg = "No valid 4chan boards found after " + attempts + " attempts";
        logger.error(errorMsg);
        ErrorReporter.reportProviderError("4chan", "board validation", errorMsg, userId);
        throw new IOException(errorMsg + ". Please use /support for help.");
    }
    
    /**
     * Validate if a board exists by checking its catalog
     */
    private boolean validateBoardExists(String board) throws IOException {
        // Check cache first
        if (BOARD_VALIDATION_CACHE.containsKey(board)) {
            return BOARD_VALIDATION_CACHE.get(board);
        }
        
        try {
            String url = String.format("https://a.4cdn.org/%s/catalog.json", board);
            String response = httpClient.get(url, "4chan", null);
            
            // If we can parse the response as JSON array, the board exists
            JSONArray data = new JSONArray(response);
            boolean exists = data.length() > 0;
            
            // Cache the result
            BOARD_VALIDATION_CACHE.put(board, exists);
            
            // Simple cache eviction policy
            if (BOARD_VALIDATION_CACHE.size() > 100) {
                BOARD_VALIDATION_CACHE.clear();
            }
            
            return exists;
        } catch (Exception e) {
            // If any error occurs, consider the board invalid
            BOARD_VALIDATION_CACHE.put(board, false);
            return false;
        }
    }

    private void populateCache(String board, String userId) throws IOException, HttpClientWrapper.RateLimitException, InterruptedException {
        try {
            List<Integer> threadNumbers = fetchThreadNumbers(board);
            if (threadNumbers.isEmpty()) {
                String errorMsg = "No threads found for board: " + board;
                logger.warn(errorMsg);
                ErrorReporter.reportFailed4ChanBoard(board, errorMsg, userId);
                return;
            }

            int selectedThread = threadNumbers.get(random.nextInt(threadNumbers.size()));
            List<MediaResult> images = fetchImagesFromThread(board, selectedThread);

            Queue<MediaResult> cache = imageCache.get(board);
            cache.addAll(images);
        } catch (IOException | HttpClientWrapper.RateLimitException | InterruptedException e) {
            String errorMsg = "Failed to populate cache for board " + board + ": " + e.getMessage();
            logger.error(errorMsg);
            ErrorReporter.reportFailed4ChanBoard(board, errorMsg, userId);
            throw e;
        }
    }

    private List<Integer> fetchThreadNumbers(String board) throws IOException, HttpClientWrapper.RateLimitException, InterruptedException {
        String url = String.format("https://a.4cdn.org/%s/catalog.json", board);
        String response = httpClient.get(url, "4chan", null);

        JSONArray data = new JSONArray(response);
        List<Integer> threadNumbers = new ArrayList<>();

        for (int i = 0; i < data.length(); i++) {
            JSONObject page = data.getJSONObject(i);
            JSONArray threads = page.getJSONArray("threads");
            for (int j = 0; j < threads.length(); j++) {
                JSONObject thread = threads.getJSONObject(j);
                threadNumbers.add(thread.getInt("no"));
            }
        }
        return threadNumbers;
    }

    private List<MediaResult> fetchImagesFromThread(String board, int threadId) throws IOException, HttpClientWrapper.RateLimitException, InterruptedException {
        String url = String.format("https://a.4cdn.org/%s/thread/%d.json", board, threadId);
        String response = httpClient.get(url, "4chan", null);

        JSONObject postData = new JSONObject(response);
        JSONArray posts = postData.getJSONArray("posts");
        List<MediaResult> images = new ArrayList<>();

        for (int i = 0; i < posts.length(); i++) {
            JSONObject post = posts.getJSONObject(i);
            if (post.has("tim") && post.has("ext")) {
                String imageUrl = String.format("https://i.4cdn.org/%s/%d%s",
                        board, post.getLong("tim"), post.getString("ext"));

                String description = String.format("Source: 4Chan\nBoard: %s\nThread: <%s>",
                        board,
                        String.format("https://boards.4chan.org/%s/thread/%d", board, threadId));

                images.add(new MediaResult(imageUrl, "Here is your random 4Chan image!", description, MediaSource.CHAN_4));
            }
        }
        return images;
    }

    public boolean isValidBoard(String board) {
        if (!BOARDS.contains(board)) {
            return false;
        }
        
        // Also check if board actually exists (with caching)
        try {
            return validateBoardExists(board);
        } catch (IOException e) {
            logger.error("Failed to validate board {}: {}", board, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean supportsQuery() {
        return true;
    }

    @Override
    public String getProviderName() {
        return "4Chan Enhanced";
    }
}