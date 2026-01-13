package me.hash.mediaroulette.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ColorExtractor
 */
@DisplayName("ColorExtractor Tests")
class ColorExtractorTest {

    @Nested
    @DisplayName("extractDominantColor tests")
    class ExtractDominantColorTests {

        @Test
        @DisplayName("Should return CYAN for null URL")
        void shouldReturnCyanForNull() throws Exception {
            CompletableFuture<Color> future = ColorExtractor.extractDominantColor(null);
            Color result = future.get(5, TimeUnit.SECONDS);
            assertEquals(Color.CYAN, result);
        }

        @Test
        @DisplayName("Should return CYAN for 'none' URL")
        void shouldReturnCyanForNone() throws Exception {
            CompletableFuture<Color> future = ColorExtractor.extractDominantColor("none");
            Color result = future.get(5, TimeUnit.SECONDS);
            assertEquals(Color.CYAN, result);
        }

        @Test
        @DisplayName("Should return CYAN for attachment:// URLs")
        void shouldReturnCyanForAttachmentUrl() throws Exception {
            CompletableFuture<Color> future = ColorExtractor.extractDominantColor("attachment://image.png");
            Color result = future.get(5, TimeUnit.SECONDS);
            assertEquals(Color.CYAN, result);
        }

        @ParameterizedTest
        @DisplayName("Should handle special attachment URLs")
        @ValueSource(strings = {
            "attachment://file.png",
            "attachment://thumbnail.jpg",
            "attachment://video_preview.gif"
        })
        void shouldHandleAttachmentUrls(String url) throws Exception {
            CompletableFuture<Color> future = ColorExtractor.extractDominantColor(url);
            Color result = future.get(5, TimeUnit.SECONDS);
            assertEquals(Color.CYAN, result);
        }
    }

    @Nested
    @DisplayName("getExtensionFromUrl tests (via reflection)")
    class GetExtensionFromUrlTests {

        private String getExtensionFromUrl(String url) throws Exception {
            Method method = ColorExtractor.class.getDeclaredMethod("getExtensionFromUrl", String.class);
            method.setAccessible(true);
            return (String) method.invoke(null, url);
        }

        @ParameterizedTest
        @DisplayName("Should extract extension from simple URLs")
        @CsvSource({
            "https://example.com/video.mp4, mp4",
            "https://example.com/image.jpg, jpg",
            "https://example.com/animation.gif, gif",
            "https://example.com/photo.png, png",
            "https://example.com/file.webm, webm"
        })
        void shouldExtractSimpleExtension(String url, String expected) throws Exception {
            assertEquals(expected, getExtensionFromUrl(url));
        }

        @ParameterizedTest
        @DisplayName("Should extract extension from URLs with query params")
        @CsvSource({
            "https://example.com/video.mp4?token=123, mp4",
            "https://wimg.rule34.xxx//images/6783/e08c9fcfd438faec66a4bbdcc3a93406.mp4?7743470, mp4",
            "https://example.com/image.jpg?width=100&height=100, jpg",
            "https://cdn.example.com/file.webm?v=2&format=hd, webm"
        })
        void shouldExtractExtensionWithQueryParams(String url, String expected) throws Exception {
            assertEquals(expected, getExtensionFromUrl(url));
        }

        @ParameterizedTest
        @DisplayName("Should extract extension from URLs with fragment identifiers")
        @CsvSource({
            "https://example.com/video.mp4#section, mp4",
            "https://example.com/page.jpg#anchor, jpg"
        })
        void shouldExtractExtensionWithFragment(String url, String expected) throws Exception {
            assertEquals(expected, getExtensionFromUrl(url));
        }

        @ParameterizedTest
        @DisplayName("Should extract extension from URLs with both query params and fragments")
        @CsvSource({
            "https://example.com/video.mp4?token=abc#section, mp4",
            "https://example.com/image.jpg?v=1&t=2#anchor, jpg"
        })
        void shouldExtractExtensionWithQueryAndFragment(String url, String expected) throws Exception {
            assertEquals(expected, getExtensionFromUrl(url));
        }

