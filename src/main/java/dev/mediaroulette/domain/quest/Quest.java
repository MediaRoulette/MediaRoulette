package dev.mediaroulette.domain.quest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

public class Quest {
    public enum QuestType {
        GENERATE_IMAGES,
        USE_SPECIFIC_SOURCE,
        FAVORITE_IMAGES,
        CHANGE_THEME,
        CONSECUTIVE_DAYS,
        EXPLORE_SOURCES,
        RATE_IMAGES,
        SOCIAL_INTERACTION
    }

    public enum QuestDifficulty {
        EASY(50, 100),    // 50-100 coins
        HARD(150, 300),   // 150-300 coins
        PREMIUM(200, 500); // 200-500 coins (premium only)

        private final int minReward;
        private final int maxReward;

        QuestDifficulty(int minReward, int maxReward) {
            this.minReward = minReward;
            this.maxReward = maxReward;
        }

        public int getMinReward() { return minReward; }
        public int getMaxReward() { return maxReward; }
    }

    private String questId;
    private QuestType type;
    private QuestDifficulty difficulty;
    private String title;
    private String description;
    private String emoji;
    private int targetValue; // Target number to complete quest
    private int currentProgress; // Current progress
    private int coinReward;
    private boolean completed;
    private boolean claimed;
    private LocalDate assignedDate;
    private Instant completedAt;
    private String metadata; // JSON for additional quest data

    public Quest() {
        this.questId = generateQuestId();
        this.assignedDate = LocalDate.now(ZoneOffset.UTC);
        this.completed = false;
        this.claimed = false;
        this.currentProgress = 0;
    }

    public Quest(QuestType type, QuestDifficulty difficulty, String title, String description, String emoji, int targetValue) {
        this();
        this.type = type;
        this.difficulty = difficulty;
        this.title = title;
        this.description = description;
        this.emoji = emoji;
        this.targetValue = targetValue;
        this.coinReward = calculateReward(difficulty);
    }

    private String generateQuestId() {
        return "QUEST_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    private int calculateReward(QuestDifficulty difficulty) {
        int min = difficulty.getMinReward();
        int max = difficulty.getMaxReward();
        return min + (int)(Math.random() * (max - min + 1));
    }

    // Progress management
    public void addProgress(int amount) {
        if (!completed) {
            this.currentProgress = Math.min(this.currentProgress + amount, this.targetValue);
            if (this.currentProgress >= this.targetValue) {
                this.completed = true;
                this.completedAt = Instant.now();
            }
        }
    }

    public boolean canClaim() {
        return completed && !claimed;
    }

    public void claim() {
        if (canClaim()) {
            this.claimed = true;
        }
    }

    public double getProgressPercentage() {
        if (targetValue == 0) return 0.0;
        return Math.min(100.0, (double) currentProgress / targetValue * 100.0);
    }

    public String getProgressBar() {
        int totalBars = 10;
        int filledBars = (int) (getProgressPercentage() / 10);
        StringBuilder bar = new StringBuilder();
        
        for (int i = 0; i < totalBars; i++) {
            if (i < filledBars) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }
        
        return bar.toString();
    }

    public String getStatusEmoji() {
        if (claimed) return "✅";
        if (completed) return "🎁";
        return "⏳";
    }

    // Getters and Setters
    public String getQuestId() { return questId; }
    public void setQuestId(String questId) { this.questId = questId; }

    public QuestType getType() { return type; }
    public void setType(QuestType type) { this.type = type; }

    public QuestDifficulty getDifficulty() { return difficulty; }
    public void setDifficulty(QuestDifficulty difficulty) { this.difficulty = difficulty; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }

    public int getTargetValue() { return targetValue; }
    public void setTargetValue(int targetValue) { this.targetValue = targetValue; }

    public int getCurrentProgress() { return currentProgress; }
    public void setCurrentProgress(int currentProgress) { this.currentProgress = currentProgress; }

    public int getCoinReward() { return coinReward; }
    public void setCoinReward(int coinReward) { this.coinReward = coinReward; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public boolean isClaimed() { return claimed; }
    public void setClaimed(boolean claimed) { this.claimed = claimed; }

    public LocalDate getAssignedDate() { return assignedDate; }
    public void setAssignedDate(LocalDate assignedDate) { this.assignedDate = assignedDate; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    @Override
    public String toString() {
        return String.format("Quest{id='%s', title='%s', difficulty=%s, progress=%d/%d, completed=%s, claimed=%s}",
                questId, title, difficulty, currentProgress, targetValue, completed, claimed);
    }
}
