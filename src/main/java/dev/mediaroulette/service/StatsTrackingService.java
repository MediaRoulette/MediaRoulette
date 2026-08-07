package dev.mediaroulette.service;

import com.opencsv.CSVWriter;
import dev.mediaroulette.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for tracking and logging bot statistics to CSV files for analysis.
 * Tracks hourly data for images generated, user activity, source usage, etc.
 */
public class StatsTrackingService {
    private static final Logger logger = LoggerFactory.getLogger(StatsTrackingService.class);

    private static final String STATS_DIR = "stats";
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH");
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AccountRepository accountRepository;
    private final ScheduledExecutorService scheduler;

    // Hourly counters (reset every hour)
    private final AtomicLong hourlyImagesGenerated = new AtomicLong(0);
    private final AtomicLong hourlyCommandsUsed = new AtomicLong(0);
    private final AtomicLong hourlyActiveUsers = new AtomicLong(0);
    private final AtomicLong hourlyNewUsers = new AtomicLong(0);
    private final AtomicLong hourlyCoinsEarned = new AtomicLong(0);
    private final AtomicLong hourlyCoinsSpent = new AtomicLong(0);
    private final AtomicLong hourlyQuestsCompleted = new AtomicLong(0);

    // Track unique users per hour
    private final Set<String> hourlyUniqueUsers = ConcurrentHashMap.newKeySet();

    // Track source usage per hour
    private final Map<String, AtomicLong> hourlySourceUsage = new ConcurrentHashMap<>();

    // Track command usage per hour
    private final Map<String, AtomicLong> hourlyCommandUsage = new ConcurrentHashMap<>();

    // Track theme usage per hour
    private final Map<String, AtomicLong> hourlyThemeUsage = new ConcurrentHashMap<>();

    // Track NSFW vs SFW usage per hour
    private final AtomicLong hourlyNsfwRequests = new AtomicLong(0);
    private final AtomicLong hourlySfwRequests = new AtomicLong(0);

    // Track premium vs regular users activity
    private final AtomicLong hourlyPremiumActivity = new AtomicLong(0);
    private final AtomicLong hourlyRegularActivity = new AtomicLong(0);

    public StatsTrackingService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
        this.scheduler = Executors.newScheduledThreadPool(2);

