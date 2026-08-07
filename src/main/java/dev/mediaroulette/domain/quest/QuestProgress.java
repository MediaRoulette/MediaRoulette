package dev.mediaroulette.domain.quest;

import java.util.ArrayList;
import java.util.List;

public class QuestProgress {
    private String userId;
    private List<Quest> dailyQuests;
    private java.time.LocalDate lastQuestReset;
    private long totalQuestsCompleted;
    private long questsCompletedToday;
    private java.time.LocalDate lastQuestCompletionDate;

    public QuestProgress() {}

    public QuestProgress(String userId) {
        this.userId = userId;
        this.dailyQuests = new ArrayList<>();
        this.lastQuestReset = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        this.totalQuestsCompleted = 0;
        this.questsCompletedToday = 0;
        this.lastQuestCompletionDate = null;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public List<Quest> getDailyQuests() { return dailyQuests; }
    public void setDailyQuests(List<Quest> dailyQuests) { this.dailyQuests = dailyQuests; }
    
    public java.time.LocalDate getLastQuestReset() { return lastQuestReset; }
    public void setLastQuestReset(java.time.LocalDate lastQuestReset) { this.lastQuestReset = lastQuestReset; }
    
    public long getTotalQuestsCompleted() { return totalQuestsCompleted; }
    public void setTotalQuestsCompleted(long totalQuestsCompleted) { this.totalQuestsCompleted = totalQuestsCompleted; }
    
    public long getQuestsCompletedToday() { return questsCompletedToday; }
    public void setQuestsCompletedToday(long questsCompletedToday) { this.questsCompletedToday = questsCompletedToday; }
    
    public java.time.LocalDate getLastQuestCompletionDate() { return lastQuestCompletionDate; }
    public void setLastQuestCompletionDate(java.time.LocalDate lastQuestCompletionDate) { this.lastQuestCompletionDate = lastQuestCompletionDate; }

    public void addQuest(Quest quest) {
        this.dailyQuests.add(quest);
    }

    public void updateQuestProgress(Quest.QuestType questType, int amount) {
        for (Quest quest : dailyQuests) {
            if (quest.getType() == questType && !quest.isCompleted()) {
                quest.addProgress(amount);
            }
        }
    }

    public List<Quest> getCompletedQuests() {
        return dailyQuests.stream()
                .filter(Quest::isCompleted)
                .toList();
    }

    public List<Quest> getClaimableQuests() {
        return dailyQuests.stream()
                .filter(Quest::canClaim)
                .toList();
    }

    public boolean needsQuestReset() {
        return lastQuestReset == null || 
               !lastQuestReset.equals(java.time.LocalDate.now(java.time.ZoneOffset.UTC));
    }

    public void resetDailyQuests() {
        this.dailyQuests.clear();
        this.lastQuestReset = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
    }
}
