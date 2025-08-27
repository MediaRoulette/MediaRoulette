package me.hash.mediaroulette.utils.media.ffmpeg.resolvers.impl;

import me.hash.mediaroulette.utils.media.ffmpeg.resolvers.UrlResolver;
import me.hash.mediaroulette.utils.browser.PlaywrightBrowser;
import me.hash.mediaroulette.utils.media.M3u8Parser;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Concise RedGifs resolver using Playwright and M3U8 parsing
 */
public class RedGifsResolver implements UrlResolver {
    private static final Logger logger = LoggerFactory.getLogger(RedGifsResolver.class);

    @Override
    public boolean canResolve(String url) {
        return url != null && url.contains("redgifs.com");
    }

    @Override
    public CompletableFuture<String> resolve(String url) {
        if (!url.contains("redgifs.com/watch/")) {
            return CompletableFuture.completedFuture(url);
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String gifId = M3u8Parser.extractGifId(url);
                if (gifId == null) return url;
                
                // Try M3U8 approach first
                String m3u8Url = M3u8Parser.buildRedGifsM3u8Url(gifId);
                String videoUrl = M3u8Parser.extractVideoUrl(m3u8Url);
                if (videoUrl != null) {
                    return videoUrl.replace(".m4s", ".mp4");
                }
                
                // Fallback to browser scraping with improved stability
                return PlaywrightBrowser.executeWithPage(url, page -> {
                    try {
                        // Wait for the player to load
                        page.waitForSelector(".Player", new Page.WaitForSelectorOptions().setTimeout(8000));
                        
                        // Get poster URL first (most reliable)
                        String posterSrc = (String) page.evaluate(
                            "() => document.querySelector('.Player-Poster')?.src"
                        );
                        
                        if (posterSrc != null && posterSrc.contains("redgifs.com")) {
                            String gifName = extractGifNameFromPoster(posterSrc);
                            if (gifName != null) {
                                // Try direct video URL patterns based on poster
                                String[] patterns = {
                                    "https://thumbs.redgifs.com/" + gifName + ".mp4",
                                    posterSrc.replace("-mobile.jpg", ".mp4").replace(".jpg", ".mp4")
                                };
                                
                                for (String pattern : patterns) {
                                    if (isUrlAccessible(pattern)) {
                                        return pattern;
                                    }
                                }
                            }
                        }
                        
                        // Try to get video source from DOM
                        String videoSrc = (String) page.evaluate(
                            "() => { " +
                            "const video = document.querySelector('.Player-Video video'); " +
                            "return video ? video.src || video.getAttribute('src') : null; " +
                            "}"
                        );
                        
                        if (videoSrc != null && !videoSrc.startsWith("blob:") && videoSrc.contains(".mp4")) {
                            return videoSrc;
                        }
                        
                    } catch (Exception e) {
                        logger.error("Browser scraping error: {}", e.getMessage());
                    }
                    
                    return url;
                });
                
            } catch (Exception e) {
                logger.error("Failed to resolve RedGifs URL: {}", e.getMessage());
                return url;
            }
        });
    }
    
    private String extractGifNameFromPoster(String posterUrl) {
        try {
            String[] parts = posterUrl.split("/");
            String filename = parts[parts.length - 1];
            return filename.replace("-mobile.jpg", "").replace(".jpg", "");
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isUrlAccessible(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            connection.setInstanceFollowRedirects(true);
            int responseCode = connection.getResponseCode();
            return responseCode >= 200 && responseCode < 400;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int getPriority() {
        return 10; // High priority for RedGifs URLs
    }
    
    /**
     * Debug method to resolve with visible browser for inspection
     */
    public String resolveWithVisibleBrowser(String url) {
        if (!url.contains("redgifs.com/watch/")) {
            return url;
        }
        
        try {
            String gifId = M3u8Parser.extractGifId(url);
            if (gifId == null) return url;
            
            // Try M3U8 approach first
            String m3u8Url = M3u8Parser.buildRedGifsM3u8Url(gifId);
            String videoUrl = M3u8Parser.extractVideoUrl(m3u8Url);
            if (videoUrl != null) {
                return videoUrl;
            }
            
            // Fallback to browser scraping with visible browser
            return PlaywrightBrowser.executeWithVisiblePage(url, page -> {
                logger.info("Inspecting page: {}", url);

                String videoSrc = (String) page.evaluate(
                    "() => document.querySelector('.Player-Video video')?.src"
                );
                logger.info("Video src: {}", videoSrc);
                
                if (videoSrc != null && videoSrc.startsWith("blob:")) {
                    String posterSrc = (String) page.evaluate(
                        "() => document.querySelector('.Player-Poster')?.src"
                    );
                    logger.info("Poster src: {}", posterSrc);
                    
                    if (posterSrc != null && posterSrc.endsWith(".jpg")) {
                        String mp4Url = posterSrc.replace(".jpg", ".mp4");
                        logger.info("Converted to MP4: {}", mp4Url);
                        return mp4Url;
                    }
                }
                
                if (videoSrc != null && !videoSrc.startsWith("blob:") && videoSrc.contains(".mp4")) {
                    logger.info("Direct video URL: {}", videoSrc);
                    return videoSrc;
                }
                
                return url;
            });
            
        } catch (Exception e) {
            logger.error("Failed to resolve RedGifs URL: {}", e.getMessage());
            return url;
        }
    }
}