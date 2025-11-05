package me.hash.mediaroulette.utils;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.model.Quest;
import me.hash.mediaroulette.model.Transaction;
import me.hash.mediaroulette.model.User;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class QuestNotificationManager {
    private static final Logger logger = LoggerFactory.getLogger(QuestNotificationManager.class);

    private static final Color QUEST_COMPLETE_COLOR = new Color(255, 215, 0); // Gold
    private static final Color SUCCESS_COLOR = new Color(87, 242, 135); // Green
    private static final NumberFormat FORMATTER = NumberFormat.getInstance(Locale.US);

    /**
     * Handles quest completion notification and auto-claiming
     * This should be called whenever quest progress is updated
     */
    public static void handleQuestCompletion(User user, String userId) {
        List<Quest> newlyCompletedQuests = user.getDailyQuests().stream()
                .filter(quest -> quest.isCompleted() && !quest.isClaimed())
                .toList();
        
        if (newlyCompletedQuests.isEmpty()) {
            return;
        }
        
        // Auto-claim all completed quests
        long totalReward = 0;
        for (Quest quest : newlyCompletedQuests) {
            Transaction transaction = user.claimQuestReward(quest);
            if (transaction != null) {
                totalReward += transaction.getAmount();
            }
        }
        
        // Save user data
        Main.getUserService().updateUser(user);
        
        // Send notification
        if (totalReward > 0) {
            sendQuestCompletionNotification(userId, newlyCompletedQuests, totalReward);
        }
    }
    
    /**
     * Sends a DM notification about quest completion, falls back to ephemeral message if DM fails
     */
    private static void sendQuestCompletionNotification(String userId, List<Quest> completedQuests, long totalReward) {
        if (Bot.getShardManager() == null) {
            return;
        }
        
        MessageEmbed embed = createQuestCompletionEmbed(completedQuests, totalReward);
        
        // Try to send DM first
        Bot.getShardManager().retrieveUserById(userId).queue(
            user -> {
                user.openPrivateChannel().queue(
                    privateChannel -> {
                        privateChannel.sendMessageEmbeds(embed).queue(
                            success -> {
                                // DM Sent!
                            },
                            dmError -> {
                                // DM failed, but we can't send ephemeral here without interaction context
                                logger.error("Failed to send quest completion DM to user: {} - {}", userId, dmError.getMessage());
                            }
                        );
                    },
                    channelError -> {
                        logger.error("Failed to open private channel for user: {} - {}", userId, channelError.getMessage());
                    }
                );
            },
            userError -> {
                logger.error("Failed to retrieve user: {} - {}", userId, userError.getMessage());
            }
        );
    }
    
    /**
     * Sends an ephemeral message about quest completion (for when DM fails and we have interaction context)
     */
    public static void sendEphemeralQuestNotification(SlashCommandInteractionEvent event, List<Quest> completedQuests, long totalReward) {
        MessageEmbed embed = createQuestCompletionEmbed(completedQuests, totalReward);
        
        event.getHook().sendMessageEmbeds(embed).setEphemeral(true).queue(
            success -> {},
            error -> logger.error("Failed to send ephemeral quest notification: " + error.getMessage())
        );
    }
    
    /**
     * Sends an ephemeral message about quest completion (for button interactions)
     */
    public static void sendEphemeralQuestNotification(ButtonInteractionEvent event, List<Quest> completedQuests, long totalReward) {
        MessageEmbed embed = createQuestCompletionEmbed(completedQuests, totalReward);
        
        event.getHook().sendMessageEmbeds(embed).setEphemeral(true).queue(
            success -> {},
            error -> logger.error("Failed to send ephemeral quest notification: " + error.getMessage())
        );
    }
    
    /**
     * Creates the quest completion embed
     */
    private static MessageEmbed createQuestCompletionEmbed(List<Quest> completedQuests, long totalReward) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(QUEST_COMPLETE_COLOR);
        embed.setTitle("🎉 Quest(s) Completed!");
        
        if (completedQuests.size() == 1) {
            Quest quest = completedQuests.get(0);
            embed.setDescription(String.format(
                "**%s %s**\n" +
                "```%s```\n" +
                "**Reward:** %s coins (auto-claimed)\n" +
                "**Completed:** %s",
                quest.getEmoji(),
                quest.getTitle(),
                quest.getDescription(),
                FORMATTER.format(quest.getCoinReward()),
                quest.getCompletedAt() != null ? 
                    quest.getCompletedAt().atZone(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")) + " UTC" : 
                    "Just now"
            ));
        } else {
            StringBuilder description = new StringBuilder();
            description.append(String.format("You completed **%d quests** and earned **%s coins** total!\n\n", 
                completedQuests.size(), FORMATTER.format(totalReward)));
            
            for (Quest quest : completedQuests) {
                description.append(String.format("**%s %s** - %s coins\n", 
                    quest.getEmoji(), quest.getTitle(), FORMATTER.format(quest.getCoinReward())));
            }
            
            embed.setDescription(description.toString());
        }
        
        embed.addField("💰 Total Reward", FORMATTER.format(totalReward) + " coins", true);
        embed.addField("✅ Status", "Auto-claimed", true);
        embed.addField("📊 Progress", "All rewards added to your balance!", false);
        
        embed.setFooter("Keep completing quests daily for maximum earnings!", null);
        embed.setTimestamp(java.time.Instant.now());
        
        return embed.build();
    }
    
    /**
     * Enhanced quest progress update with auto-completion handling
     */
    public static void updateQuestProgressWithNotification(User user, Quest.QuestType questType, int amount, String userId) {
        // Store quests that were completed before the update
        List<String> previouslyCompletedQuestIds = user.getDailyQuests().stream()
                .filter(Quest::isCompleted)
                .map(Quest::getQuestId)
                .toList();
        
        // Update quest progress
        user.updateQuestProgress(questType, amount);
        
        // Find newly completed quests
        List<Quest> newlyCompletedQuests = user.getDailyQuests().stream()
                .filter(quest -> quest.isCompleted() && !previouslyCompletedQuestIds.contains(quest.getQuestId()))
                .toList();
        
        // Handle completion if there are newly completed quests
        if (!newlyCompletedQuests.isEmpty()) {
            handleQuestCompletion(user, userId);
        }
    }
    
    /**
     * Convenience method for single progress increment
     */
    public static void updateQuestProgressWithNotification(User user, Quest.QuestType questType, String userId) {
        updateQuestProgressWithNotification(user, questType, 1, userId);
    }
}