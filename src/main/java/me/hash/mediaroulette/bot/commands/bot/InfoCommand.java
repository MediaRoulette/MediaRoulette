package me.hash.mediaroulette.bot.commands.bot;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.bot.commands.BaseCommand;
import me.hash.mediaroulette.bot.utils.CommandCooldown;
import me.hash.mediaroulette.bot.utils.Emoji;
import me.hash.mediaroulette.model.User;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.awt.Color;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.concurrent.TimeUnit;

import static me.hash.mediaroulette.bot.utils.EmbedFactory.PREMIUM_COLOR;
import static me.hash.mediaroulette.bot.utils.EmbedFactory.PRIMARY_COLOR;

public class InfoCommand extends BaseCommand {
    private static final Color ACCENT_COLOR = new Color(114, 137, 218);
    private static final Color ADMIN_COLOR = new Color(220, 20, 60);
    private static final MemoryMXBean MEMORY_BEAN = ManagementFactory.getMemoryMXBean();
    private static final OperatingSystemMXBean OS_BEAN = ManagementFactory.getOperatingSystemMXBean();
    private static final String DEFAULT_AVATAR = "https://cdn.discordapp.com/embed/avatars/0.png";

    @Override
    public CommandData getCommandData() {
        return Commands.slash("info", "📊 Bot and user information")
                .addSubcommands(
                        new SubcommandData("bot", "🤖 Bot statistics"),
                        new SubcommandData("me", "👤 Your profile")
                ).setIntegrationTypes(IntegrationType.ALL)
                .setContexts(InteractionContextType.ALL);
    }

    @Override
    @CommandCooldown(value = 3, commands = {"info"})
    public void handleCommand(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("info")) return;

        event.deferReply().queue();
        Main.getBot().getExecutor().execute(() -> {
            Container container = "bot".equals(event.getSubcommandName())
                    ? createBotInfo()
                    : createUserInfo(event.getUser().getId(), event.getUser().getName(), event.getUser().getAvatarUrl());
            event.getHook().sendMessageComponents(container).useComponentsV2().queue();
        });
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("refresh_")) return;

        event.deferEdit().queue();
        Main.getBot().getExecutor().execute(() -> {
            Container container = id.equals("refresh_stats") ? createBotInfo() 
                    : createUserInfo(event.getUser().getId(), event.getUser().getName(), event.getUser().getAvatarUrl());
            event.getHook().editOriginalComponents(container).useComponentsV2().queue();
        });
    }

    private Container createBotInfo() {
        JDA shard = Main.getBot().getShardManager().getShards().getFirst();
        long totalImages = Main.getUserService().getTotalImagesGenerated();
        long totalUsers = Main.getUserService().getTotalUsers();
        long uptime = System.currentTimeMillis() - Main.START_TIME;
        long usedMem = MEMORY_BEAN.getHeapMemoryUsage().getUsed();
        long maxMem = getMaxMemory();

        Section header = Section.of(
                Thumbnail.fromUrl(getBotAvatar(shard)),
                TextDisplay.of("## 🤖 Media Roulette Bot"),
                TextDisplay.of("**Real-time Statistics & Performance**")
        );

        String statsText = String.format("""
                ### 📊 Usage
                **Images:** `%s` • **Users:** `%s` • **Uptime:** `%s`
                
                ### ⚡ Performance
                **Ping:** `%dms` • **Guilds:** `%s` • **Shards:** `%d`
                **Memory:** `%s / %s` (%.0f%%)
                %s
                
                ### 🖥️ System
                **CPU:** `%d cores` • **Load:** `%s` • **Java:** `%s`""",
                formatNumber(totalImages), formatNumber(totalUsers), formatUptime(uptime),
                shard.getGatewayPing(), formatNumber(shard.getGuilds().size()), shard.getShardInfo().getShardTotal(),
                formatBytes(usedMem), formatBytes(maxMem), (double) usedMem / maxMem * 100,
                Emoji.createProgressBar(usedMem, maxMem, 10),
                OS_BEAN.getAvailableProcessors(), getSystemLoad(), System.getProperty("java.version").split("\\.")[0]);

        return Container.of(
                header,
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(statsText),
                Separator.createDivider(Separator.Spacing.SMALL),
                ActionRow.of(
                        Button.link("https://discord.gg/Kr7qvutZ4N", "Support"),
                        Button.link("https://www.buymeacoffee.com/HashyDev", "Donate"),
                        Button.secondary("refresh_stats", "🔄 Refresh")
                )
        ).withAccentColor(PRIMARY_COLOR);
    }

    private Container createUserInfo(String userId, String username, String avatarUrl) {
        User user = Main.getUserService().getOrCreateUser(userId);
        int favUsed = user.getFavorites().size();
        int favLimit = user.getFavoriteLimit();
        String badges = (user.isAdmin() ? "🛡️ " : "") + (user.isPremium() ? "👑 " : "");

        Section header = Section.of(
                Thumbnail.fromUrl(avatarUrl != null ? avatarUrl : DEFAULT_AVATAR),
                TextDisplay.of("## 👤 " + username + badges),
                TextDisplay.of("**" + getAccountLevel(user) + " Account**")
        );

        String infoText = String.format("""
                ### 🎨 Statistics
                **Images:** `%s` • **Level:** `%s`
                
                ### ❤️ Favorites
                **Slots:** `%d/%d` (%d remaining)
                %s""",
                formatNumber(user.getImagesGenerated()), getUsageLevel(user.getImagesGenerated()),
                favUsed, favLimit, favLimit - favUsed,
                Emoji.createProgressBar(favUsed, favLimit, 10));

        return Container.of(
                header,
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(infoText),
                Separator.createDivider(Separator.Spacing.SMALL),
                ActionRow.of(
                        Button.link("https://discord.gg/Kr7qvutZ4N", "Support"),
                        Button.secondary("refresh_profile", "🔄 Refresh")
                )
        ).withAccentColor(getUserColor(user));
    }

    // Helper Methods
    private String getBotAvatar(JDA shard) {
        String url = shard.getSelfUser().getAvatarUrl();
        return url != null ? url : DEFAULT_AVATAR;
    }

    private Color getUserColor(User user) {
        return user.isPremium() ? PREMIUM_COLOR : user.isAdmin() ? ADMIN_COLOR : ACCENT_COLOR;
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

    private String getUsageLevel(long images) {
        if (images >= 1000) return "Expert";
        if (images >= 500) return "Advanced";
        if (images >= 100) return "Regular";
        if (images >= 10) return "Active";
        return "New";
    }

    private String getAccountLevel(User user) {
        if (user.isAdmin()) return "Administrator";
        if (user.isPremium()) return "Premium";
        return "Standard";
    }

    private String getSystemLoad() {
        double load = OS_BEAN.getSystemLoadAverage();
        return load < 0 ? "N/A" : String.format("%.1f", load);
    }

    private long getMaxMemory() {
        long max = MEMORY_BEAN.getHeapMemoryUsage().getMax();
        return max == -1 ? Runtime.getRuntime().maxMemory() : max;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB"};
        int i = 0;
        double size = bytes;
        while (size >= 1024 && i < units.length - 1) {
            size /= 1024;
            i++;
        }
        return String.format("%.1f %s", size, units[i]);
    }

}