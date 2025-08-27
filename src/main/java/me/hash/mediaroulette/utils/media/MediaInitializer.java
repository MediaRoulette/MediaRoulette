package me.hash.mediaroulette.utils.media;

import me.hash.mediaroulette.bot.MediaContainerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * Initializes media processing capabilities including FFmpeg download and setup.
 * This should be called during application startup.
 */
public class MediaInitializer {
    private static final Logger logger = LoggerFactory.getLogger(MediaInitializer.class);

    private static boolean initialized = false;
    
    /**
     * Initializes all media processing capabilities
     */
    public static CompletableFuture<Void> initialize() {
        if (initialized) {
            return CompletableFuture.completedFuture(null);
        }

        return MediaContainerManager.initializeFFmpeg()
                .thenRun(() -> {
                    initialized = true;
                    scheduleCleanup();
                })
                .exceptionally(throwable -> {
                    logger.error("Media processing initialization failed: {}", throwable.getMessage(), throwable);
                    return null;
                });
    }
    
    /**
     * Checks if media processing is fully initialized
     */
    public static boolean isInitialized() {
        return initialized && MediaContainerManager.isFFmpegReady();
    }
    
    /**
     * Schedules periodic cleanup of temporary files
     */
    private static void scheduleCleanup() {
        // Run cleanup every 30 minutes
        java.util.concurrent.Executors.newScheduledThreadPool(1)
                .scheduleAtFixedRate(() -> {
                    try {
                        MediaContainerManager.cleanup();
                    } catch (Exception e) {
                        logger.error("Error during scheduled cleanup: {}", e.getMessage());
                    }
                }, 30, 30, java.util.concurrent.TimeUnit.MINUTES);
    }
    
    /**
     * Performs cleanup on shutdown
     */
    public static void shutdown() {
        try {
            MediaContainerManager.cleanup();
            logger.info("Media processing cleanup completed.");
        } catch (Exception e) {
            logger.error("Error during media processing shutdown: {}", e.getMessage());
        }
    }
}