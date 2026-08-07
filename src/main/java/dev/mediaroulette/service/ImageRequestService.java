package dev.mediaroulette.service;

import dev.mediaroulette.Main;
import dev.mediaroulette.bot.utils.ErrorHandler;
import dev.mediaroulette.domain.user.Account;
import dev.mediaroulette.plugins.images.ImageSource;
import dev.mediaroulette.plugins.images.ImageSourceProvider;
import dev.mediaroulette.locale.LocaleManager;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ImageRequestService {

    private static ImageRequestService instance;

    public static synchronized ImageRequestService getInstance() {
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

    
    /**
     * Validate channel access with provider-aware NSFW checking.
     * SFW providers (YouTube, Picsum, Movies, TV Shows) can be used in any channel.
     * NSFW providers require NSFW channel or DM access.
     * 
     * @param event The slash command event
     * @param user The user making the request
     * @param provider The image source provider (null for unknown/legacy sources)
     * @return ValidationResult indicating whether access is allowed and error details if not
     */
    public ValidationResult validateChannelAccessWithResult(SlashCommandInteractionEvent event, Account userAccount, ImageSourceProvider provider) {
        // If provider is SFW, skip NSFW validation entirely
        if (provider != null && !provider.isNsfw()) {
            return ValidationResult.success();
        }
        
        ChannelType type = event.getChannelType();

        boolean isPrivate = type == ChannelType.PRIVATE;
        boolean isText = type == ChannelType.TEXT;
        boolean isNsfwText = isText && event.getChannel().asTextChannel().isNSFW();

        if (isPrivate && !userAccount.isNsfw()) {
            return ValidationResult.nsfwNotEnabledInDm();
        }

        if (isText) {
            if (isNsfwText && !userAccount.isNsfw()) {
                userAccount.setNsfw(true);
                Main.getAccountService().updateAccount(userAccount);
                return ValidationResult.success();
            }

            if (!isNsfwText) {
                // Return source-specific error if we know which provider was denied
                if (provider != null) {
                    return ValidationResult.nsfwSourceDenied(provider);
                }
                return ValidationResult.nsfwChannelRequired();
            }
        }

        return ValidationResult.success();
    }
    
    /**
     * Legacy method for backward compatibility - validates and shows error if denied.
     * @deprecated Use {@link #validateChannelAccessWithResult} for more granular control
     */
    @Deprecated
    public boolean validateChannelAccess(SlashCommandInteractionEvent event, Account userAccount, ImageSourceProvider provider) {
        ValidationResult result = validateChannelAccessWithResult(event, userAccount, provider);
        if (!result.allowed()) {
            showValidationError(event, userAccount, result);
            return false;
        }
        return true;
    }
    
    /**
     * Display the appropriate error message for a denied validation result.
     */
    public void showValidationError(SlashCommandInteractionEvent event, Account userAccount, ValidationResult result) {
        LocaleManager locale = LocaleManager.getInstance(userAccount.getLocale());
        
        String title = locale.get(result.errorTitleKey());
        String description;
        
        if (result.hasSourceSpecificError()) {
            description = locale.get(result.errorDescriptionKey(), result.getDeniedProviderName());
        } else {
            description = locale.get(result.errorDescriptionKey());
        }
        
        ErrorHandler.sendErrorEmbed(event, title, description);
    }


    public void trackSourceUsage(String userId, String subcommand, String query) {
        if (query != null) {
            switch (subcommand) {
                case "reddit" -> {
                    dev.mediaroulette.domain.user.Preferences prefs = Main.getPreferencesService().getPreferences(userId);
                    prefs.addCustomSubreddit(query);
                    
                }
                case "google" -> {
                    dev.mediaroulette.domain.user.Preferences prefs = Main.getPreferencesService().getPreferences(userId);
                    prefs.addCustomQuery("google", query);
                    
                }
                case "tenor" -> {
                    dev.mediaroulette.domain.user.Preferences prefs = Main.getPreferencesService().getPreferences(userId);
                    prefs.addCustomQuery("tenor", query);
                    
                }
                case "4chan" -> {
                    dev.mediaroulette.domain.user.Preferences prefs = Main.getPreferencesService().getPreferences(userId);
                    prefs.addCustomQuery("4chan", query);
                    
                }
                case "urban" -> {
                    dev.mediaroulette.domain.user.Preferences prefs = Main.getPreferencesService().getPreferences(userId);
                    prefs.addCustomQuery("urban", query);
                    
                }
            }
        }
    }

    public void trackStats(String userId, String subcommand, Account userAccount) {
        if (Main.getStatsService() != null) {
            Main.getStatsService().trackImageGenerated(userId, subcommand, userAccount.isNsfw(), userAccount.isPremium());
            Main.getStatsService().trackCommandUsed(userId, "random", userAccount.isPremium());
            Main.getStatsService().trackUserActivity(userId, userAccount.isPremium());
        }
    }
}
