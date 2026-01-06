package me.hash.mediaroulette.utils.media.ffmpeg.resolvers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for UrlResolverFactory
 */
@DisplayName("UrlResolverFactory Tests")
class UrlResolverFactoryTest {

    private UrlResolverFactory factory;

    @BeforeEach
    void setUp() {
        factory = new UrlResolverFactory();
    }

    @Nested
    @DisplayName("isVideoUrl tests")
    class IsVideoUrlTests {

        @ParameterizedTest
        @DisplayName("Should detect video URLs with query parameters")
        @CsvSource({
            "https://example.com/video.mp4?12345, true",
            "https://wimg.rule34.xxx//images/6783/e08c9fcfd438faec66a4bbdcc3a93406.mp4?7743470, true",
            "https://example.com/video.webm?v=123&t=10, true",
            "https://example.com/video.mov?token=abc, true",
            "https://cdn.example.com/file.m4v?id=999, true"
        })
        void shouldDetectVideoUrlsWithQueryParams(String url, boolean expected) {
            assertEquals(expected, factory.isVideoUrl(url));
        }

        @ParameterizedTest
        @DisplayName("Should detect video URLs with fragment identifiers")
        @CsvSource({
            "https://example.com/video.mp4#section1, true",
            "https://example.com/video.webm#t=10, true"
        })
        void shouldDetectVideoUrlsWithFragments(String url, boolean expected) {
            assertEquals(expected, factory.isVideoUrl(url));
        }

        @ParameterizedTest
        @DisplayName("Should detect video URLs with both query params and fragments")
        @ValueSource(strings = {
            "https://example.com/video.mp4?token=abc#section",
            "https://example.com/clip.webm?v=1&t=2#anchor"
        })
        void shouldDetectVideoUrlsWithQueryParamsAndFragments(String url) {
            assertTrue(factory.isVideoUrl(url));
        }

        @ParameterizedTest
        @DisplayName("Should detect plain video URLs by extension")
        @ValueSource(strings = {
            "https://example.com/video.mp4",
            "https://example.com/video.webm",
            "https://example.com/video.mov",
            "https://example.com/video.avi",
            "https://example.com/video.mkv",
            "https://example.com/video.flv",
            "https://example.com/video.wmv",
            "https://example.com/video.m4v",
            "https://example.com/video.m4s",
            "https://example.com/video.3gp",
            "https://example.com/video.ogv",
            "https://example.com/video.ts"
        })
        void shouldDetectPlainVideoUrlsByExtension(String url) {
            assertTrue(factory.isVideoUrl(url), "Should detect " + url + " as video");
        }

        @ParameterizedTest
        @DisplayName("Should detect video platform URLs")
        @ValueSource(strings = {
            "https://redgifs.com/watch/happycatrunning",
            "https://www.redgifs.com/watch/fluffybunny",
            "https://gfycat.com/animatedgifname",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://streamable.com/abc123",
            "https://i.imgur.com/abc123.mp4"
        })
        void shouldDetectVideoPlatformUrls(String url) {
            assertTrue(factory.isVideoUrl(url), "Should detect " + url + " as video");
        }

        @ParameterizedTest
        @DisplayName("Should NOT detect image URLs as video")
        @ValueSource(strings = {
            "https://example.com/image.jpg",
            "https://example.com/image.jpeg",
            "https://example.com/image.png",
            "https://example.com/image.gif",
            "https://example.com/image.webp",
            "https://i.imgur.com/abc123.jpg",
            "https://i.imgur.com/abc123.png"
        })
        void shouldNotDetectImageUrlsAsVideo(String url) {
            assertFalse(factory.isVideoUrl(url), "Should NOT detect " + url + " as video");
        }

