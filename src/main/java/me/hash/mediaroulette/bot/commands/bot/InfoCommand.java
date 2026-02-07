package me.hash.mediaroulette.bot.commands.bot;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.commands.BaseCommand;
import me.hash.mediaroulette.bot.utils.CommandCooldown;
import me.hash.mediaroulette.bot.utils.Emoji;
import me.hash.mediaroulette.locale.LocaleManager;
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
            User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
            LocaleManager locale = LocaleManager.getInstance(user.getLocale());
            
            Container container = "bot".equals(event.getSubcommandName())
                    ? createBotInfo(locale)
                    : createUserInfo(event.getUser().getId(), event.getUser().getName(), event.getUser().getAvatarUrl(), locale);
            event.getHook().sendMessageComponents(container).useComponentsV2().queue();
        });
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("refresh_")) return;

        event.deferEdit().queue();
        Main.getBot().getExecutor().execute(() -> {
            User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
            LocaleManager locale = LocaleManager.getInstance(user.getLocale());
            
            Container container = id.equals("refresh_stats") ? createBotInfo(locale) 
                    : createUserInfo(event.getUser().getId(), event.getUser().getName(), event.getUser().getAvatarUrl(), locale);
            event.getHook().editOriginalComponents(container).useComponentsV2().queue();
        });
    }

    private Container createBotInfo(LocaleManager locale) {
        JDA shard = Main.getBot().getShardManager().getShards().getFirst();
        long totalImages = Main.getUserService().getTotalImagesGenerated();
        long totalUsers = Main.getUserService().getTotalUsers();
        long uptime = System.currentTimeMillis() - Main.START_TIME;
        long usedMem = MEMORY_BEAN.getHeapMemoryUsage().getUsed();
        long maxMem = getMaxMemory();

        Section header = Section.of(
                Thumbnail.fromUrl(getBotAvatar(shard)),
                TextDisplay.of("## 🤖 " + locale.get("info.title")),
                TextDisplay.of("**" + locale.get("info.subtitle") + "**")
        );

        String statsText = String.format("""
                ### 📊 %s
                **%s:** `%s` • **%s:** `%s` • **%s:** `%s`
                
                ### ⚡ %s
                **%s:** `%dms` • **%s:** `%s` • **%s:** `%d`
                **%s:** `%s / %s` (%.0f%%)
                %s
                
                ### 🖥️ %s
                **%s:** `%d cores` • **%s:** `%s` • **%s:** `%s`""",
                locale.get("info.usage_section"),
                locale.get("info.images_generated"), formatNumber(totalImages), locale.get("info.users"), formatNumber(totalUsers), locale.get("info.uptime"), formatUptime(uptime),
                locale.get("info.performance_section"),
                locale.get("info.ping"), shard.getGatewayPing(), locale.get("info.servers"), formatNumber(shard.getGuilds().size()), locale.get("info.shards"), shard.getShardInfo().getShardTotal(),
                locale.get("info.memory"), formatBytes(usedMem), formatBytes(maxMem), (double) usedMem / maxMem * 100,
                Emoji.createProgressBar(usedMem, maxMem, 10),
                locale.get("info.system_section"),
                locale.get("info.cpu"), OS_BEAN.getAvailableProcessors(), locale.get("info.system_load"), getSystemLoad(), locale.get("info.java"), System.getProperty("java.version").split("\\.")[0]);

        return Container.of(
                header,
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(statsText),
                Separator.createDivider(Separator.Spacing.SMALL),
                ActionRow.of(
                        Button.link("https://discord.gg/Kr7qvutZ4N", locale.get("ui.support")),
                        Button.link("https://www.buymeacoffee.com/HashyDev", locale.get("ui.donate")),
                        Button.secondary("refresh_stats", "🔄 " + locale.get("ui.refresh"))
                )
        ).withAccentColor(PRIMARY_COLOR);
    }

    private Container createUserInfo(String userId, String username, String avatarUrl, LocaleManager locale) {
        User user = Main.getUserService().getOrCreateUser(userId);
        int favUsed = user.getFavorites().size();
        int favLimit = user.getFavoriteLimit();
        String badges = (user.isAdmin() ? "🛡️ " : "") + (user.isPremium() ? "👑 " : "");

        Section header = Section.of(
                Thumbnail.fromUrl(avatarUrl != null ? avatarUrl : DEFAULT_AVATAR),
                TextDisplay.of("## 👤 " + username + badges),
                TextDisplay.of("**" + getAccountLevel(user, locale) + " " + locale.get("info.account_type", "Account") + "**")
        );

        String infoText = String.format("""
                ### 🎨 %s
                **%s:** `%s` • **%s:** `%s`
                
                ### ❤️ %s
                **%s:** `%d/%d` (%d %s)
                %s""",
                locale.get("info.statistics"),
                locale.get("info.images_generated"), formatNumber(user.getImagesGenerated()), locale.get("info.usage_level"), getUsageLevel(user.getImagesGenerated(), locale),
                locale.get("info.favorites_count"),
                locale.get("info.slots"), favUsed, favLimit, favLimit - favUsed, locale.get("info.remaining"),
                Emoji.createProgressBar(favUsed, favLimit, 10));

        return Container.of(
                header,
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(infoText),
                Separator.createDivider(Separator.Spacing.SMALL),
                ActionRow.of(
                        Button.link("https://discord.gg/Kr7qvutZ4N", locale.get("ui.support")),
                        Button.secondary("refresh_profile", "🔄 " + locale.get("ui.refresh"))
                )
        ).withAccentColor(getUserColor(user));
    }

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

    private String getUsageLevel(long images, LocaleManager locale) {
        if (images >= 1000) return locale.get("info.level_super");
        if (images >= 500) return locale.get("info.level_power");
        if (images >= 100) return locale.get("info.level_regular");
        if (images >= 10) return locale.get("info.level_casual");
        return locale.get("info.level_beginner");
    }

    private String getAccountLevel(User user, LocaleManager locale) {
        if (user.isAdmin()) return locale.get("info.level_admin");
        if (user.isPremium()) return locale.get("info.level_premium");
        return locale.get("info.level_standard");
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