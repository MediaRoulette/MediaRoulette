package dev.mediaroulette.model;

import java.time.Instant;

/**
 * Represents the assignment of a dictionary to a specific source for a user
 */
public class DictionaryAssignment {
    private String userId;
    private String source; // e.g., "tenor", "reddit", "google"
    private String dictionaryId;
    private Instant assignedAt;
    private Instant lastUsed;
    private int usageCount;
    
    public DictionaryAssignment() {
        this.assignedAt = Instant.now();
        this.usageCount = 0;
    }
    
    public DictionaryAssignment(String userId, String source, String dictionaryId) {
        this();
        this.userId = userId;
        this.source = source;
        this.dictionaryId = dictionaryId;
    }
    
    // Getters and Setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    public String getDictionaryId() { return dictionaryId; }
    public void setDictionaryId(String dictionaryId) { this.dictionaryId = dictionaryId; }
    
    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }
    
    public Instant getLastUsed() { return lastUsed; }
    public void setLastUsed(Instant lastUsed) { this.lastUsed = lastUsed; }
    
    public int getUsageCount() { return usageCount; }
    public void setUsageCount(int usageCount) { this.usageCount = usageCount; }
    
    // Business logic methods
    public void recordUsage() {
        this.lastUsed = Instant.now();
        this.usageCount++;
    }
    
    public String getAssignmentKey() {
        return userId + ":" + source;
    }
    
    @Override
    public String toString() {
        return String.format("DictionaryAssignment{user='%s', source='%s', dictionary='%s', usage=%d}", 
                userId, source, dictionaryId, usageCount);
    }
}