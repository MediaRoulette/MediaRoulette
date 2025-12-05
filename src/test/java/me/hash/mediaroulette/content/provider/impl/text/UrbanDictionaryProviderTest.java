package me.hash.mediaroulette.content.provider.impl.text;

import me.hash.mediaroulette.content.http.HttpClientWrapper;
import me.hash.mediaroulette.model.content.MediaResult;
import me.hash.mediaroulette.model.content.MediaSource;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrbanDictionaryProviderTest {

    @Mock
    private HttpClientWrapper httpClient;

    private UrbanDictionaryProvider provider;

    @BeforeEach
    void setUp() {
        provider = new UrbanDictionaryProvider(httpClient);
    }

    @Test
    void getRandomMedia_Random_Success() throws Exception {
        // Mock JSON response for random word
        String mockResponse = new JSONObject()
                .put("list", new JSONArray()
                        .put(new JSONObject()
                                .put("word", "TestWord")
                                .put("definition", "A test definition with [brackets].")
                                .put("author", "TestAuthor")
                                .put("written_on", "2023-01-01T12:00:00Z")
                        ))
                .toString();

        when(httpClient.getBody(anyString())).thenReturn(mockResponse);

        MediaResult result = provider.getRandomMedia(null);

        assertNotNull(result);
        assertEquals(MediaSource.URBAN_DICTIONARY, result.getSource());
        assertEquals("create", result.getImageType());
        assertEquals("TestWord", result.getImageContent());
        assertTrue(result.getDescription().contains("**Word:** TestWord"));
        // Verify definition processing (brackets replaced with links)
        assertTrue(result.getDescription().contains("[brackets](https://www.urbandictionary.com/define.php?term=brackets)"));
    }

    @Test
    void getRandomMedia_Query_Success() throws Exception {
        String query = "specific";
        String mockResponse = new JSONObject()
                .put("list", new JSONArray()
                        .put(new JSONObject()
                                .put("word", "specific")
                                .put("definition", "Specific definition.")
                                .put("author", "Author")
                                .put("written_on", "2023-01-01T12:00:00Z")
                        ))
                .toString();

        when(httpClient.getBody(anyString())).thenReturn(mockResponse);

        MediaResult result = provider.getRandomMedia(query);

        assertNotNull(result);
        assertEquals("specific", result.getImageContent());
    }

    @Test
    void getRandomMedia_NoResults() throws Exception {
        String mockResponse = new JSONObject()
                .put("list", new JSONArray()) // Empty list
                .toString();

        when(httpClient.getBody(anyString())).thenReturn(mockResponse);

        assertThrows(IOException.class, () -> provider.getRandomMedia("nonexistent"));
    }

    @Test
    void providerMetadata() {
        assertEquals("Urban Dictionary", provider.getProviderName());
        assertTrue(provider.supportsQuery());
    }
}