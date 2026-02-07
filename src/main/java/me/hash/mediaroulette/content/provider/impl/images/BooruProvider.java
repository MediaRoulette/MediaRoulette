package me.hash.mediaroulette.content.provider.impl.images;

import me.hash.mediaroulette.content.http.HttpClientWrapper;
import me.hash.mediaroulette.content.provider.MediaProvider;
import me.hash.mediaroulette.model.content.MediaResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * MediaProvider for booru image boards (Rule34, Gelbooru, etc.)
 * Supports random board selection or specific board via query parameter.
 */
public class BooruProvider implements MediaProvider {
    private static final Logger logger = LoggerFactory.getLogger(BooruProvider.class);
    private final HttpClientWrapper httpClient;

    public BooruProvider(HttpClientWrapper httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public MediaResult getRandomMedia(String query) throws IOException, HttpClientWrapper.RateLimitException, InterruptedException {
        BooruBoard board = selectBoard(query);
        
        try {
            return board.fetch(httpClient);
        } catch (Exception e) {
            throw new IOException("Failed to get image from " + board.getDisplayName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Select board based on query or randomly.
     */
    private BooruBoard selectBoard(String query) {
        if (query != null && !query.isBlank()) {
            return BooruBoard.byId(query).orElseGet(() -> {
                logger.debug("Unknown board '{}', using random", query);
                return BooruBoard.random();
            });
        }
        return BooruBoard.random();
    }

    @Override
    public boolean supportsQuery() {
        return true; // Supports board selection via query
    }

    @Override
    public String getProviderName() {
        return "Booru";
    }
}