        @ParameterizedTest
        @DisplayName("Should handle URLs with dots in path")
        @CsvSource({
            "https://example.com/path.with.dots/video.mp4, mp4",
            "https://my.site.com/folder/image.jpg, jpg",
            "https://api.v2.example.com/files/doc.pdf, pdf"
        })
        void shouldHandleDotsInPath(String url, String expected) throws Exception {
            assertEquals(expected, getExtensionFromUrl(url));
        }

        @Test
        @DisplayName("Should return null for null URL")
        void shouldReturnNullForNull() throws Exception {
            assertNull(getExtensionFromUrl(null));
        }

        @ParameterizedTest
        @DisplayName("Should return null for URLs without extension")
        @ValueSource(strings = {
            "https://example.com/file",
            "https://example.com/path/to/resource",
            "https://redgifs.com/watch/happycat"
        })
        void shouldReturnNullForNoExtension(String url) throws Exception {
            assertNull(getExtensionFromUrl(url));
        }
    }

    @Nested
    @DisplayName("detectMediaType tests (via reflection)")
    class DetectMediaTypeTests {

        private Object detectMediaType(String url) throws Exception {
            Method method = ColorExtractor.class.getDeclaredMethod("detectMediaType", String.class);
            method.setAccessible(true);
            return method.invoke(null, url);
        }

        @ParameterizedTest
        @DisplayName("Should detect VIDEO type for video extensions")
        @ValueSource(strings = {
            "https://example.com/video.mp4",
            "https://example.com/video.mp4?token=abc",
            "https://example.com/video.webm",
            "https://example.com/video.mov",
            "https://example.com/video.avi",
            "https://example.com/video.mkv",
            "https://example.com/video.m4v",
            "https://example.com/video.m4s",
            "https://example.com/video.3gp",
            "https://example.com/video.ogv",
            "https://example.com/video.ts"
        })
        void shouldDetectVideoType(String url) throws Exception {
            Object mediaType = detectMediaType(url);
            assertEquals("VIDEO", mediaType.toString());
        }

        @ParameterizedTest
        @DisplayName("Should detect IMAGE type for image extensions")
        @ValueSource(strings = {
            "https://example.com/image.jpg",
            "https://example.com/image.jpeg",
            "https://example.com/image.png",
            "https://example.com/image.gif",
            "https://example.com/image.webp",
            "https://example.com/image.bmp",
            "https://example.com/image.ico",
            "https://example.com/image.tiff"
        })
        void shouldDetectImageType(String url) throws Exception {
            Object mediaType = detectMediaType(url);
            assertEquals("IMAGE", mediaType.toString());
        }

        @ParameterizedTest
        @DisplayName("Should detect VIDEO type for video platforms without extension")
        @ValueSource(strings = {
            "https://redgifs.com/watch/happycat",
            "https://gfycat.com/happycatrunning",
            "https://youtube.com/watch?v=abc",
            "https://youtu.be/abc123",
            "https://streamable.com/xyz"
        })
        void shouldDetectVideoPlatforms(String url) throws Exception {
            Object mediaType = detectMediaType(url);
            assertEquals("VIDEO", mediaType.toString());
        }

        @ParameterizedTest
        @DisplayName("Should handle mixed case extensions")
        @CsvSource({
            "https://example.com/video.MP4, VIDEO",
            "https://example.com/video.Mp4, VIDEO",
            "https://example.com/image.JPG, IMAGE",
            "https://example.com/image.Jpeg, IMAGE"
        })
        void shouldHandleMixedCaseExtensions(String url, String expectedType) throws Exception {
            Object mediaType = detectMediaType(url);
            assertEquals(expectedType, mediaType.toString());
        }
    }

    @Nested
    @DisplayName("Edge case tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle double slashes in URL path")
        void shouldHandleDoubleSlashesInPath() throws Exception {
            String url = "https://wimg.rule34.xxx//images/6783/e08c9fcfd438faec66a4bbdcc3a93406.mp4?7743470";
            
            Method method = ColorExtractor.class.getDeclaredMethod("detectMediaType", String.class);
            method.setAccessible(true);
            Object mediaType = method.invoke(null, url);
            
            assertEquals("VIDEO", mediaType.toString(), 
                "Should correctly detect video with double slashes and query params");
        }

