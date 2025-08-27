package me.hash.mediaroulette.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.hash.mediaroulette.service.GiveawayService;
import me.hash.mediaroulette.model.Giveaway;

import java.util.List;

/**
 * Manages giveaway lifecycle and startup recovery
 */
public class GiveawayManager {
    private static final Logger logger = LoggerFactory.getLogger(GiveawayManager.class);
    private static GiveawayService giveawayService;
    
    public static void initialize() {
        giveawayService = new GiveawayService();
        
        // Check for giveaways that ended while bot was offline
        resumeGiveaways();
    }
    
    /**
     * Resume giveaways that may have ended while bot was offline
     */
    private static void resumeGiveaways() {
        try {
            List<Giveaway> activeGiveaways = giveawayService.getActiveGiveaways();
            
            int endedCount = 0;
            for (Giveaway giveaway : activeGiveaways) {
                if (giveaway.isExpired() && !giveaway.isCompleted()) {
                    logger.info("Ending giveaway that expired while offline: {}", giveaway.getId());
                    giveawayService.endGiveaway(giveaway);
                    endedCount++;
                }
            }
            
            if (endedCount > 0) {
                logger.info("Resumed and ended {} expired giveaways", endedCount);
            }
            
        } catch (Exception e) {
            logger.error("Error resuming giveaways: {}", e.getMessage());
        }
    }
    
    public static GiveawayService getGiveawayService() {
        return giveawayService;
    }
    
    public static void shutdown() {
        if (giveawayService != null) {
            giveawayService.shutdown();
        }
    }
}