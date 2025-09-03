package me.hash.mediaroulette.bot.commands.bot;

import java.awt.Color;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.bot.commands.CommandHandler;
import me.hash.mediaroulette.model.User;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import static me.hash.mediaroulette.bot.MediaContainerManager.PREMIUM_COLOR;
import static me.hash.mediaroulette.bot.MediaContainerManager.PRIMARY_COLOR;

public class InfoCommand extends ListenerAdapter implements CommandHandler {
    private static final Color ACCENT_COLOR = new Color(114, 137, 218);
    private static final MemoryMXBean MEMORY_BEAN = ManagementFactory.getMemoryMXBean();
    private static final OperatingSystemMXBean OS_BEAN = ManagementFactory.getOperatingSystemMXBean();
    private static final Runtime RUNTIME = Runtime.getRuntime();

    @Override
    public CommandData getCommandData() {
        return Commands.slash("info", "📊 Get detailed bot or user information")
                .addSubcommands(
                        new SubcommandData("bot", "🤖 Get comprehensive bot statistics and information"),
                        new SubcommandData("me", "👤 View your personal profile and usage statistics")
                )
                .setIntegrationTypes(IntegrationType.ALL)
                .setContexts(InteractionContextType.ALL);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("info")) return;

        event.deferReply().queue();
        Bot.executor.execute(() -> {
            if (!checkCooldown(event)) return;

            Container container = "bot".equals(event.getSubcommandName())
                    ? getBotInfoContainer()
                    : getUserInfoContainer(event.getUser());
            event.getHook().sendMessageComponents(container).useComponentsV2().queue();
        });
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!isValidButtonId(id)) return;

        event.deferEdit().queue();
        Bot.executor.execute(() -> {
            Container container = switch (id) {
                case "refresh_stats" -> getBotInfoContainer();
                case "refresh_profile" -> getUserInfoContainer(event.getUser());
                case "view_favorites" -> createFavoritesContainer(event.getUser().getId());
                case "check_balance" -> createBalanceContainer(event.getUser().getId());
                case "back_to_bot_info" -> getBotInfoContainer();
                case "back_to_user_info" -> getUserInfoContainer(event.getUser());
                default -> throw new IllegalStateException("Unexpected value: " + id);
            };
            event.getHook().editOriginalComponents(container).useComponentsV2().queue();
        });
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String id = event.getComponentId();
        if (!"bot_details".equals(id) && !"user_actions".equals(id)) return;

        event.deferEdit().queue();
        Bot.executor.execute(() -> {
            String value = event.getValues().getFirst();
            Container container = "bot_details".equals(id)
                    ? createBotDetailsContainer(value)
                    : createUserActionContainer(event.getUser().getId(), value);
            event.getHook().editOriginalComponents(container).useComponentsV2().queue();
        });
    }

    private boolean checkCooldown(SlashCommandInteractionEvent event) {
        long now = System.currentTimeMillis();
        long userId = event.getUser().getIdLong();

        if (Bot.COOLDOWNS.containsKey(userId) && now - Bot.COOLDOWNS.get(userId) < Bot.COOLDOWN_DURATION) {
            EmbedBuilder cooldownEmbed = new EmbedBuilder()
                    .setTitle("⏰ Slow Down!")
                    .setDescription("Please wait **2 seconds** before using this command again.")
                    .setColor(new Color(255, 107, 107))
                    .setTimestamp(Instant.now());
            event.getHook().sendMessageEmbeds(cooldownEmbed.build()).queue();
            return false;
        }

        Bot.COOLDOWNS.put(userId, now);
        return true;
    }

    private boolean isValidButtonId(String id) {
        return id.matches("refresh_(stats|profile)|view_favorites|check_balance|back_to_(bot|user)_info");
    }

    private Container createBotDetailsContainer(String selection) {
        SystemMetrics metrics = new SystemMetrics();
        var shard = Bot.getShardManager().getShards().getFirst();
        String botAvatarUrl = getBotAvatarUrl(shard);

        return switch (selection) {
            case "system" -> createSystemInfoContainer(botAvatarUrl);
            case "memory" -> createMemoryInfoContainer(botAvatarUrl, metrics);
            case "performance" -> createPerformanceInfoContainer(botAvatarUrl, shard);
            default -> getBotInfoContainer();
        };
    }

    private Container createSystemInfoContainer(String botAvatarUrl) {
        return Container.of(
                createSection(botAvatarUrl, "🖥️ System Information", "System specifications and environment", "Real-time system data"),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(formatSystemInfo()),
                Separator.createDivider(Separator.Spacing.SMALL),
                createBackRefreshButtons("back_to_bot_info")
        ).withAccentColor(PRIMARY_COLOR);
    }

    private Container createMemoryInfoContainer(String botAvatarUrl, SystemMetrics metrics) {
        String memoryProgress = createProgressBar(metrics.getUsedMemoryBytes(), metrics.getMaxMemoryBytes(), 20);
        double memoryPercentage = (double) metrics.getUsedMemoryBytes() / metrics.getMaxMemoryBytes() * 100;
        long availableMemory = metrics.getMaxMemoryBytes() - metrics.getUsedMemoryBytes();

        return Container.of(
                createSection(botAvatarUrl, "💾 Memory Analysis", "Memory usage", "Real-time memory statistics"),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(formatMemoryInfo(metrics, availableMemory, memoryPercentage, memoryProgress)),
                Separator.createDivider(Separator.Spacing.SMALL),
                createBackRefreshButtons("back_to_bot_info")
        ).withAccentColor(PRIMARY_COLOR);
    }

    private Container createPerformanceInfoContainer(String botAvatarUrl, net.dv8tion.jda.api.JDA shard) {
        long uptimeMillis = System.currentTimeMillis() - Main.startTime;

        return Container.of(
                createSection(botAvatarUrl, "⚡ Performance Analytics", "Real-time performance metrics and statistics", "Live performance data"),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(formatPerformanceInfo(shard, uptimeMillis)),
                Separator.createDivider(Separator.Spacing.SMALL),
                createBackRefreshButtons("back_to_bot_info")
        ).withAccentColor(PRIMARY_COLOR);
    }

    private Container createUserActionContainer(String userId, String selection) {
        return switch (selection) {
            case "favorites" -> createFavoritesContainer(userId);
            case "balance" -> createBalanceContainer(userId);
            case "quests" -> createQuestsContainer(userId);
            case "settings" -> createSettingsContainer(userId);
            default -> getUserInfoContainer(Main.userService.getOrCreateUser(userId));
        };
    }

    private Container createFavoritesContainer(String userId) {
        User user = Main.userService.getOrCreateUser(userId);
        Color userColor = getUserColor(user);

        int favoritesUsed = user.getFavorites().size();
        int favoritesLimit = user.getFavoriteLimit();
        String favoritesBar = createProgressBar(favoritesUsed, favoritesLimit, 15);

        return Container.of(
                createSection(getDefaultAvatar(), "❤️ Favorites Management", "Manage your favorite images and collections", "Your personal favorites dashboard"),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(formatFavoritesInfo(favoritesUsed, favoritesLimit, favoritesBar)),
                Separator.createDivider(Separator.Spacing.SMALL),
                createBackRefreshButtons("back_to_user_info")
        ).withAccentColor(userColor);
    }

    private Container createBalanceContainer(String userId) {
        return createComingSoonContainer(userId, "💰 Account Balance", "View your account balance and transaction history",
                "Financial overview and statistics", "balance", "Economy features");
    }

    private Container createQuestsContainer(String userId) {
        return createComingSoonContainer(userId, "🎯 Quests & Challenges", "Complete quests to earn rewards and unlock features",
                "Your adventure awaits", "quests", "Quest system");
    }

    private Container createSettingsContainer(String userId) {
        User user = Main.userService.getOrCreateUser(userId);
        Color userColor = getUserColor(user);

        return Container.of(
                createSection(getDefaultAvatar(), "⚙️ Account Settings", "Customize your Media Roulette experience", "Personalize your preferences"),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(formatSettingsInfo(user)),
                Separator.createDivider(Separator.Spacing.SMALL),
                createBackRefreshButtons("back_to_user_info")
        ).withAccentColor(userColor);
    }

    private Container createComingSoonContainer(String userId, String title, String description, String subtitle, String command, String feature) {
        User user = Main.userService.getOrCreateUser(userId);
        Color userColor = getUserColor(user);

        return Container.of(
                createSection(getDefaultAvatar(), title, description, subtitle),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(formatComingSoonInfo(user, command, feature)),
                Separator.createDivider(Separator.Spacing.SMALL),
                createBackRefreshButtons("back_to_user_info")
        ).withAccentColor(userColor);
    }

    public Container getBotInfoContainer() {
        SystemMetrics metrics = new SystemMetrics();
        var shard = Bot.getShardManager().getShards().getFirst();
        String botAvatarUrl = getBotAvatarUrl(shard);

        long totalImages = Main.userService.getTotalImagesGenerated();
        long totalUsers = Main.userService.getTotalUsers();
        long uptimeMillis = System.currentTimeMillis() - Main.startTime;
        String memoryProgress = createProgressBar(metrics.getUsedMemoryBytes(), metrics.getMaxMemoryBytes(), 12);

        return Container.of(
                createSection(botAvatarUrl, "🤖 Media Roulette Bot", "Your premium AI-powered media generation companion", "Real-time statistics and system information"),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(formatBotStats(totalImages, totalUsers, uptimeMillis, shard, metrics, memoryProgress)),
                Separator.createDivider(Separator.Spacing.SMALL),
                ActionRow.of(createBotDetailsSelect()),
                ActionRow.of(createBotActionButtons())
        ).withAccentColor(PRIMARY_COLOR);
    }

    public Container getUserInfoContainer(net.dv8tion.jda.api.entities.User discordUser) {
        return getUserInfoContainer(discordUser.getId(), discordUser.getName(), discordUser.getAvatarUrl());
    }

    public Container getUserInfoContainer(User user) {
        return getUserInfoContainer(user.getUserId(), user.toString(), null);
    }

    public Container getUserInfoContainer(String id, String username, String avatarUrl) {
        User user = Main.userService.getOrCreateUser(id);
        Color userColor = getUserColor(user);
        String title = buildUserTitle(username, user);

        if (avatarUrl == null) avatarUrl = getDefaultAvatar();

        int favoritesUsed = user.getFavorites().size();
        int favoritesLimit = user.getFavoriteLimit();
        String favoritesBar = createProgressBar(favoritesUsed, favoritesLimit, 12);

        String accountLevel = getAccountLevel(user);
        String statusText = getStatusText(user);
        String usageLevel = getUsageLevel(user.getImagesGenerated());
        String tips = buildTips(user, favoritesUsed, favoritesLimit);

        return Container.of(
                createSection(avatarUrl, title, "Your personal Media Roulette profile", "Account Level: " + accountLevel),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(formatUserStats(user.getImagesGenerated(), usageLevel, statusText, favoritesUsed, favoritesLimit, favoritesBar, tips)),
                Separator.createDivider(Separator.Spacing.SMALL),
                ActionRow.of(createUserActionsSelect()),
                ActionRow.of(createUserActionButtons())
        ).withAccentColor(userColor);
    }

    // Helper methods
    private Section createSection(String imageUrl, String title, String description, String subtitle) {
        return Section.of(
                Thumbnail.fromUrl(imageUrl),
                TextDisplay.of("## " + title),
                TextDisplay.of("**" + description + "**"),
                TextDisplay.of("*" + subtitle + "*")
        );
    }

    private ActionRow createBackRefreshButtons(String backButton) {
        return ActionRow.of(
                Button.secondary(backButton, "⬅️ Back"),
                Button.secondary("refresh_stats", "🔄 Refresh")
        );
    }

    private StringSelectMenu createBotDetailsSelect() {
        return StringSelectMenu.create("bot_details")
                .setPlaceholder("🔍 View Detailed Information")
                .addOption("System Details", "system", "View detailed system information")
                .addOption("Memory Analysis", "memory", "View detailed memory usage")
                .addOption("Performance Metrics", "performance", "View performance analytics")
                .build();
    }

    private Button[] createBotActionButtons() {
        return new Button[]{
                Button.link("https://discord.gg/Kr7qvutZ4N", "🆘 Support Server"),
                Button.link("https://www.buymeacoffee.com/HashyDev", "☕ Donate"),
                Button.secondary("refresh_stats", "🔄 Refresh Stats")
        };
    }

    private StringSelectMenu createUserActionsSelect() {
        return StringSelectMenu.create("user_actions")
                .setPlaceholder("🚀 Quick Actions")
                .addOption("View Favorites", "favorites", "❤️ Manage your favorite images")
                .addOption("Check Balance", "balance", "💰 View your account balance")
                .addOption("View Quests", "quests", "🎯 Check available quests")
                .addOption("Account Settings", "settings", "⚙️ Manage your preferences")
                .build();
    }

    private Button[] createUserActionButtons() {
        return new Button[]{
                Button.secondary("view_favorites", "❤️ Favorites"),
                Button.secondary("check_balance", "💰 Balance"),
                Button.secondary("refresh_profile", "🔄 Refresh"),
                Button.link("https://discord.gg/Kr7qvutZ4N", "🆘 Help")
        };
    }

    private String getBotAvatarUrl(net.dv8tion.jda.api.JDA shard) {
        String url = shard.getSelfUser().getAvatarUrl();
        return url != null ? url : getDefaultAvatar();
    }

    private String getDefaultAvatar() {
        return "https://cdn.discordapp.com/embed/avatars/0.png";
    }

    private Color getUserColor(User user) {
        if (user.isPremium()) return PREMIUM_COLOR;
        if (user.isAdmin()) return new Color(220, 20, 60);
        return ACCENT_COLOR;
    }

    private String buildUserTitle(String username, User user) {
        StringBuilder title = new StringBuilder("👤 " + username);
        if (user.isAdmin()) title.append(" 🛡️");
        if (user.isPremium()) title.append(" 👑");
        return title.toString();
    }

    private String getStatusText(User user) {
        String status = user.isPremium() ? "👑 Premium Member" : "🆓 Free Tier";
        if (user.isAdmin()) status += " | 🛡️ Administrator";
        return status;
    }

    private String buildTips(User user, int favoritesUsed, int favoritesLimit) {
        StringBuilder tips = new StringBuilder();
        if (!user.isPremium()) {
            tips.append("💡 **Upgrade to Premium** for more favorite slots!\n");
        }
        if (favoritesUsed < favoritesLimit) {
            tips.append("💾 You have **").append(favoritesLimit - favoritesUsed).append(" favorite slots** available\n");
        }
        long images = user.getImagesGenerated();
        tips.append(images < 10 ? "🎨 **Keep exploring** to unlock new features!" : "🎨 **Great job!** You're an active user!");
        return tips.toString();
    }

    // Format methods
    private String formatSystemInfo() {
        return String.format("""
                ### 🖥️ **Operating System**
                **OS Name:** `%s`
                **OS Version:** `%s`
                **Architecture:** `%s`
                
                ### ☕ **Java Environment**
                **Java Version:** `%s`
                **Java Vendor:** `%s`
                **Runtime Name:** `%s`
                
                ### ⚙️ **Hardware Resources**
                **Available Processors:** `%d cores`
                **System Load Average:** `%s`
                **JDA Version:** `%s`""",
                System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"),
                System.getProperty("java.version"), System.getProperty("java.vendor"), System.getProperty("java.runtime.name"),
                OS_BEAN.getAvailableProcessors(), getSystemLoad(), net.dv8tion.jda.api.JDAInfo.VERSION);
    }

    private String formatMemoryInfo(SystemMetrics metrics, long availableMemory, double memoryPercentage, String memoryProgress) {
        String efficiency = memoryPercentage < 80 ? "Optimal" : memoryPercentage < 90 ? "Good" : "High Usage";
        return String.format("""
                ### 📊 **Memory Usage Overview**
                **Used Memory:** `%s`
                **Maximum Memory:** `%s`
                **Available Memory:** `%s`
                
                ### 📈 **Memory Statistics**
                **Usage Percentage:** `%.1f%%`
                **Memory Efficiency:** `%s`
                **Progress Bar:** `%s`""",
                metrics.getUsedMemory(), metrics.getMaxMemory(), formatBytes(availableMemory),
                memoryPercentage, efficiency, memoryProgress);
    }

    private String formatPerformanceInfo(net.dv8tion.jda.api.JDA shard, long uptimeMillis) {
        return String.format("""
                ### 🏓 **Network Performance**
                **Gateway Ping:** `%dms`
                **Connection Status:** `%s`
                **Shard ID:** `%d/%d`
                
                ### ⏱️ **Runtime Statistics**
                **Bot Uptime:** `%s`
                **Active Threads:** `%d`
                **Total Guilds:** `%s`""",
                shard.getGatewayPing(), shard.getStatus().name(),
                shard.getShardInfo().getShardId(), shard.getShardInfo().getShardTotal(),
                formatUptime(uptimeMillis), Thread.activeCount(), formatNumber(shard.getGuilds().size()));
    }

    private String formatFavoritesInfo(int favoritesUsed, int favoritesLimit, String favoritesBar) {
        return String.format("""
                ### 📊 **Favorites Overview**
                **Used Slots:** `%d/%d`
                **Available Slots:** `%d`
                **Progress:** `%s`
                
                ### 💡 **Quick Actions**
                Use `/favorites view` to see your saved images
                Use `/favorites add` to save new favorites
                Use `/favorites remove` to manage your collection""",
                favoritesUsed, favoritesLimit, favoritesLimit - favoritesUsed, favoritesBar);
    }

    private String formatComingSoonInfo(User user, String command, String feature) {
        return String.format("""
                ### 💳 **Information**
                **Current Status:** `Coming Soon`
                **Account Type:** `%s`
                **Status:** `Active`
                
                ### 📈 **Quick Actions**
                Use `/%s` command for detailed information
                %s is currently in development
                Stay tuned for updates!""",
                user.isPremium() ? "Premium" : "Free", command, feature);
    }

    private String formatSettingsInfo(User user) {
        return String.format("""
                ### 🔧 **Current Settings**
                **Account Type:** `%s`
                **Admin Status:** `%s`
                **Profile Status:** `Active`
                
                ### 🎛️ **Available Options**
                Use `/settings` command for detailed configuration
                Customize themes, notifications, and preferences
                More settings options coming soon!""",
                user.isPremium() ? "Premium" : "Free", user.isAdmin() ? "Yes" : "No");
    }

    private String formatBotStats(long totalImages, long totalUsers, long uptimeMillis,
                                  net.dv8tion.jda.api.JDA shard, SystemMetrics metrics, String memoryProgress) {
        return String.format("""
                ### 📊 **Usage Statistics**
                **📈 Total Images Generated:** `%s`
                **👥 Total Users:** `%s`
                **⏱️ Bot Uptime:** `%s`
                
                ### ⚡ **Performance Metrics**
                **🏓 Gateway Ping:** `%dms`
                **💾 Memory Usage:** `%s / %s`
                **📊 Memory Progress:** `%s`
                
                ### 🖥️ **System Resources**
                **⚙️ CPU Cores:** `%d`
                **📈 System Load:** `%s`
                **☕ Java Version:** `%s`
                
                ### 🔗 **Discord Statistics**
                **🏠 Guilds:** `%s`
                **🔀 Total Shards:** `%d`
                **🧵 Active Threads:** `%d`""",
                formatNumber(totalImages), formatNumber(totalUsers), formatUptime(uptimeMillis),
                shard.getGatewayPing(), metrics.getUsedMemory(), metrics.getMaxMemory(), memoryProgress,
                metrics.getCpuCores(), metrics.getSystemLoad(), System.getProperty("java.version"),
                formatNumber(shard.getGuilds().size()), shard.getShardInfo().getShardTotal(), Thread.activeCount());
    }

    private String formatUserStats(long imagesGenerated, String usageLevel, String statusText,
                                   int favoritesUsed, int favoritesLimit, String favoritesBar, String tips) {
        return String.format("""
                ### 🎨 **Generation Statistics**
                **📈 Images Generated:** `%s`
                **⭐ Usage Level:** `%s`
                **🏷️ Account Status:** %s
                
                ### ❤️ **Favorites Management**
                **📊 Used Slots:** `%d/%d`
                **📈 Progress:** `%s`
                **🆓 Available:** `%d slots remaining`
                
                ### 💡 **Tips & Recommendations**
                %s""",
                formatNumber(imagesGenerated), usageLevel, statusText,
                favoritesUsed, favoritesLimit, favoritesBar, favoritesLimit - favoritesUsed, tips);
    }

    private String formatNumber(long number) {
        return String.format("%,d", number);
    }

    private String formatUptime(long uptimeMillis) {
        long days = TimeUnit.MILLISECONDS.toDays(uptimeMillis);
        long hours = TimeUnit.MILLISECONDS.toHours(uptimeMillis) - TimeUnit.DAYS.toHours(days);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(uptimeMillis) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(uptimeMillis));

        if (days > 0) return String.format("%dd %dh %dm", days, hours, minutes);
        if (hours > 0) return String.format("%dh %dm", hours, minutes);
        return String.format("%dm", minutes);
    }

    private String createProgressBar(long current, long max, int length) {
        if (max == 0) return "░".repeat(length);
        int filled = (int) ((double) current / max * length);
        return "█".repeat(Math.min(filled, length)) + "░".repeat(Math.max(0, length - filled));
    }

    private String getUsageLevel(long imagesGenerated) {
        if (imagesGenerated >= 1000) return "Expert Creator";
        if (imagesGenerated >= 500) return "Advanced User";
        if (imagesGenerated >= 100) return "Regular User";
        if (imagesGenerated >= 10) return "Active User";
        return "New User";
    }

    private String getAccountLevel(User user) {
        if (user.isAdmin()) return "Administrator";
        if (user.isPremium()) return "Premium";
        return "Standard";
    }

    private String getSystemLoad() {
        double load = OS_BEAN.getSystemLoadAverage();
        return load < 0 ? "N/A" : String.format("%.2f", load);
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) return "0 B";

        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = 0;
        double size = bytes;

        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }

        // Format to 1 decimal place for values >= 10, no decimals for < 10
        if (size >= 10) {
            return String.format("%.0f %s", size, units[unitIndex]);
        } else {
            return String.format("%.1f %s", size, units[unitIndex]);
        }
    }

    // Simplified SystemMetrics class
    private static class SystemMetrics {
        public String getUsedMemory() {
            return formatBytes(MEMORY_BEAN.getHeapMemoryUsage().getUsed());
        }

        public String getMaxMemory() {
            long max = MEMORY_BEAN.getHeapMemoryUsage().getMax();
            return max == -1 ? "Unlimited" : formatBytes(max);
        }

        public long getUsedMemoryBytes() {
            return MEMORY_BEAN.getHeapMemoryUsage().getUsed();
        }

        public long getMaxMemoryBytes() {
            long max = MEMORY_BEAN.getHeapMemoryUsage().getMax();
            return max == -1 ? RUNTIME.maxMemory() : max;
        }

        public int getCpuCores() {
            return OS_BEAN.getAvailableProcessors();
        }

        public String getSystemLoad() {
            double load = OS_BEAN.getSystemLoadAverage();
            return load < 0 ? "N/A" : String.format("%.2f", load);
        }
    }
}