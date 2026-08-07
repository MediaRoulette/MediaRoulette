package dev.mediaroulette.model;

import java.time.Instant;
import dev.mediaroulette.domain.economy.BotInventoryItem;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a giveaway event
 */
public class Giveaway {
    private String id;
    private String title;
    private String description;
    private String channelId;
    private String messageId;
    private String hostId; // Admin who created the giveaway
    private BotInventoryItem prize;
    private Instant startTime;
    private Instant endTime;
    private int maxEntries; // -1 for unlimited
    private List<String> entries; // User IDs who entered
    private String winnerId;
    private boolean isActive;
    private boolean isCompleted;
    private String requirements; // JSON string for entry requirements
    
    public Giveaway() {
        this.id = generateId();
        this.startTime = Instant.now();
        this.entries = new ArrayList<>();
        this.isActive = true;
        this.isCompleted = false;
        this.maxEntries = -1;
    }
    
    public Giveaway(String title, String description, String channelId, String hostId, BotInventoryItem prize, Instant endTime) {
        this();
        this.title = title;
        this.description = description;
        this.channelId = channelId;
        this.hostId = hostId;
        this.prize = prize;
        this.endTime = endTime;
    }
    
    private String generateId() {
        return "GIVEAWAY_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }
    
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    
    public String getHostId() { return hostId; }
    public void setHostId(String hostId) { this.hostId = hostId; }
    
    public BotInventoryItem getPrize() { return prize; }
    public void setPrize(BotInventoryItem prize) { this.prize = prize; }
    
    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }
    
    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    
    public int getMaxEntries() { return maxEntries; }
    public void setMaxEntries(int maxEntries) { this.maxEntries = maxEntries; }
    
    public List<String> getEntries() { return entries; }
    public void setEntries(List<String> entries) { this.entries = entries; }
    
    public String getWinnerId() { return winnerId; }
    public void setWinnerId(String winnerId) { this.winnerId = winnerId; }
    
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    
    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean completed) { isCompleted = completed; }
    
    public String getRequirements() { return requirements; }
    public void setRequirements(String requirements) { this.requirements = requirements; }
    
    // Business logic methods
    public boolean isExpired() {
        return Instant.now().isAfter(endTime);
    }
    
    public boolean canEnter(String userId) {
        if (!isActive || isCompleted || isExpired()) {
            return false;
        }
        
        if (entries.contains(userId)) {
            return false; // Already entered
        }
        
        if (maxEntries > 0 && entries.size() >= maxEntries) {
            return false; // Max entries reached
        }
        
        return true;
    }
    
    public boolean addEntry(String userId) {
        if (canEnter(userId)) {
            entries.add(userId);
            return true;
        }
        return false;
    }
    
    public boolean removeEntry(String userId) {
        return entries.remove(userId);
    }
    
    public String selectRandomWinner() {
        if (entries.isEmpty()) {
            return null;
        }
        
        int randomIndex = (int) (Math.random() * entries.size());
        String winner = entries.get(randomIndex);
        this.winnerId = winner;
        this.isCompleted = true;
        this.isActive = false;
        
        return winner;
    }
    
    public long getTimeRemaining() {
        if (isExpired()) {
            return 0;
        }
        return endTime.getEpochSecond() - Instant.now().getEpochSecond();
    }
    
    public String getTimeRemainingFormatted() {
        long seconds = getTimeRemaining();
        if (seconds <= 0) {
            return "Ended";
        }
        
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, secs);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, secs);
        } else {
            return String.format("%ds", secs);
        }
    }
    
    public int getEntryCount() {
        return entries.size();
    }
    
    @Override
    public String toString() {
        return String.format("Giveaway{id='%s', title='%s', entries=%d, active=%s, completed=%s}", 
            id, title, getEntryCount(), isActive, isCompleted);
    }
}