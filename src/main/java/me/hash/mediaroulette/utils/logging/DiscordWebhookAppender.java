package me.hash.mediaroulette.utils.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.hash.mediaroulette.Main;
import okhttp3.*;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class DiscordWebhookAppender extends AppenderBase<ILoggingEvent> {

    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    private String webhookUrl = null;
    private String username = "Application Logger";
    private String avatarUrl = null;
    private boolean includeStackTrace = true;
    private int queueSize = 100;

    public DiscordWebhookAppender() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        this.objectMapper = new ObjectMapper();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "discord-webhook-sender");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    protected void append(ILoggingEvent event) {
        // Skip if webhook URL is not configured
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            return;
        }
        
        try {
            String message = formatMessage(event);
            sendToDiscordAsync(message, getEmbedColor(event.getLevel().toString()));
        } catch (Exception e) {
            addError("Failed to send log to Discord webhook", e);
        }
    }

    private String formatMessage(ILoggingEvent event) {
        StringBuilder sb = new StringBuilder();

        // Basic message
        String basicMessage = String.format("[%s] %s: %s",
                event.getLevel(),
                event.getLoggerName(),
                event.getFormattedMessage());

        if (basicMessage.length() > MAX_MESSAGE_LENGTH) {
            basicMessage = basicMessage.substring(0, MAX_MESSAGE_LENGTH - 3) + "...";
        }

        return basicMessage;
    }

    private void sendToDiscordAsync(String message, int color) {
        CompletableFuture.runAsync(() -> {
            try {
                sendToDiscord(message, color);
            } catch (Exception e) {
                addError("Failed to send message to Discord", e);
            }
        }, executor);
    }

    private void sendToDiscord(String message, int color) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", username);
        if (avatarUrl != null) {
            payload.put("avatar_url", avatarUrl);
        }

        // Create embed for better formatting
        Map<String, Object> embed = new HashMap<>();
        embed.put("description", message);
        embed.put("color", color);
        embed.put("timestamp", Instant.now().toString());

        Map<String, String> footer = new HashMap<>();
        footer.put("text", "Application Logs");
        embed.put("footer", footer);

        payload.put("embeds", List.of(embed));

        String jsonPayload = objectMapper.writeValueAsString(payload);

        RequestBody body = RequestBody.create(jsonPayload, JSON);
        Request request = new Request.Builder()
                .url(webhookUrl)
                .post(body)
                .addHeader("User-Agent", "LogbackDiscordAppender/1.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "No response body";
                addError("Discord webhook returned error: " + response.code() + " - " + responseBody);
            }
        }
    }

    private int getEmbedColor(String level) {
        switch (level.toUpperCase()) {
            case "ERROR":
                return 0xFF0000; // Red
            case "WARN":
                return 0xFFA500; // Orange
            case "INFO":
                return 0x0099FF; // Blue
            case "DEBUG":
                return 0x808080; // Gray
            case "TRACE":
                return 0xC0C0C0; // Light Gray
            default:
                return 0x000000; // Black
        }
    }

    @Override
    public void start() {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            addWarn("Discord webhook URL is not configured - appender will be disabled");
            return;
        }

        if (!webhookUrl.startsWith("https://discord.com/api/webhooks/") &&
                !webhookUrl.startsWith("https://discordapp.com/api/webhooks/")) {
            addError("Invalid Discord webhook URL format");
            return;
        }

        super.start();
        addInfo("Discord webhook appender started with URL: " + maskWebhookUrl(webhookUrl));
    }

    @Override
    public void stop() {
        super.stop();
        if (executor instanceof ExecutorService) {
            ((ExecutorService) executor).shutdown();
        }
        // Cleanup OkHttp resources
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        addInfo("Discord webhook appender stopped");
    }

    private String maskWebhookUrl(String url) {
        if (url == null || url.length() < 20) return url;
        return url.substring(0, url.lastIndexOf('/') + 1) + "***";
    }

    // Getters and Setters for configuration

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setIncludeStackTrace(boolean includeStackTrace) {
        this.includeStackTrace = includeStackTrace;
    }

    public boolean isIncludeStackTrace() {
        return includeStackTrace;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public int getQueueSize() {
        return queueSize;
    }
}