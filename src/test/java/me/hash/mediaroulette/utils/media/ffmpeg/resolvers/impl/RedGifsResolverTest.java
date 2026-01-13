package me.hash.mediaroulette.utils.media.ffmpeg.resolvers.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RedGifsResolver Tests")
class RedGifsResolverTest {

    private RedGifsResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new RedGifsResolver();
    }

    @Nested
    @DisplayName("canResolve tests")
    class CanResolveTests {

        @ParameterizedTest
        @DisplayName("Should resolve various RedGifs watch URLs")
        @ValueSource(strings = {
            "https://redgifs.com/watch/happycat",
            "https://www.redgifs.com/watch/happycat",
            "https://v3.redgifs.com/watch/happycat",
            "http://redgifs.com/watch/happycat",
            "https://redgifs.com/watch/HappyCat",
            "https://redgifs.com/watch/improbablewebbedsmew",
            "https://redgifs.com/watch/faroffbossyhippopotamus"
        })
        void shouldResolveWatchUrls(String url) {
            assertTrue(resolver.canResolve(url), "Should resolve: " + url);
        }

        @ParameterizedTest
        @DisplayName("Should resolve various RedGifs ifr URLs")
        @ValueSource(strings = {
            "https://redgifs.com/ifr/happycat",
            "https://www.redgifs.com/ifr/happycat",
            "https://v3.redgifs.com/ifr/happycat",
            "http://redgifs.com/ifr/HAPPYCAT"
        })
        void shouldResolveIfrUrls(String url) {
            assertTrue(resolver.canResolve(url), "Should resolve: " + url);
        }

        @ParameterizedTest
        @DisplayName("Should NOT resolve non-RedGifs URLs")
        @ValueSource(strings = {
            "https://gfycat.com/happycat",
            "https://imgur.com/abc123",
            "https://example.com/video.mp4",
            "https://youtube.com/watch?v=abc123",
            "https://redgifs.com/users/someone",
            "https://redgifs.com/browse"
        })
        void shouldNotResolveNonRedGifsUrls(String url) {
            assertFalse(resolver.canResolve(url), "Should NOT resolve: " + url);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should handle null and empty URLs")
        void shouldHandleNullAndEmpty(String url) {
            assertFalse(resolver.canResolve(url));
        }
    }

    @Nested
    @DisplayName("extractGifId tests")
    class ExtractGifIdTests {

        @ParameterizedTest
        @DisplayName("Should extract GIF ID from various URL formats")
        @CsvSource({
            "https://redgifs.com/watch/happycat, happycat",
            "https://www.redgifs.com/watch/FluffyBunny, fluffybunny",
            "https://v3.redgifs.com/watch/UPPERCASE, uppercase",
            "https://redgifs.com/ifr/ifrformat, ifrformat",
            "https://www.redgifs.com/ifr/Mixed123, mixed123"
        })
        void shouldExtractGifId(String url, String expectedId) {
            assertEquals(expectedId, resolver.extractGifId(url));
        }

        @Test
        @DisplayName("Should return null for invalid URLs")
        void shouldReturnNullForInvalidUrls() {
            assertNull(resolver.extractGifId("https://example.com/video.mp4"));
            assertNull(resolver.extractGifId("https://redgifs.com/users/someone"));
            assertNull(resolver.extractGifId(null));
        }
    }

    @Nested
    @DisplayName("Priority tests")
    class PriorityTests {

        @Test
        @DisplayName("Should have higher priority than DirectUrlResolver")
        void shouldHaveHigherPriorityThanDirect() {
            DirectUrlResolver directResolver = new DirectUrlResolver();
            assertTrue(resolver.getPriority() > directResolver.getPriority(),
                "RedGifsResolver priority (" + resolver.getPriority() + 
                ") should be higher than DirectUrlResolver (" + directResolver.getPriority() + ")");
        }

        @Test
        @DisplayName("Should have positive priority")
        void shouldHavePositivePriority() {
            assertTrue(resolver.getPriority() > 0, "Priority should be positive");
        }
    }

    @Nested
    @DisplayName("resolve tests")
    class ResolveTests {

        @Test
        @DisplayName("Should return original URL on network error (graceful fallback)")
        void shouldReturnOriginalUrlOnError() throws Exception {
            // This test verifies graceful fallback behavior
            // Without mocking, if network is unavailable, it should return original URL
            String url = "https://redgifs.com/watch/nonexistent12345xyz";
            String result = resolver.resolve(url).get();
            // Result should either be a valid direct URL or the original URL (graceful fallback)
            assertNotNull(result);
            // If API call failed, should return original URL
            assertTrue(result.equals(url) || result.contains(".mp4") || result.contains("thumbs"),
                "Should return original URL or resolved URL, got: " + result);
        }
    }
}
