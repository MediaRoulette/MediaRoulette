package me.hash.mediaroulette.utils.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.AppenderBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.hash.mediaroulette.Main;
import okhttp3.*;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Logback appender that sends log messages to a Discord webhook.
 * Features:
 * - Full stacktrace support with file attachments for long traces
 * - Rate limiting with exponential backoff
 * - Rich embed formatting
 * - Async message sending
 * - Vault integration for webhook URL
 * - Startup/shutdown notifications
 */
public class DiscordWebhookAppender extends AppenderBase<ILoggingEvent> {

    private static final int MAX_EMBED_DESCRIPTION = 4000;
    private static final int MAX_FIELD_VALUE = 1000;
    private static final int STACKTRACE_FILE_THRESHOLD = 800; // Attach file if stacktrace exceeds this
    private static final int MAX_RETRIES = 3;
    private static final long BASE_RETRY_DELAY_MS = 1000;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final MediaType TEXT = MediaType.get("text/plain; charset=utf-8");
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    // Rate limiting: max messages per time window
    private static final int RATE_LIMIT_MAX = 5;
    private static final long RATE_LIMIT_WINDOW_MS = 5000;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final BlockingQueue<LogEntry> messageQueue;
    private final AtomicLong lastRateLimitTime = new AtomicLong(0);
    private final List<Long> recentMessageTimes = Collections.synchronizedList(new LinkedList<>());
    private final AtomicBoolean webhookResolved = new AtomicBoolean(false);
    private final AtomicBoolean startupMessageSent = new AtomicBoolean(false);

    private String webhookUrl = null;
    private String configuredWebhookUrl = null;
    private String username = "MediaRoulette Logger";
    private String avatarUrl = null;
    private boolean includeStackTrace = true;
    private int maxStackTraceDepth = 50;
    private volatile boolean running = false;

