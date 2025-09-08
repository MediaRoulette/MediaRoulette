// PicsumProvider.java
package me.hash.mediaroulette.content.provider.impl.images;

import me.hash.mediaroulette.model.content.MediaResult;
import me.hash.mediaroulette.model.content.MediaSource;
import me.hash.mediaroulette.content.provider.MediaProvider;
import me.hash.mediaroulette.content.http.HttpClientWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class PicsumProvider implements MediaProvider {
    private static final Logger logger = LoggerFactory.getLogger(PicsumProvider.class);
    private final HttpClientWrapper httpClient;

    public PicsumProvider(HttpClientWrapper httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public MediaResult getRandomMedia(String query) throws IOException {
        // Try different approaches to get a random Picsum image
        String imageUrl = getRandomPicsumImage();

        return new MediaResult(
                imageUrl,
                "Here is your random Picsum image!",
                "Source: Picsum - Resolution: 1920x1080 - Random ID: " + extractImageId(imageUrl),
                MediaSource.PICSUM
        );
    }

    private String getRandomPicsumImage() throws IOException {
        try {
            return getRandomImageDirect();
        } catch (IOException e) {
            logger.error("Method 1 failed: {}", e.getMessage());
        }

        try {
            return getRandomImageById();
        } catch (IOException e) {
            logger.error("Method 2 failed: {}", e.getMessage());
        }

        // Method 3: Fallback to a specific image
        return getFallbackImage();
    }

    private String getRandomImageById() throws IOException {
        // Generate a random image ID (Picsum has images from 1 to ~1000+)
        int randomId = (int) (Math.random() * 1000) + 1;
        String url = "https://picsum.photos/id/" + randomId + "/1920/1080";
        
        try {
            // Get response without following redirects to capture the Location header
            var response = httpClient.getWithoutRedirects(url);
            
            // Check if it's a redirect response (3xx status codes)
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                var locationHeader = response.headers().firstValue("Location");
                if (locationHeader.isPresent()) {
                    String redirectUrl = locationHeader.get();
                    // Make sure it's an absolute URL
                    if (redirectUrl.startsWith("/")) {
                        redirectUrl = "https://picsum.photos" + redirectUrl;
                    }
                    return redirectUrl;
                }
            }
            
            // If no redirect or redirect failed, try the normal method
            String finalUrl = httpClient.getFinalUrl(url);
            return finalUrl;
        } catch (Exception e) {
            throw new IOException("Failed to get image with ID " + randomId + ": " + e.getMessage());
        }
    }

    private String getRandomImageDirect() throws IOException {
        String url = "https://picsum.photos/1920/1080";
        
        try {
            // Get response without following redirects to capture the Location header
            var response = httpClient.getWithoutRedirects(url);
            
            // Check if it's a redirect response (3xx status codes)
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                var locationHeader = response.headers().firstValue("Location");
                if (locationHeader.isPresent()) {
                    String redirectUrl = locationHeader.get();
                    // Make sure it's an absolute URL
                    if (redirectUrl.startsWith("/")) {
                        redirectUrl = "https://picsum.photos" + redirectUrl;
                    }
                    return redirectUrl;
                }
            }
            
            // If no redirect or redirect failed, try the normal method
            String finalUrl = httpClient.getFinalUrl(url);
            return finalUrl;
        } catch (Exception e) {
            throw new IOException("Picsum API returned error: " + e.getMessage());
        }
    }

    private String getFallbackImage() {
        // Fallback to a known working image - use direct image URL
        try {
            var response = httpClient.getWithoutRedirects("https://picsum.photos/id/1/1920/1080");
            
            // Check if it's a redirect response (3xx status codes)
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                var locationHeader = response.headers().firstValue("Location");
                if (locationHeader.isPresent()) {
                    String redirectUrl = locationHeader.get();
                    // Make sure it's an absolute URL
                    if (redirectUrl.startsWith("/")) {
                        redirectUrl = "https://picsum.photos" + redirectUrl;
                    }
                    return redirectUrl;
                }
            }
            
            return httpClient.getFinalUrl("https://picsum.photos/id/1/1920/1080");
        } catch (Exception e) {
            // Ultimate fallback - return the redirect URL
            return "https://picsum.photos/id/1/1920/1080";
        }
    }

    private String extractImageId(String imageUrl) {
        try {
            if (imageUrl.contains("/id/")) {
                String[] parts = imageUrl.split("/id/")[1].split("/");
                return parts[0];
            }
            return "Random";
        } catch (Exception e) {
            return "Unknown";
        }
    }

    @Override
    public boolean supportsQuery() {
        return false;
    }

    @Override
    public String getProviderName() {
        return "Picsum";
    }
}