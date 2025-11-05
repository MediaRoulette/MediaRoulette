package me.hash.mediaroulette.bot.commands.bot;

import java.awt.Color;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.bot.commands.BaseCommand;
import me.hash.mediaroulette.bot.utils.CommandCooldown;
import me.hash.mediaroulette.model.User;
import net.dv8tion.jda.api.JDA;
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
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import static me.hash.mediaroulette.bot.MediaContainerManager.PREMIUM_COLOR;
import static me.hash.mediaroulette.bot.MediaContainerManager.PRIMARY_COLOR;

public class InfoCommand extends BaseCommand {
    private static final Color ACCENT_COLOR = new Color(114, 137, 218);
    private static final Color ADMIN_COLOR = new Color(220, 20, 60);
    private static final MemoryMXBean MEMORY_BEAN = ManagementFactory.getMemoryMXBean();
    private static final OperatingSystemMXBean OS_BEAN = ManagementFactory.getOperatingSystemMXBean();
    private static final String DEFAULT_AVATAR = "https://cdn.discordapp.com/embed/avatars/0.png";

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
    @CommandCooldown(value = 3, commands = {"info"})
    public void handleCommand(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("info")) return;

        event.deferReply().queue();
        Bot.executor.execute(() -> {
            Container container = "bot".equals(event.getSubcommandName())
                    ? createBotInfo()
                    : createUserInfo(event.getUser().getId(), event.getUser().getName(), event.getUser().getAvatarUrl());
            event.getHook().sendMessageComponents(container).useComponentsV2().queue();
        });
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.matches("refresh_(stats|profile)|view_favorites|check_balance|back_to_(bot|user)_info")) return;

        event.deferEdit().queue();
        Bot.executor.execute(() -> {
            Container container = switch (id) {
                case "refresh_stats", "back_to_bot_info" -> createBotInfo();
                case "refresh_profile", "back_to_user_info" -> createUserInfo(event.getUser().getId(), event.getUser().getName(), event.getUser().getAvatarUrl());
                case "view_favorites" -> createDetailView(event.getUser().getId(), "favorites");
                case "check_balance" -> createDetailView(event.getUser().getId(), "balance");
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
                    ? createBotDetailView(value)
                    : createDetailView(event.getUser().getId(), value);
            event.getHook().editOriginalComponents(container).useComponentsV2().queue();
        });
    }

    private Container createBotInfo() {
        JDA shard = Bot.getShardManager().getShards().getFirst();
        long totalImages = Main.getUserService().getTotalImagesGenerated();
        long totalUsers = Main.getUserService().getTotalUsers();
        long uptime = System.currentTimeMillis() - Main.START_TIME;
        long usedMem = MEMORY_BEAN.getHeapMemoryUsage().getUsed();
        long maxMem = getMaxMemory();

        String content = String.format("""
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
                formatNumber(totalImages), formatNumber(totalUsers), formatUptime(uptime),
                shard.getGatewayPing(), formatBytes(usedMem), formatBytes(maxMem), createProgressBar(usedMem, maxMem, 12),
                OS_BEAN.getAvailableProcessors(), getSystemLoad(), System.getProperty("java.version"),
                formatNumber(shard.getGuilds().size()), shard.getShardInfo().getShardTotal(), Thread.activeCount());

        return buildContainer(
                getBotAvatar(shard),
                "🤖 Media Roulette Bot",
                "Your premium AI-powered media generation companion",
                "Real-time statistics and system information",
                content,
                PRIMARY_COLOR,
                createBotDetailsSelect(),
                new Button[]{
                        Button.link("https://discord.gg/Kr7qvutZ4N", "🆘 Support Server"),
                        Button.link("https://www.buymeacoffee.com/HashyDev", "☕ Donate"),
                        Button.secondary("refresh_stats", "🔄 Refresh Stats")
                }
        );
    }

    private Container createUserInfo(String userId, String username, String avatarUrl) {
        User user = Main.getUserService().getOrCreateUser(userId);
        int favUsed = user.getFavorites().size();
        int favLimit = user.getFavoriteLimit();
        String title = buildUserTitle(username, user);

        String content = String.format("""
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
                formatNumber(user.getImagesGenerated()), getUsageLevel(user.getImagesGenerated()), getStatusText(user),
                favUsed, favLimit, createProgressBar(favUsed, favLimit, 12), favLimit - favUsed,
                buildTips(user, favUsed, favLimit));

        return buildContainer(
                avatarUrl != null ? avatarUrl : DEFAULT_AVATAR,
                title,
                "Your personal Media Roulette profile",
                "Account Level: " + getAccountLevel(user),
                content,
                getUserColor(user),
                createUserActionsSelect(),
                new Button[]{
                        Button.secondary("view_favorites", "❤️ Favorites"),
                        Button.secondary("check_balance", "💰 Balance"),
                        Button.secondary("refresh_profile", "🔄 Refresh"),
                        Button.link("https://discord.gg/Kr7qvutZ4N", "🆘 Help")
                }
        );
    }

    private Container createBotDetailView(String type) {
        JDA shard = Bot.getShardManager().getShards().getFirst();
        String avatar = getBotAvatar(shard);
        long usedMem = MEMORY_BEAN.getHeapMemoryUsage().getUsed();
        long maxMem = getMaxMemory();

        return switch (type) {
            case "system" -> buildContainer(avatar, "🖥️ System Information", "System specifications and environment",
                    "Real-time system data", formatSystemInfo(), PRIMARY_COLOR, null, createBackButton());
            case "memory" -> buildContainer(avatar, "💾 Memory Analysis", "Memory usage", "Real-time memory statistics",
                    formatMemoryInfo(usedMem, maxMem), PRIMARY_COLOR, null, createBackButton());
            case "performance" -> buildContainer(avatar, "⚡ Performance Analytics", "Real-time performance metrics",
                    "Live performance data", formatPerformanceInfo(shard), PRIMARY_COLOR, null, createBackButton());
            default -> createBotInfo();
        };
    }

    private Container createDetailView(String userId, String type) {
        User user = Main.getUserService().getOrCreateUser(userId);
        Color color = getUserColor(user);

        return switch (type) {
            case "favorites" -> {
                int used = user.getFavorites().size();
                int limit = user.getFavoriteLimit();
                yield buildContainer(DEFAULT_AVATAR, "❤️ Favorites Management", "Manage your favorite images",
                        "Your personal favorites dashboard", formatFavoritesInfo(used, limit), color, null, createBackButton("back_to_user_info"));
            }
            case "settings" -> buildContainer(DEFAULT_AVATAR, "⚙️ Account Settings", "Customize your experience",
                    "Personalize your preferences", formatSettingsInfo(user), color, null, createBackButton("back_to_user_info"));
            case "balance", "quests" -> buildContainer(DEFAULT_AVATAR,
                    type.equals("balance") ? "💰 Account Balance" : "🎯 Quests & Challenges",
                    type.equals("balance") ? "View balance and transactions" : "Complete quests to earn rewards",
                    type.equals("balance") ? "Financial overview" : "Your adventure awaits",
                    formatComingSoon(user, type), color, null, createBackButton("back_to_user_info"));
            default -> createUserInfo(userId, user.toString(), null);
        };
    }

    private Container buildContainer(String imageUrl, String title, String desc, String subtitle,
                                     String content, Color color, StringSelectMenu menu, Button[] buttons) {
        Section section = Section.of(
                Thumbnail.fromUrl(imageUrl),
                TextDisplay.of("## " + title),
                TextDisplay.of("**" + desc + "**"),
                TextDisplay.of("*" + subtitle + "*")
        );

        if (menu != null && buttons != null) {
            return Container.of(section, Separator.createDivider(Separator.Spacing.SMALL),
                    TextDisplay.of(content), Separator.createDivider(Separator.Spacing.SMALL),
                    ActionRow.of(menu), ActionRow.of(Arrays.asList(buttons))).withAccentColor(color);
        } else {
            return Container.of(section, Separator.createDivider(Separator.Spacing.SMALL),
                    TextDisplay.of(content), Separator.createDivider(Separator.Spacing.SMALL),
                    ActionRow.of(Arrays.asList(buttons))).withAccentColor(color);
        }
    }

    private Button[] createBackButton() {
        return createBackButton("back_to_bot_info");
    }

    private Button[] createBackButton(String id) {
        return new Button[]{Button.secondary(id, "⬅️ Back"), Button.secondary("refresh_stats", "🔄 Refresh")};
    }

    private StringSelectMenu createBotDetailsSelect() {
        return StringSelectMenu.create("bot_details")
                .setPlaceholder("🔍 View Detailed Information")
                .addOption("System Details", "system", "View detailed system information")
                .addOption("Memory Analysis", "memory", "View detailed memory usage")
                .addOption("Performance Metrics", "performance", "View performance analytics")
                .build();
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

    private String formatSystemInfo() {
        return String.format("""
                ### 🖥️ **Operating System**
                **OS Name:** `%s`
                **OS Version:** `%s`
                **Architecture:** `%s`
                
                ### ☕ **Java Environment**
                **Java Version:** `%s`
                **Java Vendor:** `%s`
                
                ### ⚙️ **Hardware Resources**
                **Available Processors:** `%d cores`
                **System Load Average:** `%s`
                **JDA Version:** `%s`""",
                System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"),
                System.getProperty("java.version"), System.getProperty("java.vendor"),
                OS_BEAN.getAvailableProcessors(), getSystemLoad(), net.dv8tion.jda.api.JDAInfo.VERSION);
    }

    private String formatMemoryInfo(long used, long max) {
        double percent = (double) used / max * 100;
        String efficiency = percent < 80 ? "Optimal" : percent < 90 ? "Good" : "High Usage";
        return String.format("""
                ### 📊 **Memory Usage Overview**
                **Used Memory:** `%s`
                **Maximum Memory:** `%s`
                **Available Memory:** `%s`
                
                ### 📈 **Memory Statistics**
                **Usage Percentage:** `%.1f%%`
                **Memory Efficiency:** `%s`
                **Progress Bar:** `%s`""",
                formatBytes(used), formatBytes(max), formatBytes(max - used),
                percent, efficiency, createProgressBar(used, max, 20));
    }

    private String formatPerformanceInfo(JDA shard) {
        long uptime = System.currentTimeMillis() - Main.START_TIME;
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
                formatUptime(uptime), Thread.activeCount(), formatNumber(shard.getGuilds().size()));
    }

    private String formatFavoritesInfo(int used, int limit) {
        return String.format("""
                ### 📊 **Favorites Overview**
                **Used Slots:** `%d/%d`
                **Available Slots:** `%d`
                **Progress:** `%s`
                
                ### 💡 **Quick Actions**
                Use `/favorites view` to see your saved images
                Use `/favorites add` to save new favorites
                Use `/favorites remove` to manage your collection""",
                used, limit, limit - used, createProgressBar(used, limit, 15));
    }

    private String formatComingSoon(User user, String type) {
        String feature = type.equals("balance") ? "Economy features" : "Quest system";
        return String.format("""
                ### 💳 **Information**
                **Current Status:** `Coming Soon`
                **Account Type:** `%s`
                
                ### 📈 **Quick Actions**
                Use `/%s` command for detailed information
                %s is currently in development
                Stay tuned for updates!""",
                user.isPremium() ? "Premium" : "Free", type, feature);
    }

    private String formatSettingsInfo(User user) {
        return String.format("""
                ### 🔧 **Current Settings**
                **Account Type:** `%s`
                **Admin Status:** `%s`
                **Profile Status:** `Active`
                
                ### 🎛️ **Available Options**
                Use `/settings` command for detailed configuration
                Customize themes, notifications, and preferences""",
                user.isPremium() ? "Premium" : "Free", user.isAdmin() ? "Yes" : "No");
    }

    private String getBotAvatar(JDA shard) {
        String url = shard.getSelfUser().getAvatarUrl();
        return url != null ? url : DEFAULT_AVATAR;
    }

    private Color getUserColor(User user) {
        if (user.isPremium()) return PREMIUM_COLOR;
        if (user.isAdmin()) return ADMIN_COLOR;
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

    private String buildTips(User user, int used, int limit) {
        StringBuilder tips = new StringBuilder();
        if (!user.isPremium()) tips.append("💡 **Upgrade to Premium** for more favorite slots!\n");
        if (used < limit) tips.append("💾 You have **").append(limit - used).append(" favorite slots** available\n");
        long images = user.getImagesGenerated();
        tips.append(images < 10 ? "🎨 **Keep exploring** to unlock new features!" : "🎨 **Great job!** You're an active user!");
        return tips.toString();
    }

    private String formatNumber(long number) {
        return String.format("%,d", number);
    }

    private String formatUptime(long millis) {
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        if (days > 0) return String.format("%dd %dh %dm", days, hours, minutes);
        if (hours > 0) return String.format("%dh %dm", hours, minutes);
        return String.format("%dm", minutes);
    }

    private String createProgressBar(long current, long max, int length) {
        if (max == 0) return "░".repeat(length);
        int filled = (int) ((double) current / max * length);
        return "█".repeat(Math.min(filled, length)) + "░".repeat(Math.max(0, length - filled));
    }

    private String getUsageLevel(long images) {
        if (images >= 1000) return "Expert Creator";
        if (images >= 500) return "Advanced User";
        if (images >= 100) return "Regular User";
        if (images >= 10) return "Active User";
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

    private long getMaxMemory() {
        long max = MEMORY_BEAN.getHeapMemoryUsage().getMax();
        return max == -1 ? Runtime.getRuntime().maxMemory() : max;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int i = 0;
        double size = bytes;
        while (size >= 1024 && i < units.length - 1) {
            size /= 1024;
            i++;
        }
        return size >= 10 ? String.format("%.0f %s", size, units[i]) : String.format("%.1f %s", size, units[i]);
    }

    public Container getUserInfoContainer(net.dv8tion.jda.api.entities.User discordUser) {
        return createUserInfo(discordUser.getId(), discordUser.getName(), discordUser.getAvatarUrl());
    }

    public Container getUserInfoContainer(User user) {
        return createUserInfo(user.getUserId(), user.toString(), null);
    }
}