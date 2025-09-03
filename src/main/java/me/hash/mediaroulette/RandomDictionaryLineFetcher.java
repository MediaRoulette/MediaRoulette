package me.hash.mediaroulette;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class RandomDictionaryLineFetcher {
    private static final int CHUNK_SIZE = 4096;
    private final OkHttpClient client;
    private final String source;
    private final boolean isLocal;
    private final Random random;
    private List<String> localLines;

    /**
     * Constructs a RandomDictionaryLineFetcher with a specified source.
     * @param source The URL (for online) or resource path (for local) to fetch from.
     * @param isLocal True if the source is a local resource, false if it's an online URL.
     */
    public RandomDictionaryLineFetcher(String source, boolean isLocal) {
        this.client = new OkHttpClient();
        this.source = source;
        this.isLocal = isLocal;
        this.random = new Random();
        if (isLocal) {
            try {
                loadLocalDictionary();
            } catch (IOException e) {
                throw new RuntimeException("Failed to load local dictionary: " + source, e);
            }
        }
    }

    /**
     * Loads the local dictionary from the resource file into memory.
     * @throws IOException If the resource cannot be found or read.
     */
    private void loadLocalDictionary() throws IOException {
        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream is = classLoader.getResourceAsStream(source)) {
            if (is == null) {
                throw new IOException("Resource not found: " + source);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                localLines = reader.lines().collect(Collectors.toList());
            }
        }
    }

    /**
     * Fetches a random line from the configured source.
     * @return A random line (query or word) from the source.
     * @throws IOException If the fetch operation fails.
     */
    public String getRandomLine() throws IOException {
        if (isLocal) {
            if (localLines == null || localLines.isEmpty()) {
                throw new IOException("Local dictionary is empty or not loaded.");
            }
            return localLines.get(random.nextInt(localLines.size()));
        } else {
            Request headRequest = new Request.Builder().url(source).head().build();
            try (Response headResponse = client.newCall(headRequest).execute()) {
                if (!headResponse.isSuccessful()) {
                    throw new IOException("HEAD request failed with code: " + headResponse.code());
                }

                String contentLengthStr = headResponse.header("Content-Length");
                if (contentLengthStr == null) {
                    throw new IOException("Missing Content-Length header.");
                }

                long contentLength = Long.parseLong(contentLengthStr);
                if (contentLength <= 0) {
                    throw new IOException("Invalid content length: " + contentLength);
                }

                long randomOffset = (long) (random.nextDouble() * contentLength);
                long start = Math.max(0, randomOffset - CHUNK_SIZE / 2);
                long end = Math.min(contentLength - 1, start + CHUNK_SIZE - 1);

                Request getRequest = new Request.Builder()
                        .url(source)
                        .header("Range", "bytes=" + start + "-" + end)
                        .build();

                try (Response getResponse = client.newCall(getRequest).execute()) {
                    if (!getResponse.isSuccessful() || getResponse.body() == null) {
                        throw new IOException("GET request failed with code: " + getResponse.code());
                    }

                    InputStream inputStream = getResponse.body().byteStream();
                    byte[] bytes = inputStream.readAllBytes();
                    String chunk = new String(bytes, StandardCharsets.UTF_8);

                    int relativeOffset = (int) (randomOffset - start);
                    relativeOffset = Math.max(0, Math.min(relativeOffset, chunk.length() - 1));

                    int lineStart = chunk.lastIndexOf('\n', relativeOffset);
                    int lineEnd = chunk.indexOf('\n', relativeOffset);

                    if (lineStart == -1) lineStart = 0;
                    if (lineEnd == -1) lineEnd = chunk.length();
                    if (lineStart >= lineEnd) {
                        return getRandomLine(); // Retry if no valid line is found
                    }

                    return chunk.substring(lineStart + 1, lineEnd).trim();
                }
            }
        }
    }

    /**
     * Creates a fetcher configured to use the local basic_dictionary.txt resource.
     * @return A RandomDictionaryLineFetcher instance for the basic dictionary.
     */
    public static RandomDictionaryLineFetcher getBasicDictionaryFetcher() {
        String resourcePath = "basic_dictionary.txt";
        return new RandomDictionaryLineFetcher(resourcePath, true);
    }

    public static RandomDictionaryLineFetcher getRedditDictionaryFetcher() {
        String resourcePath = "subreddits.txt";
        return new RandomDictionaryLineFetcher(resourcePath, true);
    }

    /**
     * Creates a fetcher configured to fetch random Google search queries from an online source.
     * @return A RandomDictionaryLineFetcher instance for search queries.
     */
    public static RandomDictionaryLineFetcher getSearchQueriesFetcher() {
        // Placeholder URL; replace with an actual source of search queries
        String searchQueriesUrl = "https://example.com/search_queries.txt";
        return new RandomDictionaryLineFetcher(searchQueriesUrl, false);
    }
}