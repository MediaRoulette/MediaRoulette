package me.hash.mediaroulette.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.hash.mediaroulette.Main;
import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Utility class for reporting errors, warnings, and info messages to a Discord webhook
 */
public class ErrorReporter {
    private static final Logger logger = LoggerFactory.getLogger(ErrorReporter.class);
    private static WebhookClient webhookClient;
    private static boolean initialized = false;

    private static void initialize() {
        if (!initialized) {
            String webhookUrl = Main.getEnv("ERROR_WEBHOOK");
            if (webhookUrl != null && !webhookUrl.isEmpty()) {
                webhookClient = WebhookClient.withUrl(webhookUrl);
            }
            initialized = true;
        }
    }

    /**
     * Report a failed subreddit error
     */
    public static void reportFailedSubreddit(String subreddit, String error, String userId) {
        reportError("Failed Subreddit",
                String.format("Subreddit '%s' failed validation or access", subreddit),
                error,
                "reddit",
                userId);
    }

    /**
     * Report a failed 4chan board error
     */
    public static void reportFailed4ChanBoard(String board, String error, String userId) {
        reportError("Failed 4Chan Board",
                String.format("4Chan board '%s' failed validation or access", board),
                error,
                "4chan",
                userId);
    }

    /**
     * Report a general provider error
     */
    public static void reportProviderError(String provider, String operation, String error, String userId) {
        reportError("Provider Error",
                String.format("%s provider failed during %s", provider, operation),
                error,
                provider,
                userId);
    }

    /**
     * Report an informational message
     */
    public static void reportInfo(String title, String description, String details, String source, String userId) {
        reportMessage("INFO", title, description, details, source, userId, 0x00FF00, "ℹ️"); // Green color
    }

    /**
     * Report a warning message
     */
    public static void reportWarning(String title, String description, String details, String source, String userId) {
        reportMessage("WARNING", title, description, details, source, userId, 0xFFFF00, "⚠️"); // Yellow color
    }

    /**
     * Generic error reporting method
     */
    public static void reportError(String title, String description, String errorDetails, String source, String userId) {
        reportMessage("ERROR", title, description, errorDetails, source, userId, 0xFF0000, "🚨"); // Red color
    }

    /**
     * Generic message reporting method
     */
    private static void reportMessage(String logLevel, String title, String description, String details, String source, String userId, int color, String emoji) {
        initialize();

        if (webhookClient == null) {
            // Fallback to console logging if webhook is not configured
            switch (logLevel) {
                case "ERROR":
                    logger.error("[{}] {} - {}: {} (User: {}, Source: {})",
                            logLevel, title, description, details, userId != null ? userId : "unknown", source);
                    break;
                case "WARNING":
                    logger.warn("[{}] {} - {}: {} (User: {}, Source: {})",
                            logLevel, title, description, details, userId != null ? userId : "unknown", source);
                    break;
                case "INFO":
                    logger.info("[{}] {} - {}: {} (User: {}, Source: {})",
                            logLevel, title, description, details, userId != null ? userId : "unknown", source);
                    break;
            }
            return;
        }

        try {
            WebhookEmbedBuilder embedBuilder = new WebhookEmbedBuilder()
                    .setTitle(new WebhookEmbed.EmbedTitle(emoji + " " + title, null))
                    .setDescription(description)
                    .addField(new WebhookEmbed.EmbedField(true, "Details",
                            details.length() > 1000 ? details.substring(0, 1000) + "..." : details))
                    .addField(new WebhookEmbed.EmbedField(true, "Source", source))
                    .addField(new WebhookEmbed.EmbedField(true, "User ID", userId != null ? userId : "Unknown"))
                    .setTimestamp(Instant.now())
                    .setColor(color);

            CompletableFuture<Void> future = webhookClient.send(embedBuilder.build())
                    .thenAccept(message -> {
                        // Success - no action needed
                    })
                    .exceptionally(throwable -> {
                        logger.error("Failed to send {} report to webhook: {}", logLevel.toLowerCase(), throwable.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            logger.error("Error while trying to report {}: {}", logLevel.toLowerCase(), e.getMessage());
        }
    }

    /**
     * Cleanup method to close webhook client
     */
    public static void cleanup() {
        if (webhookClient != null) {
            webhookClient.close();
        }
    }
}