        // Create stats directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(STATS_DIR));
        } catch (IOException e) {
            logger.error("Failed to create stats directory", e);
        }

        // Schedule hourly stats logging
        scheduleHourlyStatsLogging();

        // Schedule daily summary generation
        scheduleDailySummaryGeneration();
    }

    /**
     * Track image generation
     */
    public void trackImageGenerated(String userId, String source, boolean isNsfw, boolean isPremium) {
        hourlyImagesGenerated.incrementAndGet();
        hourlyUniqueUsers.add(userId);

        // Track source usage
        hourlySourceUsage.computeIfAbsent(source, k -> new AtomicLong(0)).incrementAndGet();

        // Track NSFW vs SFW
        if (isNsfw) {
            hourlyNsfwRequests.incrementAndGet();
        } else {
            hourlySfwRequests.incrementAndGet();
        }

        // Track premium vs regular
        if (isPremium) {
            hourlyPremiumActivity.incrementAndGet();
        } else {
            hourlyRegularActivity.incrementAndGet();
        }
    }

    /**
     * Track command usage
     */
    public void trackCommandUsed(String userId, String command, boolean isPremium) {
        hourlyCommandsUsed.incrementAndGet();
        hourlyUniqueUsers.add(userId);

        // Track specific command usage
        hourlyCommandUsage.computeIfAbsent(command, k -> new AtomicLong(0)).incrementAndGet();

        // Track premium vs regular
        if (isPremium) {
            hourlyPremiumActivity.incrementAndGet();
        } else {
            hourlyRegularActivity.incrementAndGet();
        }
    }

    /**
     * Track new user registration
     */
    public void trackNewUser(String userId) {
        hourlyNewUsers.incrementAndGet();
        hourlyUniqueUsers.add(userId);
    }

    /**
     * Track user activity (any interaction)
     */
    public void trackUserActivity(String userId, boolean isPremium) {
        hourlyActiveUsers.incrementAndGet();
        hourlyUniqueUsers.add(userId);

        if (isPremium) {
            hourlyPremiumActivity.incrementAndGet();
        } else {
            hourlyRegularActivity.incrementAndGet();
        }
    }

    /**
     * Track coins earned
     */
    public void trackCoinsEarned(long amount) {
        hourlyCoinsEarned.addAndGet(amount);
    }

    /**
     * Track coins spent
     */
    public void trackCoinsSpent(long amount) {
        hourlyCoinsSpent.addAndGet(amount);
    }

    /**
     * Track quest completion
     */
    public void trackQuestCompleted(String userId) {
        hourlyQuestsCompleted.incrementAndGet();
        hourlyUniqueUsers.add(userId);
    }

    /**
     * Track theme usage
     */
    public void trackThemeUsed(String theme) {
        hourlyThemeUsage.computeIfAbsent(theme, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Schedule hourly stats logging
     */
    private void scheduleHourlyStatsLogging() {
        // Calculate delay until next hour
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextHour = now.plusHours(1).withMinute(0).withSecond(0).withNano(0);
        long initialDelay = java.time.Duration.between(now, nextHour).toMinutes();

        scheduler.scheduleAtFixedRate(this::logHourlyStats, initialDelay, 60, TimeUnit.MINUTES);
    }

    /**
     * Schedule daily summary generation (runs at midnight)
     */
    private void scheduleDailySummaryGeneration() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextMidnight = now.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        long initialDelay = java.time.Duration.between(now, nextMidnight).toHours();

        scheduler.scheduleAtFixedRate(this::generateDailySummary, initialDelay, 24, TimeUnit.HOURS);
    }

    /**
     * Log hourly statistics to CSV
     */
    /**
     * Log hourly statistics to CSV
     */
    private void logHourlyStats() {
        LocalDateTime now = LocalDateTime.now().minusHours(1); // Log for the previous hour
        String hourKey = now.format(HOUR_FORMAT);
        String monthKey = now.format(FILE_DATE_FORMAT);

        // Snapshot atomic counters and reset atomically
        long imagesGenerated = hourlyImagesGenerated.getAndSet(0);
        long commandsUsed = hourlyCommandsUsed.getAndSet(0);
        long activeUsers = hourlyActiveUsers.getAndSet(0);
        long newUsers = hourlyNewUsers.getAndSet(0);
        long coinsEarned = hourlyCoinsEarned.getAndSet(0);
        long coinsSpent = hourlyCoinsSpent.getAndSet(0);
        long questsCompleted = hourlyQuestsCompleted.getAndSet(0);
        long nsfwRequests = hourlyNsfwRequests.getAndSet(0);
        long sfwRequests = hourlySfwRequests.getAndSet(0);
        long premiumActivity = hourlyPremiumActivity.getAndSet(0);
        long regularActivity = hourlyRegularActivity.getAndSet(0);

        // Extract unique users thread-safely
        List<String> uniqueUsersSnapshot = new ArrayList<>();
        Iterator<String> it = hourlyUniqueUsers.iterator();
        while (it.hasNext()) {
            uniqueUsersSnapshot.add(it.next());
            it.remove();
        }

        // Snapshot maps thread-safely without removing keys (avoids detached AtomicLong race)
        Map<String, Long> sourceUsageSnapshot = new HashMap<>();
        for (String key : hourlySourceUsage.keySet()) {
            AtomicLong counter = hourlySourceUsage.get(key);
            if (counter != null) {
                long val = counter.getAndSet(0);
                if (val > 0) {
                    sourceUsageSnapshot.put(key, val);
                }
            }
        }

        Map<String, Long> commandUsageSnapshot = new HashMap<>();
        for (String key : hourlyCommandUsage.keySet()) {
            AtomicLong counter = hourlyCommandUsage.get(key);
            if (counter != null) {
                long val = counter.getAndSet(0);
                if (val > 0) {
                    commandUsageSnapshot.put(key, val);
                }
            }
        }

        Map<String, Long> themeUsageSnapshot = new HashMap<>();
        for (String key : hourlyThemeUsage.keySet()) {
            AtomicLong counter = hourlyThemeUsage.get(key);
            if (counter != null) {
                long val = counter.getAndSet(0);
                if (val > 0) {
                    themeUsageSnapshot.put(key, val);
                }
            }
        }

        try {
            // Log general stats
            logGeneralHourlyStats(hourKey, monthKey, imagesGenerated, commandsUsed, activeUsers,
                    uniqueUsersSnapshot.size(), newUsers, coinsEarned, coinsSpent, questsCompleted,
                    nsfwRequests, sfwRequests, premiumActivity, regularActivity);

            // Log source usage stats
            logSourceUsageStats(hourKey, monthKey, sourceUsageSnapshot);

            // Log command usage stats
            logCommandUsageStats(hourKey, monthKey, commandUsageSnapshot);

            // Log theme usage stats
            logThemeUsageStats(hourKey, monthKey, themeUsageSnapshot);

            logger.info("Logged hourly stats for: {}", hourKey);

        } catch (Exception e) {
            logger.error("Failed to log hourly stats", e);
        }
    }

    /**
     * Log general hourly statistics
     */
    private void logGeneralHourlyStats(String hourKey, String monthKey, long imagesGenerated, long commandsUsed,
                                       long activeUsers, int uniqueUsersCount, long newUsers, long coinsEarned,
                                       long coinsSpent, long questsCompleted, long nsfwRequests, long sfwRequests,
                                       long premiumActivity, long regularActivity) throws IOException {
        Path filePath = Paths.get(STATS_DIR, "general_stats_" + monthKey + ".csv");
        boolean fileExists = Files.exists(filePath);

        try (CSVWriter writer = new CSVWriter(new FileWriter(filePath.toFile(), true))) {
            // Write header if file is new
            if (!fileExists) {
                String[] header = {
                        "hour", "images_generated", "commands_used", "active_users", "unique_users",
                        "new_users", "coins_earned", "coins_spent", "quests_completed",
                        "nsfw_requests", "sfw_requests", "premium_activity", "regular_activity",
                        "total_users_in_db", "total_images_in_db"
                };
                writer.writeNext(header);
            }

            // Get database totals
            long totalUsers = 0; // TODO accountRepository.count();
            long totalImages = 0; // TODO aggregate;

            String[] data = {
                    hourKey,
                    String.valueOf(imagesGenerated),
                    String.valueOf(commandsUsed),
                    String.valueOf(activeUsers),
                    String.valueOf(uniqueUsersCount),
                    String.valueOf(newUsers),
                    String.valueOf(coinsEarned),
                    String.valueOf(coinsSpent),
                    String.valueOf(questsCompleted),
                    String.valueOf(nsfwRequests),
                    String.valueOf(sfwRequests),
                    String.valueOf(premiumActivity),
                    String.valueOf(regularActivity),
                    String.valueOf(totalUsers),
                    String.valueOf(totalImages)
            };

            writer.writeNext(data);
        }
    }

    /**
     * Log source usage statistics
     */
    private void logSourceUsageStats(String hourKey, String monthKey, Map<String, Long> sourceUsage) throws IOException {
        if (sourceUsage.isEmpty())
            return;

        Path filePath = Paths.get(STATS_DIR, "source_usage_" + monthKey + ".csv");
        boolean fileExists = Files.exists(filePath);

        try (CSVWriter writer = new CSVWriter(new FileWriter(filePath.toFile(), true))) {
            // Write header if file is new
            if (!fileExists) {
                String[] header = { "hour", "source", "usage_count" };
                writer.writeNext(header);
            }

            for (Map.Entry<String, Long> entry : sourceUsage.entrySet()) {
                String[] data = {
                        hourKey,
                        entry.getKey(),
                        String.valueOf(entry.getValue())
                };
                writer.writeNext(data);
            }
        }
    }

    /**
     * Log command usage statistics
     */
    private void logCommandUsageStats(String hourKey, String monthKey, Map<String, Long> commandUsage) throws IOException {
        if (commandUsage.isEmpty())
            return;

        Path filePath = Paths.get(STATS_DIR, "command_usage_" + monthKey + ".csv");
        boolean fileExists = Files.exists(filePath);

        try (CSVWriter writer = new CSVWriter(new FileWriter(filePath.toFile(), true))) {
            // Write header if file is new
            if (!fileExists) {
                String[] header = { "hour", "command", "usage_count" };
                writer.writeNext(header);
            }

            for (Map.Entry<String, Long> entry : commandUsage.entrySet()) {
                String[] data = {
                        hourKey,
                        entry.getKey(),
                        String.valueOf(entry.getValue())
                };
                writer.writeNext(data);
            }
        }
    }

    /**
     * Log theme usage statistics
     */
    private void logThemeUsageStats(String hourKey, String monthKey, Map<String, Long> themeUsage) throws IOException {
        if (themeUsage.isEmpty())
            return;

        Path filePath = Paths.get(STATS_DIR, "theme_usage_" + monthKey + ".csv");
        boolean fileExists = Files.exists(filePath);

        try (CSVWriter writer = new CSVWriter(new FileWriter(filePath.toFile(), true))) {
            // Write header if file is new
            if (!fileExists) {
                String[] header = { "hour", "theme", "usage_count" };
                writer.writeNext(header);
            }

            for (Map.Entry<String, Long> entry : themeUsage.entrySet()) {
                String[] data = {
                        hourKey,
                        entry.getKey(),
                        String.valueOf(entry.getValue())
                };
                writer.writeNext(data);
            }
        }
    }

    /**
     * Generate daily summary statistics
     */
    private void generateDailySummary() {
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        String dateKey = yesterday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        try {
            generateUserActivitySummary(dateKey);
            generateSourcePopularitySummary(dateKey);
            generateCommandPopularitySummary(dateKey);

            logger.info("Generated daily summary for: {}", dateKey);

        } catch (Exception e) {
            logger.error("Failed to generate daily summary", e);
        }
    }

    /**
     * Generate user activity summary
     */
    private void generateUserActivitySummary(String dateKey) throws IOException {
        Path filePath = Paths.get(STATS_DIR, "daily_user_summary.csv");
        boolean fileExists = Files.exists(filePath);

        try (CSVWriter writer = new CSVWriter(new FileWriter(filePath.toFile(), true))) {
            if (!fileExists) {
                String[] header = {
                        "date", "total_users", "active_users", "new_users", "premium_users",
                        "avg_images_per_user", "total_coins_in_circulation"
                };
                writer.writeNext(header);
            }

            // Calculate summary stats from database
            long totalUsers = 0; // TODO accountRepository.count();
            long totalImages = 0; // TODO aggregate;

            // These would need to be calculated from the database
            // For now, using placeholder values
            String[] data = {
                    dateKey,
                    String.valueOf(totalUsers),
                    "0", // Would need to query active users from yesterday
                    "0", // Would need to query new users from yesterday
                    "0", // Would need to query premium users
                    totalUsers > 0 ? String.format("%.2f", (double) totalImages / totalUsers) : "0",
                    "0" // Would need to calculate total coins in circulation
            };

            writer.writeNext(data);
        }
    }

    /**
     * Generate source popularity summary
     */
    private void generateSourcePopularitySummary(String dateKey) throws IOException {
        // This would aggregate hourly source usage data for the day
        // Implementation would read from hourly CSV files and summarize
        logger.info("Source popularity summary generation not yet implemented");
    }

    /**
     * Generate command popularity summary
     */
    private void generateCommandPopularitySummary(String dateKey) throws IOException {
        // This would aggregate hourly command usage data for the day
        // Implementation would read from hourly CSV files and summarize
        logger.info("Command popularity summary generation not yet implemented");
    }

    /**
     * Shutdown the service
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get current hourly stats (for debugging/monitoring)
     */
    public Map<String, Object> getCurrentHourlyStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("images_generated", hourlyImagesGenerated.get());
        stats.put("commands_used", hourlyCommandsUsed.get());
        stats.put("active_users", hourlyActiveUsers.get());
        stats.put("unique_users", hourlyUniqueUsers.size());
        stats.put("new_users", hourlyNewUsers.get());
        stats.put("coins_earned", hourlyCoinsEarned.get());
        stats.put("coins_spent", hourlyCoinsSpent.get());
        stats.put("quests_completed", hourlyQuestsCompleted.get());
        stats.put("nsfw_requests", hourlyNsfwRequests.get());
        stats.put("sfw_requests", hourlySfwRequests.get());
        stats.put("premium_activity", hourlyPremiumActivity.get());
        stats.put("regular_activity", hourlyRegularActivity.get());
        stats.put("source_usage", new HashMap<>(hourlySourceUsage));
        stats.put("command_usage", new HashMap<>(hourlyCommandUsage));
        stats.put("theme_usage", new HashMap<>(hourlyThemeUsage));

        return stats;
    }
}