        @ParameterizedTest
        @DisplayName("Should NOT detect image URLs with query params as video")
        @ValueSource(strings = {
            "https://example.com/image.jpg?width=100",
            "https://example.com/image.png?quality=80",
            "https://cdn.example.com/photo.jpeg?token=abc123"
        })
        void shouldNotDetectImageUrlsWithQueryParamsAsVideo(String url) {
            assertFalse(factory.isVideoUrl(url), "Should NOT detect " + url + " as video");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should handle null and empty URLs")
        void shouldHandleNullAndEmpty(String url) {
            assertFalse(factory.isVideoUrl(url));
        }

        @Test
        @DisplayName("Should handle case-insensitive extensions")
        void shouldHandleCaseInsensitiveExtensions() {
            assertTrue(factory.isVideoUrl("https://example.com/VIDEO.MP4"));
            assertTrue(factory.isVideoUrl("https://example.com/video.Mp4"));
            assertTrue(factory.isVideoUrl("https://example.com/video.WEBM"));
        }

        @Test
        @DisplayName("Should handle URLs with dots in path")
        void shouldHandleUrlsWithDotsInPath() {
            assertTrue(factory.isVideoUrl("https://example.com/path.with.dots/video.mp4"));
            assertFalse(factory.isVideoUrl("https://example.com/path.mp4/image.jpg"));
        }

        @Test
        @DisplayName("Should handle URLs with special characters")
        void shouldHandleUrlsWithSpecialCharacters() {
            assertTrue(factory.isVideoUrl("https://example.com/video%20name.mp4?param=value"));
            assertTrue(factory.isVideoUrl("https://example.com/video+name.mp4"));
        }
    }

    @Nested
    @DisplayName("shouldConvertToGif tests")
    class ShouldConvertToGifTests {

        @ParameterizedTest
        @DisplayName("Should identify URLs that should convert to GIF")
        @ValueSource(strings = {
            "https://redgifs.com/watch/example",
            "https://www.redgifs.com/watch/example",
            "https://gfycat.com/example",
            "https://streamable.com/abc123",
            "https://i.imgur.com/abc123.mp4"
        })
        void shouldIdentifyGifConversionUrls(String url) {
            assertTrue(factory.shouldConvertToGif(url), "Should convert " + url + " to GIF");
        }

        @ParameterizedTest
        @DisplayName("Should NOT convert regular video URLs to GIF")
        @ValueSource(strings = {
            "https://example.com/video.mp4",
            "https://youtube.com/watch?v=abc",
            "https://example.com/video.webm"
        })
        void shouldNotConvertRegularVideoUrls(String url) {
            assertFalse(factory.shouldConvertToGif(url), "Should NOT convert " + url + " to GIF");
        }

        @Test
        @DisplayName("Should handle imgur URLs with query params")
        void shouldHandleImgurUrlsWithQueryParams() {
            assertTrue(factory.shouldConvertToGif("https://i.imgur.com/abc123.mp4?source=reddit"));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should handle null and empty URLs")
        void shouldHandleNullAndEmpty(String url) {
            assertFalse(factory.shouldConvertToGif(url));
        }
    }

    @Nested
    @DisplayName("getVideoPreviewUrl tests")
    class GetVideoPreviewUrlTests {

        @Test
        @DisplayName("Should generate RedGifs preview URL")
        void shouldGenerateRedGifsPreviewUrl() {
            String result = factory.getVideoPreviewUrl("https://redgifs.com/watch/happycat");
            assertNotNull(result);
            assertTrue(result.contains("ifr"));
        }

        @Test
        @DisplayName("Should generate Gfycat preview URL")
        void shouldGenerateGfycatPreviewUrl() {
            String result = factory.getVideoPreviewUrl("https://gfycat.com/happycatrunning");
            assertNotNull(result);
            assertTrue(result.contains("thumbs.gfycat.com"));
            assertTrue(result.contains("-poster.jpg"));
        }

        @Test
        @DisplayName("Should generate Imgur video preview URL")
        void shouldGenerateImgurPreviewUrl() {
            String result = factory.getVideoPreviewUrl("https://i.imgur.com/abc123.mp4");
            assertEquals("https://i.imgur.com/abc123h.jpg", result);
        }

        @Test
        @DisplayName("Should return null for unknown video sources")
        void shouldReturnNullForUnknownSources() {
            assertNull(factory.getVideoPreviewUrl("https://example.com/video.mp4"));
            assertNull(factory.getVideoPreviewUrl("https://youtube.com/watch?v=abc"));
        }
    }

    @Nested
    @DisplayName("getResolver tests")
    class GetResolverTests {

        @Test
        @DisplayName("Should return DirectUrlResolver for unknown URLs")
        void shouldReturnDirectResolverForUnknownUrls() {
            var resolver = factory.getResolver("https://example.com/video.mp4");
            assertNotNull(resolver);
        }
    }
}
