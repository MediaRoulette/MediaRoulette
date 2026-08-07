package dev.mediaroulette.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Dictionary {
    private String id;
    private String name;
    private String description;
    private String createdBy; // User ID who created this dictionary
    private Instant createdAt;
    private Instant updatedAt;
    private List<String> words;
    private boolean isPublic; // Whether this dictionary can be shared
    private boolean isDefault; // Whether this is a system default dictionary
    private int usageCount; // How many times this dictionary has been used
    
    public Dictionary() {
        this.id = UUID.randomUUID().toString();
        this.words = new ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.isPublic = false;
        this.isDefault = false;
        this.usageCount = 0;
    }
    
    public Dictionary(String name, String description, String createdBy) {
        this();
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { 
        this.name = name;
        this.updatedAt = Instant.now();
    }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { 
        this.description = description;
        this.updatedAt = Instant.now();
    }
    
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    public List<String> getWords() { return words; }
    public void setWords(List<String> words) { 
        this.words = words;
        this.updatedAt = Instant.now();
    }
    
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { 
        this.isPublic = isPublic;
        this.updatedAt = Instant.now();
    }
    
    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean isDefault) { this.isDefault = isDefault; }
    
    public int getUsageCount() { return usageCount; }
    public void setUsageCount(int usageCount) { this.usageCount = usageCount; }
    
    // Business logic methods
    public void addWord(String word) {
        if (word != null && !word.trim().isEmpty() && !words.contains(word.trim().toLowerCase())) {
            words.add(word.trim().toLowerCase());
            this.updatedAt = Instant.now();
        }
    }
    
    public void addWords(List<String> newWords) {
        for (String word : newWords) {
            addWord(word);
        }
    }
    
    public boolean removeWord(String word) {
        if (words.remove(word.trim().toLowerCase())) {
            this.updatedAt = Instant.now();
            return true;
        }
        return false;
    }
    
    public void clearWords() {
        words.clear();
        this.updatedAt = Instant.now();
    }
    
    public String getRandomWord() {
        if (words.isEmpty()) {
            return null;
        }
        return words.get((int) (Math.random() * words.size()));
    }
    
    public void incrementUsage() {
        this.usageCount++;
    }
    
    public boolean canBeEditedBy(String userId) {
        return createdBy.equals(userId) || isDefault;
    }
    
    public boolean canBeViewedBy(String userId) {
        return isPublic || createdBy.equals(userId) || isDefault;
    }
    
    public int getWordCount() {
        return words.size();
    }
    
    @Override
    public String toString() {
        return String.format("Dictionary{id='%s', name='%s', words=%d, public=%s}", 
                id, name, words.size(), isPublic);
    }
}