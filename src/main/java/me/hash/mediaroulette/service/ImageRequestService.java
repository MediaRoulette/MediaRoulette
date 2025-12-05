package me.hash.mediaroulette.service;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.utils.errorHandler;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.plugins.Images.ImageSource;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ImageRequestService {

    private static ImageRequestService instance;

    public static ImageRequestService getInstance() {
        if (instance == null) {
            instance = new ImageRequestService();
        }
        return instance;
    }

    public CompletableFuture<Map<String, String>> fetchImage(String subcommand, SlashCommandInteractionEvent event, String query) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return ImageSource.handle(subcommand.toUpperCase(), event, query);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public boolean validateChannelAccess(SlashCommandInteractionEvent event, User user) {
        boolean isPrivateChannel = event.getChannelType() == ChannelType.PRIVATE;
        boolean isTextChannel = event.getChannelType() == ChannelType.TEXT;
        boolean isNsfwChannel = isTextChannel && event.getChannel().asTextChannel().isNSFW();

        if (isPrivateChannel && !user.isNsfw()) {
            errorHandler.sendErrorEmbed(event, "NSFW not enabled", "Please use the bot in an NSFW channel first");
            return false;
        }

        if (isTextChannel) {
            if (!user.isNsfw() && isNsfwChannel) {
                user.setNsfw(true);
            } else if (user.isNsfw() && !isNsfwChannel) {
                errorHandler.sendErrorEmbed(event, "Use in NSFW channel/DMs!", "Please use the bot in an NSFW channel or DMs!");
                return false;
            }
        }
        return true;
    }

    public void trackSourceUsage(String userId, String subcommand, String query) {
        if (query != null) {
            switch (subcommand) {
                case "reddit" -> {
                    Main.getUserService().addCustomSubreddit(userId, query);
                    Main.getUserService().trackSourceUsage(userId, "reddit");
                }
                case "google" -> {
                    Main.getUserService().addCustomQuery(userId, "google", query);
                    Main.getUserService().trackSourceUsage(userId, "google");
                }
                case "tenor" -> {
                    Main.getUserService().addCustomQuery(userId, "tenor", query);
                    Main.getUserService().trackSourceUsage(userId, "tenor");
                }
                case "4chan" -> {
                    Main.getUserService().addCustomQuery(userId, "4chan", query);
                    Main.getUserService().trackSourceUsage(userId, "4chan");
                }
            }
        } else {
            switch (subcommand) {
                case "all" -> Main.getUserService().trackSourceUsage(userId, "all");
                case "picsum" -> Main.getUserService().trackSourceUsage(userId, "picsum");
                case "imgur" -> Main.getUserService().trackSourceUsage(userId, "imgur");
                case "rulee34xxx" -> Main.getUserService().trackSourceUsage(userId, "rule34");
                case "movie" -> Main.getUserService().trackSourceUsage(userId, "tmdb-movie");
                case "tvshow" -> Main.getUserService().trackSourceUsage(userId, "tmdb-tv");
                case "youtube" -> Main.getUserService().trackSourceUsage(userId, "youtube");
                case "short" -> Main.getUserService().trackSourceUsage(userId, "youtube-shorts");
                case "urban" -> Main.getUserService().trackSourceUsage(userId, "urban-dictionary");
            }
        }
    }

    public void trackStats(String userId, String subcommand, User user) {
        if (Main.getStatsService() != null) {
            Main.getStatsService().trackImageGenerated(userId, subcommand, user.isNsfw(), user.isPremium());
            Main.getStatsService().trackCommandUsed(userId, "random", user.isPremium());
            Main.getStatsService().trackUserActivity(userId, user.isPremium());
        }
    }
}
