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
    private static java.util.concurrent.ScheduledExecutorService cleanupScheduler;

    private static void scheduleCleanup() {
        // Run cleanup every 30 minutes
        cleanupScheduler = java.util.concurrent.Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "Media-Cleanup-Thread");
            t.setDaemon(true);
            return t;
        });
        
        cleanupScheduler.scheduleAtFixedRate(() -> {
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
            if (cleanupScheduler != null) {
                cleanupScheduler.shutdown();
                try {
                    if (!cleanupScheduler.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                        cleanupScheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    cleanupScheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            MediaContainerManager.cleanup();
            FFmpegDownloader.shutdown();
            logger.info("Media processing cleanup completed.");
        } catch (Exception e) {
            logger.error("Error during media processing shutdown: {}", e.getMessage());
        }
    }
}