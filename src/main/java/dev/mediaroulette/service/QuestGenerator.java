package dev.mediaroulette.service;

import dev.mediaroulette.domain.quest.Quest;
import dev.mediaroulette.domain.quest.QuestProgress;
import dev.mediaroulette.domain.user.Account;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class QuestGenerator {
    private static final Random random = new Random();

    // Easy quest templates
    private static final List<QuestTemplate> EASY_QUESTS = Arrays.asList(
            new QuestTemplate(Quest.QuestType.GENERATE_IMAGES, "🎲 Image Explorer", "Generate {target} random images", "🎲", 3, 5),
            new QuestTemplate(Quest.QuestType.FAVORITE_IMAGES, "⭐ Collector", "Add {target} images to your favorites", "⭐", 2, 3),
            new QuestTemplate(Quest.QuestType.USE_SPECIFIC_SOURCE, "🌐 Source Explorer", "Use the Reddit source {target} times", "🌐", 2, 4),
            new QuestTemplate(Quest.QuestType.CHANGE_THEME, "🎨 Style Changer", "Change your theme {target} time(s)", "🎨", 1, 2),
            new QuestTemplate(Quest.QuestType.RATE_IMAGES, "👍 Image Rater", "Rate {target} images as safe or NSFW", "👍", 3, 5)
    );

    // Hard quest templates
    private static final List<QuestTemplate> HARD_QUESTS = Arrays.asList(
            new QuestTemplate(Quest.QuestType.GENERATE_IMAGES, "🚀 Power User", "Generate {target} random images in one day", "🚀", 15, 25),
            new QuestTemplate(Quest.QuestType.EXPLORE_SOURCES, "🗺️ Explorer", "Use {target} different image sources", "🗺️", 4, 6),
            new QuestTemplate(Quest.QuestType.FAVORITE_IMAGES, "💎 Curator", "Add {target} images to your favorites", "💎", 8, 12),
            new QuestTemplate(Quest.QuestType.CONSECUTIVE_DAYS, "🔥 Streak Master", "Use the bot for {target} consecutive days", "🔥", 3, 5),
            new QuestTemplate(Quest.QuestType.SOCIAL_INTERACTION, "👥 Social Butterfly", "Check {target} other users' balances", "👥", 3, 5)
    );

    // Premium quest templates
    private static final List<QuestTemplate> PREMIUM_QUESTS = Arrays.asList(
            new QuestTemplate(Quest.QuestType.GENERATE_IMAGES, "👑 Premium Explorer", "Generate {target} images using premium features", "👑", 20, 30),
            new QuestTemplate(Quest.QuestType.USE_SPECIFIC_SOURCE, "💎 Premium Collector", "Use premium-exclusive sources {target} times", "💎", 10, 15),
            new QuestTemplate(Quest.QuestType.EXPLORE_SOURCES, "🌟 Master Explorer", "Use ALL available image sources at least once", "🌟", 8, 10),
            new QuestTemplate(Quest.QuestType.FAVORITE_IMAGES, "🏆 Elite Curator", "Reach the maximum favorite limit", "🏆", 25, 50),
            new QuestTemplate(Quest.QuestType.RATE_IMAGES, "⚡ Premium Rater", "Rate {target} images with premium accuracy bonus", "⚡", 15, 25)
    );

    public static List<Quest> generateDailyQuests(Account userAccount, QuestProgress userQuest) {
        List<Quest> quests = new ArrayList<>();
        
        // Always add one easy quest
        quests.add(generateQuest(EASY_QUESTS, Quest.QuestDifficulty.EASY));
        
        // Always add one hard quest
        quests.add(generateQuest(HARD_QUESTS, Quest.QuestDifficulty.HARD));
        
        // Premium users get an additional premium quest
        if (userAccount.isPremium()) {
            quests.add(generateQuest(PREMIUM_QUESTS, Quest.QuestDifficulty.PREMIUM));
        }
        
        return quests;
    }

    private static Quest generateQuest(List<QuestTemplate> templates, Quest.QuestDifficulty difficulty) {
        QuestTemplate template = templates.get(random.nextInt(templates.size()));
        int targetValue = template.minTarget + random.nextInt(template.maxTarget - template.minTarget + 1);
        
        String title = template.title;
        String description = template.description.replace("{target}", String.valueOf(targetValue));
        
        return new Quest(template.type, difficulty, title, description, template.emoji, targetValue);
    }

    public static void updateQuestProgress(QuestProgress userQuest, Quest.QuestType questType, int amount) {
        QuestNotificationManager.updateQuestProgressWithNotification(userQuest, questType, amount, userQuest.getUserId());
    }

    public static void updateQuestProgress(QuestProgress userQuest, Quest.QuestType questType) {
        updateQuestProgress(userQuest, questType, 1);
    }

    // Helper method to update quest progress based on user actions
    public static void onImageGenerated(QuestProgress userQuest, String source) {
        updateQuestProgress(userQuest, Quest.QuestType.GENERATE_IMAGES);
        
        // Check if it's a specific source quest
        for (Quest quest : userQuest.getDailyQuests()) {
            if (quest.getType() == Quest.QuestType.USE_SPECIFIC_SOURCE && !quest.isCompleted()) {
                // This would need to be enhanced to check specific sources
                updateQuestProgress(userQuest, Quest.QuestType.USE_SPECIFIC_SOURCE);
                break;
            }
        }
    }

    public static void onImageFavorited(QuestProgress userQuest) {
        updateQuestProgress(userQuest, Quest.QuestType.FAVORITE_IMAGES);
    }

    public static void onThemeChanged(QuestProgress userQuest) {
        updateQuestProgress(userQuest, Quest.QuestType.CHANGE_THEME);
    }

    public static void onImageRated(QuestProgress userQuest) {
        updateQuestProgress(userQuest, Quest.QuestType.RATE_IMAGES);
    }

    public static void onBalanceChecked(QuestProgress userQuest, String targetUserId) {
        if (!userQuest.getUserId().equals(targetUserId)) {
            updateQuestProgress(userQuest, Quest.QuestType.SOCIAL_INTERACTION);
        }
    }

    private static class QuestTemplate {
        final Quest.QuestType type;
        final String title;
        final String description;
        final String emoji;
        final int minTarget;
        final int maxTarget;

        QuestTemplate(Quest.QuestType type, String title, String description, String emoji, int minTarget, int maxTarget) {
            this.type = type;
            this.title = title;
            this.description = description;
            this.emoji = emoji;
            this.minTarget = minTarget;
            this.maxTarget = maxTarget;
        }
    }
}