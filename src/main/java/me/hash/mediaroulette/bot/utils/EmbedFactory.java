package me.hash.mediaroulette.bot.utils;

import me.hash.mediaroulette.model.User;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

import java.awt.*;
import java.time.Instant;

public class EmbedFactory {

    // ===== COLOR CONSTANTS =====
    public static final Color SUCCESS_COLOR = new Color(87, 242, 135);
    public static final Color ERROR_COLOR = new Color(255, 107, 107);
    public static final Color WARNING_COLOR = new Color(255, 193, 7);
    public static final Color INFO_COLOR = new Color(52, 152, 219);
    public static final Color PRIMARY_COLOR = new Color(114, 137, 218);
    public static final Color PREMIUM_COLOR = new Color(138, 43, 226);
    public static final Color COIN_COLOR = new Color(255, 215, 0);
    public static final Color COOLDOWN_COLOR = new Color(255, 107, 107);

    public static EmbedBuilder createBase() {
        return new EmbedBuilder().setTimestamp(Instant.now());
    }

    public static EmbedBuilder createSuccess(String title, String description) {
        return createBase()
                .setTitle("✅ " + title)
                .setDescription(description)
                .setColor(SUCCESS_COLOR);
    }

    public static EmbedBuilder createError(String title, String description) {
        return createBase()
                .setTitle("❌ " + title)
                .setDescription(description)
                .setColor(ERROR_COLOR);
    }

    public static EmbedBuilder createWarning(String title, String description) {
        return createBase()
                .setTitle("⚠️ " + title)
                .setDescription(description)
                .setColor(WARNING_COLOR);
    }

    public static EmbedBuilder createInfo(String title, String description) {
        return createBase()
                .setTitle("ℹ️ " + title)
                .setDescription(description)
                .setColor(INFO_COLOR);
    }

    public static EmbedBuilder createCooldown(String duration) {
        return createBase()
                .setTitle("⏰ Slow Down!")
                .setDescription("Please wait **" + duration + "** before using this command again.")
                .setColor(COOLDOWN_COLOR);
    }

    public static EmbedBuilder createLoading(String title, String description) {
        return createBase()
                .setTitle("⏳ " + title)
                .setDescription(description)
                .setColor(INFO_COLOR);
    }

    public static EmbedBuilder createEconomy(String title, String description, boolean isPremium) {
        return createBase()
                .setTitle("💰 " + title)
                .setDescription(description)
                .setColor(isPremium ? PREMIUM_COLOR : COIN_COLOR);
    }

    public static EmbedBuilder createUserEmbed(String title, String description,
                                               net.dv8tion.jda.api.entities.User discordUser,
                                               User botUser) {
        EmbedBuilder embed = createBase()
                .setTitle(title)
                .setDescription(description)
                .setColor(botUser != null && botUser.isPremium() ? PREMIUM_COLOR : PRIMARY_COLOR);

        if (discordUser.getAvatarUrl() != null) {
            embed.setThumbnail(discordUser.getAvatarUrl());
        }

        return embed;
    }

    public static EmbedBuilder createWithAuthor(String title, String description, Color color,
                                                net.dv8tion.jda.api.entities.User user) {
        return createBase()
                .setTitle(title)
                .setDescription(description)
                .setColor(color)
                .setAuthor(user.getName(), null, user.getEffectiveAvatarUrl());
    }

    public static EmbedBuilder addCodeField(EmbedBuilder embed, String name, String value, boolean inline) {
        return embed.addField(name, "```" + value + "```", inline);
    }

    public static EmbedBuilder addEmojiField(EmbedBuilder embed, String emoji, String name,
                                             String value, boolean inline) {
        return embed.addField(emoji + " " + name, value, inline);
    }

    public static EmbedBuilder addCoinField(EmbedBuilder embed, String name, long amount, boolean inline) {
        return addCodeField(embed, name, String.format("💰 %,d coins", amount), inline);
    }

    public static EmbedBuilder addCountField(EmbedBuilder embed, String name, long count,
                                             String unit, boolean inline) {
        return addCodeField(embed, name, String.format("%,d %s", count, unit), inline);
    }

    public static ActionRow createPaginationButtons(String baseId, int currentPage, int totalPages,
                                                    String additionalData) {
        String data = additionalData != null ? ":" + additionalData : "";

        return ActionRow.of(
                Button.primary(baseId + ":prev:" + (currentPage - 1) + data, "◀ Previous")
                        .withDisabled(currentPage <= 1),
                Button.primary(baseId + ":next:" + (currentPage + 1) + data, "Next ▶")
                        .withDisabled(currentPage >= totalPages),
                Button.secondary(baseId + ":refresh:" + currentPage + data, "🔄 Refresh")
        );
    }

    public static EmbedBuilder addPaginationFooter(EmbedBuilder embed, int currentPage, int totalPages,
                                                   int totalItems) {
        return embed.setFooter(String.format("Page %d/%d • %d items total",
                currentPage, totalPages, totalItems), null);
    }
}