    public DiscordWebhookAppender() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
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
        this.messageQueue = new LinkedBlockingQueue<>(500);
    }

    /**
     * Resolves the webhook URL from Vault/Main.getEnv() or falls back to configured URL.
     * This is called lazily to ensure Vault is initialized.
     */
    private void resolveWebhookUrl() {
        if (webhookResolved.get()) {
            return;
        }

        try {
            // Try to get from Main.getEnv() which checks Vault first
            String vaultUrl = Main.getEnv("ERROR_WEBHOOK");
            if (vaultUrl != null && !vaultUrl.trim().isEmpty()) {
                webhookUrl = vaultUrl;
                addInfo("Discord webhook URL resolved from Vault/environment");
            } else if (configuredWebhookUrl != null && !configuredWebhookUrl.trim().isEmpty()
                    && !configuredWebhookUrl.equals("${ERROR_WEBHOOK}")) {
                webhookUrl = configuredWebhookUrl;
                addInfo("Discord webhook URL resolved from logback.xml configuration");
            }
        } catch (Exception e) {
            // Main might not be initialized yet, use configured URL
            if (configuredWebhookUrl != null && !configuredWebhookUrl.trim().isEmpty()
                    && !configuredWebhookUrl.equals("${ERROR_WEBHOOK}")) {
                webhookUrl = configuredWebhookUrl;
            }
        }

        webhookResolved.set(true);

        // Validate URL if we have one
        if (webhookUrl != null && !webhookUrl.trim().isEmpty()) {
            if (!isValidWebhookUrl(webhookUrl)) {
                addError("Invalid Discord webhook URL format: " + maskWebhookUrl(webhookUrl));
                webhookUrl = null;
            } else {
                addInfo("Discord webhook ready with URL: " + maskWebhookUrl(webhookUrl));
                // Send startup notification
                sendStartupNotification();
            }
        }
    }

    private boolean isValidWebhookUrl(String url) {
        return url.startsWith("https://discord.com/api/webhooks/") ||
                url.startsWith("https://discordapp.com/api/webhooks/") ||
                url.startsWith("https://canary.discord.com/api/webhooks/") ||
                url.startsWith("https://ptb.discord.com/api/webhooks/");
    }

    /**
     * Sends a startup notification to Discord
     */
    private void sendStartupNotification() {
        if (!startupMessageSent.compareAndSet(false, true)) {
            return;
        }

        try {
            String hostname = "Unknown";
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (Exception ignored) {}

            RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
            long uptimeMs = runtime.getUptime();
            String javaVersion = System.getProperty("java.version", "Unknown");
            String osName = System.getProperty("os.name", "Unknown");

            Map<String, Object> payload = new HashMap<>();
            payload.put("username", username);
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                payload.put("avatar_url", avatarUrl);
            }

            Map<String, Object> embed = new HashMap<>();
            embed.put("title", "🚀 Application Started");
            embed.put("description", "MediaRoulette logging service is now online and monitoring for errors.");
            embed.put("color", 0x00FF00); // Green

            List<Map<String, Object>> fields = new ArrayList<>();
            fields.add(createField("Host", hostname, true));
            fields.add(createField("Java", javaVersion, true));
            fields.add(createField("OS", osName, true));
            fields.add(createField("Startup Time", formatUptime(uptimeMs), true));
            fields.add(createField("Log Level", "WARN+", true));
            fields.add(createField("Stacktrace", includeStackTrace ? "Enabled" : "Disabled", true));
            embed.put("fields", fields);

            embed.put("timestamp", Instant.now().toString());
            Map<String, String> footer = new HashMap<>();
            footer.put("text", "Discord Webhook Logger v2.0");
            embed.put("footer", footer);

            payload.put("embeds", List.of(embed));

            String jsonPayload = objectMapper.writeValueAsString(payload);
            RequestBody body = RequestBody.create(jsonPayload, JSON);
            Request request = new Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .addHeader("User-Agent", "LogbackDiscordAppender/2.0")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    addInfo("Startup notification sent to Discord");
                } else {
                    addWarn("Failed to send startup notification: " + response.code());
                }
            }
        } catch (Exception e) {
            addError("Failed to send startup notification", e);
        }
    }

    /**
     * Sends a shutdown notification to Discord
     */
    private void sendShutdownNotification() {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            return;
        }

        try {
            RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
            long uptimeMs = runtime.getUptime();

            Map<String, Object> payload = new HashMap<>();
            payload.put("username", username);
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                payload.put("avatar_url", avatarUrl);
            }

            Map<String, Object> embed = new HashMap<>();
            embed.put("title", "🛑 Application Shutting Down");
            embed.put("description", "MediaRoulette is shutting down gracefully.");
            embed.put("color", 0xFFA500); // Orange

            List<Map<String, Object>> fields = new ArrayList<>();
            fields.add(createField("Uptime", formatUptime(uptimeMs), true));
            fields.add(createField("Shutdown", "Graceful", true));
            embed.put("fields", fields);

            embed.put("timestamp", Instant.now().toString());
            payload.put("embeds", List.of(embed));

            String jsonPayload = objectMapper.writeValueAsString(payload);
            RequestBody body = RequestBody.create(jsonPayload, JSON);
            Request request = new Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .addHeader("User-Agent", "LogbackDiscordAppender/2.0")
                    .build();

            // Use synchronous call for shutdown
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    addInfo("Shutdown notification sent to Discord");
                }
            }
        } catch (Exception e) {
            // Ignore errors during shutdown
        }
    }

    private String formatUptime(long uptimeMs) {
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    @Override
    public void start() {
        configuredWebhookUrl = webhookUrl;

        running = true;
        startMessageProcessor();
        super.start();
        addInfo("Discord webhook appender started - URL will be resolved from Vault/environment on first message");
    }

    @Override
    public void stop() {
        running = false;

        // Send shutdown notification before stopping
        sendShutdownNotification();

        super.stop();

        // Shutdown executor gracefully
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Cleanup OkHttp resources
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        addInfo("Discord webhook appender stopped");
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!isStarted()) {
            return;
        }

        // Lazily resolve webhook URL from Vault/Main.getEnv() on first message
        if (!webhookResolved.get()) {
            resolveWebhookUrl();
        }

        // Skip if no valid webhook URL
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            return;
        }

        try {
            String stackTrace = includeStackTrace ? extractStackTrace(event) : null;

            LogEntry entry = new LogEntry(
                    event.getLevel().toString(),
                    event.getLoggerName(),
                    event.getFormattedMessage(),
                    event.getThreadName(),
                    event.getTimeStamp(),
                    extractCallerData(event),
                    stackTrace,
                    stackTrace != null && stackTrace.length() > STACKTRACE_FILE_THRESHOLD
            );

            // Try to add to queue, drop if full (prevents memory issues)
            if (!messageQueue.offer(entry)) {
                addWarn("Discord webhook message queue full, dropping message");
            }
        } catch (Exception e) {
            addError("Failed to queue log message for Discord webhook", e);
        }
    }

    private void startMessageProcessor() {
        executor.submit(() -> {
            while (running || !messageQueue.isEmpty()) {
                try {
                    LogEntry entry = messageQueue.poll(1, TimeUnit.SECONDS);
                    if (entry != null) {
                        // Check rate limiting
                        if (isRateLimited()) {
                            Thread.sleep(RATE_LIMIT_WINDOW_MS);
                            continue;
                        }

                        sendToDiscordWithRetry(entry);
                        recordMessageSent();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    addError("Error processing Discord webhook message", e);
                }
            }
        });
    }

    private boolean isRateLimited() {
        long now = System.currentTimeMillis();

        long rateLimitEnd = lastRateLimitTime.get();
        if (now < rateLimitEnd) {
            return true;
        }

        synchronized (recentMessageTimes) {
            recentMessageTimes.removeIf(time -> now - time > RATE_LIMIT_WINDOW_MS);
            return recentMessageTimes.size() >= RATE_LIMIT_MAX;
        }
    }

    private void recordMessageSent() {
        synchronized (recentMessageTimes) {
            recentMessageTimes.add(System.currentTimeMillis());
        }
    }

    private void sendToDiscordWithRetry(LogEntry entry) {
        int attempt = 0;
        while (attempt < MAX_RETRIES) {
            try {
                int responseCode;
                if (entry.attachStackTraceAsFile && entry.stackTrace != null) {
                    responseCode = sendToDiscordWithFile(entry);
                } else {
                    responseCode = sendToDiscord(entry);
                }

                if (responseCode == 200 || responseCode == 204) {
                    return;
                } else if (responseCode == 429) {
                    long retryAfter = BASE_RETRY_DELAY_MS * (long) Math.pow(2, attempt);
                    lastRateLimitTime.set(System.currentTimeMillis() + retryAfter);
                    addWarn("Discord rate limited, waiting " + retryAfter + "ms");
                    Thread.sleep(retryAfter);
                } else if (responseCode >= 500) {
                    long delay = BASE_RETRY_DELAY_MS * (long) Math.pow(2, attempt);
                    Thread.sleep(delay);
                } else {
                    addError("Discord webhook returned error: " + responseCode);
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                addError("Failed to send message to Discord webhook", e);
            }
            attempt++;
        }
    }

    private int sendToDiscord(LogEntry entry) throws IOException {
        Map<String, Object> payload = buildPayload(entry, false);
        String jsonPayload = objectMapper.writeValueAsString(payload);

        RequestBody body = RequestBody.create(jsonPayload, JSON);
        Request request = new Request.Builder()
                .url(webhookUrl)
                .post(body)
                .addHeader("User-Agent", "LogbackDiscordAppender/2.0")
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            int code = response.code();
            if (!response.isSuccessful() && code != 429) {
                String responseBody = response.body() != null ? response.body().string() : "No response body";
                addError("Discord webhook error: " + code + " - " + responseBody);
            }
            return code;
        }
    }

    /**
     * Sends a message to Discord with a file attachment for long stacktraces
     */
    private int sendToDiscordWithFile(LogEntry entry) throws IOException {
        Map<String, Object> payload = buildPayload(entry, true);
        String jsonPayload = objectMapper.writeValueAsString(payload);

        // Create filename with timestamp
        String filename = "error_" + LocalDateTime.now().format(FILE_DATE_FORMAT) + ".txt";

        // Build error dump content
        StringBuilder errorDump = new StringBuilder();
        errorDump.append("=".repeat(80)).append("\n");
        errorDump.append("ERROR DUMP - MediaRoulette\n");
        errorDump.append("=".repeat(80)).append("\n\n");

        errorDump.append("Timestamp: ").append(Instant.ofEpochMilli(entry.timestamp)).append("\n");
        errorDump.append("Level: ").append(entry.level).append("\n");
        errorDump.append("Logger: ").append(entry.loggerName).append("\n");
        errorDump.append("Thread: ").append(entry.threadName).append("\n");
        if (entry.callerData != null) {
            errorDump.append("Location: ").append(entry.callerData).append("\n");
        }
        errorDump.append("\n");

        errorDump.append("-".repeat(80)).append("\n");
        errorDump.append("MESSAGE\n");
        errorDump.append("-".repeat(80)).append("\n");
        errorDump.append(entry.message).append("\n\n");

        if (entry.stackTrace != null) {
            errorDump.append("-".repeat(80)).append("\n");
            errorDump.append("FULL STACKTRACE\n");
            errorDump.append("-".repeat(80)).append("\n");
            errorDump.append(entry.stackTrace).append("\n");
        }

        // Build multipart request
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("payload_json", jsonPayload)
                .addFormDataPart("file", filename,
                        RequestBody.create(errorDump.toString(), TEXT))
                .build();

        Request request = new Request.Builder()
                .url(webhookUrl)
                .post(requestBody)
                .addHeader("User-Agent", "LogbackDiscordAppender/2.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            int code = response.code();
            if (!response.isSuccessful() && code != 429) {
                String responseBody = response.body() != null ? response.body().string() : "No response body";
                addError("Discord webhook error: " + code + " - " + responseBody);
            }
            return code;
        }
    }

    private Map<String, Object> buildPayload(LogEntry entry, boolean stackTraceInFile) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", username);
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            payload.put("avatar_url", avatarUrl);
        }

        Map<String, Object> embed = new HashMap<>();

        String shortLoggerName = getShortLoggerName(entry.loggerName);
        String emoji = getLevelEmoji(entry.level);
        embed.put("title", emoji + " [" + entry.level + "] " + shortLoggerName);

        String description = entry.message;
        if (description.length() > MAX_EMBED_DESCRIPTION) {
            description = description.substring(0, MAX_EMBED_DESCRIPTION - 3) + "...";
        }
        embed.put("description", description);
        embed.put("color", getEmbedColor(entry.level));

        List<Map<String, Object>> fields = new ArrayList<>();

        fields.add(createField("Thread", entry.threadName, true));

        if (entry.callerData != null && !entry.callerData.isEmpty()) {
            fields.add(createField("Location", "`" + entry.callerData + "`", true));
        }

        if (!shortLoggerName.equals(entry.loggerName)) {
            fields.add(createField("Logger", "`" + entry.loggerName + "`", false));
        }

        // Handle stacktrace
        if (entry.stackTrace != null && !entry.stackTrace.isEmpty()) {
            if (stackTraceInFile) {
                // Stacktrace will be in attached file
                fields.add(createField("📎 Stacktrace", "Full stacktrace attached as `.txt` file", false));
            } else {
                // Include truncated stacktrace in embed
                String stackTraceFormatted = "```\n" + entry.stackTrace + "\n```";
                if (stackTraceFormatted.length() > MAX_FIELD_VALUE) {
                    String truncated = entry.stackTrace.substring(0, MAX_FIELD_VALUE - 20);
                    stackTraceFormatted = "```\n" + truncated + "\n...```";
                }
                fields.add(createField("Stacktrace", stackTraceFormatted, false));
            }
        }

        embed.put("fields", fields);
        embed.put("timestamp", Instant.ofEpochMilli(entry.timestamp).toString());

        Map<String, String> footer = new HashMap<>();
        footer.put("text", "MediaRoulette Error Logger");
        embed.put("footer", footer);

        payload.put("embeds", List.of(embed));

        return payload;
    }

    private Map<String, Object> createField(String name, String value, boolean inline) {
        Map<String, Object> field = new HashMap<>();
        field.put("name", name);
        field.put("value", value);
        field.put("inline", inline);
        return field;
    }

    private String extractCallerData(ILoggingEvent event) {
        StackTraceElement[] callerData = event.getCallerData();
        if (callerData != null && callerData.length > 0) {
            StackTraceElement caller = callerData[0];
            return caller.getClassName() + "." + caller.getMethodName() + ":" + caller.getLineNumber();
        }
        return null;
    }

    private String extractStackTrace(ILoggingEvent event) {
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        appendThrowable(sb, throwableProxy, "", 0);
        return sb.toString();
    }

    private void appendThrowable(StringBuilder sb, IThrowableProxy throwable, String prefix, int depth) {
        if (throwable == null || depth > 5) {
            return;
        }

        sb.append(prefix).append(throwable.getClassName());
        if (throwable.getMessage() != null) {
            sb.append(": ").append(throwable.getMessage());
        }
        sb.append("\n");

        StackTraceElementProxy[] stackTrace = throwable.getStackTraceElementProxyArray();
        if (stackTrace != null) {
            int linesToShow = Math.min(stackTrace.length, maxStackTraceDepth);
            for (int i = 0; i < linesToShow; i++) {
                sb.append(prefix).append("    at ").append(stackTrace[i].getStackTraceElement()).append("\n");
            }
            if (stackTrace.length > linesToShow) {
                sb.append(prefix).append("    ... ").append(stackTrace.length - linesToShow).append(" more\n");
            }
        }

        IThrowableProxy cause = throwable.getCause();
        if (cause != null) {
            sb.append(prefix).append("Caused by: ");
            appendThrowable(sb, cause, prefix, depth + 1);
        }

        // Handle suppressed exceptions
        IThrowableProxy[] suppressed = throwable.getSuppressed();
        if (suppressed != null) {
            for (IThrowableProxy sup : suppressed) {
                sb.append(prefix).append("Suppressed: ");
                appendThrowable(sb, sup, prefix + "    ", depth + 1);
            }
        }
    }

    private String getShortLoggerName(String loggerName) {
        if (loggerName == null) return "Unknown";
        int lastDot = loggerName.lastIndexOf('.');
        return lastDot >= 0 ? loggerName.substring(lastDot + 1) : loggerName;
    }

    private String getLevelEmoji(String level) {
        switch (level.toUpperCase()) {
            case "ERROR": return "🚨";
            case "WARN": return "⚠️";
            case "INFO": return "ℹ️";
            case "DEBUG": return "🔍";
            case "TRACE": return "📝";
            default: return "📋";
        }
    }

    private int getEmbedColor(String level) {
        switch (level.toUpperCase()) {
            case "ERROR": return 0xFF0000;
            case "WARN": return 0xFFA500;
            case "INFO": return 0x0099FF;
            case "DEBUG": return 0x808080;
            case "TRACE": return 0xC0C0C0;
            default: return 0x000000;
        }
    }

    private String maskWebhookUrl(String url) {
        if (url == null || url.length() < 20) return url;
        return url.substring(0, url.lastIndexOf('/') + 1) + "***";
    }

    // Configuration setters/getters

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

    public void setMaxStackTraceDepth(int maxStackTraceDepth) {
        this.maxStackTraceDepth = maxStackTraceDepth;
    }

    public int getMaxStackTraceDepth() {
        return maxStackTraceDepth;
    }

    /**
     * Container for log entry data
     */
    private static class LogEntry {
        final String level;
        final String loggerName;
        final String message;
        final String threadName;
        final long timestamp;
        final String callerData;
        final String stackTrace;
        final boolean attachStackTraceAsFile;

        LogEntry(String level, String loggerName, String message, String threadName,
                 long timestamp, String callerData, String stackTrace, boolean attachStackTraceAsFile) {
            this.level = level;
            this.loggerName = loggerName;
            this.message = message;
            this.threadName = threadName;
            this.timestamp = timestamp;
            this.callerData = callerData;
            this.stackTrace = stackTrace;
            this.attachStackTraceAsFile = attachStackTraceAsFile;
        }
    }
}