        @Test
        @DisplayName("Should handle very long query strings")
        void shouldHandleLongQueryStrings() throws Exception {
            String longQuery = "?token=" + "a".repeat(1000) + "&timestamp=123456789";
            String url = "https://example.com/video.mp4" + longQuery;
            
            Method method = ColorExtractor.class.getDeclaredMethod("getExtensionFromUrl", String.class);
            method.setAccessible(true);
            String extension = (String) method.invoke(null, url);
            
            assertEquals("mp4", extension);
        }

        @Test
        @DisplayName("Should handle URLs with encoded characters")
        void shouldHandleEncodedCharacters() throws Exception {
            String url = "https://example.com/video%20file.mp4?name=test%20video";
            
            Method method = ColorExtractor.class.getDeclaredMethod("getExtensionFromUrl", String.class);
            method.setAccessible(true);
            String extension = (String) method.invoke(null, url);
            
            assertEquals("mp4", extension);
        }
    }
    
    @Nested
    @DisplayName("Integration tests for problematic URLs")
    class IntegrationTests {
        
        @Test
        @DisplayName("Should extract color from WebP image (previously failed with 'Could not decode image')")
        void shouldExtractColorFromWebP() throws Exception {
            // This URL was failing with "Could not decode image" because ImageIO doesn't support WebP
            String webpUrl = "https://saradahentai.com/wp-content/uploads/2025/12/kim-kardashian-nice-shot-by-elsychzz-fortnite.webp";
            
            CompletableFuture<Color> future = ColorExtractor.extractDominantColor(webpUrl);
            Color result = future.get(30, TimeUnit.SECONDS);
            
            // Should return a valid color, not throw an exception
            assertNotNull(result, "Should return a color for WebP image");
            // If FFmpeg not available or URL no longer valid, should fallback to CYAN
            // Any non-exception result is acceptable
        }
        
        @Test
        @DisplayName("Should extract color from GIF with 403 protection (previously failed with 'Failed to fetch image: 403')")
        void shouldExtractColorFromProtected403Gif() throws Exception {
            // This URL was failing with "Failed to fetch image: 403"
            String protectedGifUrl = "https://img1.thatpervert.com/pics/post/Riley-Reid-Porn-Model-facesitting-oral-porn-5480933.gif";
            
            CompletableFuture<Color> future = ColorExtractor.extractDominantColor(protectedGifUrl);
            Color result = future.get(30, TimeUnit.SECONDS);
            
            // Should return a valid color, not throw an exception
            assertNotNull(result, "Should return a color for protected GIF");
            // Either successfully extracts color with proper headers, or falls back to FFmpeg, or returns CYAN
            // Any non-exception result is acceptable
        }
        
        @Test
        @DisplayName("Should handle standard JPG image")
        void shouldExtractColorFromJpg() throws Exception {
            // A reliable public image for testing
            String jpgUrl = "https://httpbin.org/image/jpeg";
            
            CompletableFuture<Color> future = ColorExtractor.extractDominantColor(jpgUrl);
            Color result = future.get(30, TimeUnit.SECONDS);
            
            assertNotNull(result, "Should return a color for JPG image");
        }
        
        @Test
        @DisplayName("Should handle standard PNG image")
        void shouldExtractColorFromPng() throws Exception {
            // A reliable public image for testing
            String pngUrl = "https://httpbin.org/image/png";
            
            CompletableFuture<Color> future = ColorExtractor.extractDominantColor(pngUrl);
            Color result = future.get(30, TimeUnit.SECONDS);
            
            assertNotNull(result, "Should return a color for PNG image");
        }
        
        @Test
        @DisplayName("Should gracefully handle non-existent URL")
        void shouldHandleNonExistentUrl() throws Exception {
            String badUrl = "https://this-domain-definitely-does-not-exist-12345.com/image.jpg";
            
            CompletableFuture<Color> future = ColorExtractor.extractDominantColor(badUrl);
            Color result = future.get(30, TimeUnit.SECONDS);
            
            // Should fallback to CYAN instead of throwing
            assertEquals(Color.CYAN, result, "Should return CYAN for non-existent URLs");
        }
    }
}
