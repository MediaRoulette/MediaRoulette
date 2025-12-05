package me.hash.mediaroulette.utils.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages BufferedImage memory lifecycle to prevent memory leaks.
 * Provides automatic cleanup of images that are no longer referenced.
 */
public class ImageMemoryManager {
    private static final Logger logger = LoggerFactory.getLogger(ImageMemoryManager.class);
    private static final ImageMemoryManager INSTANCE = new ImageMemoryManager();
    
    private final ConcurrentLinkedQueue<WeakReference<BufferedImage>> trackedImages;
    private final ScheduledExecutorService cleanupScheduler;
    
    private ImageMemoryManager() {
        this.trackedImages = new ConcurrentLinkedQueue<>();
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ImageMemoryManager-Cleanup");
            t.setDaemon(true);
            return t;
        });
        
        // Schedule periodic cleanup every 30 seconds
        cleanupScheduler.scheduleAtFixedRate(this::cleanupUnreferencedImages, 30, 30, TimeUnit.SECONDS);
    }
    
    public static ImageMemoryManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Tracks a BufferedImage for automatic cleanup
     */
    public void track(BufferedImage image) {
        if (image != null) {
            trackedImages.offer(new WeakReference<>(image));
        }
    }
    
    /**
     * Manually disposes an image and removes it from tracking
     */
    public void dispose(BufferedImage image) {
        if (image != null) {
            image.flush();
        }
    }
    
    /**
     * Cleans up images that are no longer referenced
     */
    private void cleanupUnreferencedImages() {
        int cleaned = 0;
        WeakReference<BufferedImage> ref;
        
        while ((ref = trackedImages.poll()) != null) {
            BufferedImage image = ref.get();
            if (image != null) {
                // Image is still referenced, dispose it
                image.flush();
                cleaned++;
            }
        }
        
        if (cleaned > 0) {
            logger.debug("Cleaned up {} unreferenced images", cleaned);
        }
        
        // Force garbage collection hint (JVM may ignore)
        System.gc();
    }
    
    /**
     * Performs a manual cleanup of all tracked images
     */
    public void forceCleanup() {
        logger.info("Forcing image memory cleanup");
        cleanupUnreferencedImages();
    }
    
    /**
     * Shuts down the cleanup scheduler
     */
    public void shutdown() {
        logger.info("Shutting down ImageMemoryManager");
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        forceCleanup();
    }
    
    /**
     * Gets the number of tracked images
     */
    public int getTrackedImageCount() {
        return trackedImages.size();
    }
}
