package me.hash.mediaroulette.utils;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.model.User;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.Interaction;

import java.awt.Color;
import java.time.Instant;

/**
 * Utility class to check and enforce maintenance mode
 */
public class MaintenanceChecker {
    
    /**
     * Check if the bot is in maintenance mode and if the user should be blocked
     * @param event The interaction event
     * @return true if the command should be blocked, false if it should proceed
     */
    public static boolean isMaintenanceBlocked(Interaction event) {
        LocalConfig config = LocalConfig.getInstance();
        
        // If maintenance mode is disabled, allow all commands
        if (!config.getMaintenanceMode()) {
            return false;
        }
        
        // Check if user is admin - admins can use commands during maintenance
        User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
        if (user.isAdmin()) {
            return false; // Allow admins to use commands during maintenance
        }
        
        // Block non-admin users during maintenance
        return true;
    }
    
    /**
     * Send maintenance mode message to user
     * @param event The slash command interaction event
     */
    public static void sendMaintenanceMessage(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔧 Maintenance Mode")
                .setDescription("The bot is currently under maintenance. Please try again later.\n\n" +
                               "We're working hard to improve your experience!")
                .setColor(new Color(255, 165, 0)) // Orange color
                .setTimestamp(Instant.now())
                .setFooter("Thank you for your patience", null);
        
        if (event.isAcknowledged()) {
            event.getHook().sendMessageEmbeds(embed.build()).setEphemeral(true).queue();
        } else {
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        }
    }
    
    /**
     * Send maintenance mode message to user for button interactions
     * @param event The button interaction event
     */
    public static void sendMaintenanceMessage(ButtonInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔧 Maintenance Mode")
                .setDescription("The bot is currently under maintenance. Please try again later.\n\n" +
                               "We're working hard to improve your experience!")
                .setColor(new Color(255, 165, 0)) // Orange color
                .setTimestamp(Instant.now())
                .setFooter("Thank you for your patience", null);
        
        if (event.isAcknowledged()) {
            event.getHook().sendMessageEmbeds(embed.build()).setEphemeral(true).queue();
        } else {
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        }
    }
    
    /**
     * Check if a specific command should be exempt from maintenance mode
     * Some commands like /admin should always work for admins
     * @param commandName The name of the command
     * @return true if the command should be exempt from maintenance checks
     */
    public static boolean isExemptCommand(String commandName) {
        return switch (commandName.toLowerCase()) {
            case "admin" -> true; // Admin commands should always work
            case "support" -> true; // Support command should work during maintenance
            case "info" -> true; // Info command should work during maintenance
            default -> false;
        };
    }
}