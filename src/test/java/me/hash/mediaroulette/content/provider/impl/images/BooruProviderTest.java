package me.hash.mediaroulette.content.provider.impl.images;

import me.hash.mediaroulette.content.http.HttpClientWrapper;
import me.hash.mediaroulette.model.content.MediaResult;
import me.hash.mediaroulette.model.content.MediaSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for BooruProvider.
 * These tests make real network calls to booru sites.
 * 
 * Note: These tests require network access and may be slow or flaky
 * due to external service availability or rate limiting.
 */
@Tag("integration")
class BooruProviderTest {

    private HttpClientWrapper httpClient;
    private BooruProvider provider;

    @BeforeEach
    void setUp() {
        httpClient = new HttpClientWrapper();
        provider = new BooruProvider(httpClient);
    }

    // ============ Provider Metadata Tests ============

    @Test
    void supportsQuery_ReturnsTrue() {
        assertTrue(provider.supportsQuery());
    }

    @Test
    void getProviderName_ReturnsBooru() {
        assertEquals("Booru", provider.getProviderName());
    }

    // ============ Integration Tests with Network Calls ============

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void getRandomMedia_WithNullQuery_ReturnsValidResult() throws Exception {
        MediaResult result = fetchWithRetry(null, 3);
        
        assertNotNull(result);
        assertNotNull(result.getImageUrl());
        assertNotNull(result.getTitle());
        assertNotNull(result.getDescription());
        assertNotNull(result.getSource());
        
        // URL should be a valid image URL
        assertTrue(result.getImageUrl().startsWith("http"), 
            "Image URL should start with http: " + result.getImageUrl());
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void getRandomMedia_WithEmptyQuery_ReturnsValidResult() throws Exception {
        MediaResult result = fetchWithRetry("", 3);
        
        assertNotNull(result);
        assertNotNull(result.getImageUrl());
        assertTrue(result.getImageUrl().startsWith("http"));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void getRandomMedia_WithRule34Query_ReturnsRule34Result() throws Exception {
        MediaResult result = fetchWithRetry("rule34", 3);
        
        assertNotNull(result);
        assertNotNull(result.getImageUrl());
        assertEquals(MediaSource.RULE34, result.getSource());
        assertTrue(result.getTitle().contains("Rule34"));
        assertTrue(result.getDescription().contains("Rule34"));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void getRandomMedia_WithGelbooruQuery_ReturnsGelbooruResult() throws Exception {
        MediaResult result = fetchWithRetry("gelbooru", 3);
        
        assertNotNull(result);
        assertNotNull(result.getImageUrl());
        // Gelbooru source is dynamically registered
        assertEquals("Gelbooru", result.getSource().getDisplayName());
        assertTrue(result.getTitle().contains("Gelbooru"));
        assertTrue(result.getDescription().contains("Gelbooru"));
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void getRandomMedia_CaseInsensitive_Rule34() throws Exception {
        MediaResult result = fetchWithRetry("RULE34", 3);
        
        assertNotNull(result);
        assertEquals(MediaSource.RULE34, result.getSource());
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void getRandomMedia_CaseInsensitive_Gelbooru() throws Exception {
        MediaResult result = fetchWithRetry("GELBOORU", 3);
        
        assertNotNull(result);
        assertEquals("Gelbooru", result.getSource().getDisplayName());
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void getRandomMedia_WithUnknownBoard_FallsBackToRandom() throws Exception {
        // Unknown board should fallback to random selection
        MediaResult result = fetchWithRetry("danbooru", 3);
        
        assertNotNull(result);
        assertNotNull(result.getImageUrl());
        // Should be one of the valid sources
        assertTrue(
            result.getSource().equals(MediaSource.RULE34) || 
            result.getSource().getDisplayName().equals("Gelbooru"),
            "Should fallback to a valid board"
        );
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void getRandomMedia_ResultContainsNSFWWarning() throws Exception {
        // Use Gelbooru as it tends to be more reliable
        MediaResult result = fetchWithRetry("gelbooru", 3);
        
        assertNotNull(result);
        assertTrue(result.getDescription().contains("NSFW"), 
            "Description should contain NSFW warning");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void getRandomMedia_ResultContainsSourceURL() throws Exception {
        // Use Gelbooru as it tends to be more reliable
        MediaResult result = fetchWithRetry("gelbooru", 3);
        
        assertNotNull(result);
        assertTrue(result.getDescription().contains("URL:"), 
            "Description should contain source URL");
    }

    // ============ Board Selection Logic Tests (Unit tests via reflection) ============

    @Test
    void selectBoard_WithNullQuery_ReturnsValidBoard() throws Exception {
        BooruBoard result = invokeSelectBoard(null);
        assertNotNull(result);
        assertTrue(result == BooruBoard.RULE34 || result == BooruBoard.GELBOORU);
    }

    @Test
    void selectBoard_WithRule34Query_SelectsRule34() throws Exception {
        BooruBoard result = invokeSelectBoard("rule34");
        assertEquals(BooruBoard.RULE34, result);
    }

    @Test
    void selectBoard_WithGelbooruQuery_SelectsGelbooru() throws Exception {
        BooruBoard result = invokeSelectBoard("gelbooru");
        assertEquals(BooruBoard.GELBOORU, result);
    }

    @Test
    void selectBoard_CaseInsensitive() throws Exception {
        assertEquals(BooruBoard.RULE34, invokeSelectBoard("RULE34"));
        assertEquals(BooruBoard.RULE34, invokeSelectBoard("Rule34"));
        assertEquals(BooruBoard.GELBOORU, invokeSelectBoard("GELBOORU"));
        assertEquals(BooruBoard.GELBOORU, invokeSelectBoard("Gelbooru"));
    }

    @Test
    void selectBoard_WithUnknownBoard_FallsBackToValidBoard() throws Exception {
        BooruBoard result = invokeSelectBoard("danbooru");
        assertNotNull(result);
        assertTrue(result == BooruBoard.RULE34 || result == BooruBoard.GELBOORU);
    }

    private MediaResult fetchWithRetry(String query, int maxRetries) throws Exception {
        Exception lastException = null;
        
        for (int i = 0; i < maxRetries; i++) {
            try {
                return provider.getRandomMedia(query);
            } catch (IOException e) {
                lastException = e;
                // wait before retrying (exponential backoff)
                if (i < maxRetries - 1) {
                    Thread.sleep((long) Math.pow(2, i) * 1000);
                }
            }
        }
        
        throw lastException;
    }

    private BooruBoard invokeSelectBoard(String query) throws Exception {
        java.lang.reflect.Method selectBoardMethod = BooruProvider.class.getDeclaredMethod("selectBoard", String.class);
        selectBoardMethod.setAccessible(true);
        return (BooruBoard) selectBoardMethod.invoke(provider, query);
    }
}
