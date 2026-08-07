package dev.mediaroulette.domain.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UsageStats {
    public static final int MAX_CUSTOM_SUBREDDITS = 50;
    
    private String userId;
    private Map<String, Long> sourceUsageCount;
    private Map<String, Long> commandUsageCount;
    private List<String> customSubreddits;
    private Map<String, Integer> subredditUsageCount;
    private Map<String, List<String>> customQueries;
    private long totalCommandsUsed;

    public UsageStats() {}

    public UsageStats(String userId) {
        this.userId = userId;
        this.sourceUsageCount = new HashMap<>();
        this.commandUsageCount = new HashMap<>();
        this.customSubreddits = new ArrayList<>();
        this.subredditUsageCount = new HashMap<>();
        this.customQueries = new HashMap<>();
        this.totalCommandsUsed = 0;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public Map<String, Long> getSourceUsageCount() { return sourceUsageCount != null ? sourceUsageCount : new HashMap<>(); }
    public void setSourceUsageCount(Map<String, Long> sourceUsageCount) { this.sourceUsageCount = sourceUsageCount; }
    
    public void incrementSourceUsage(String source) {
        if (sourceUsageCount == null) sourceUsageCount = new HashMap<>();
        sourceUsageCount.put(source, sourceUsageCount.getOrDefault(source, 0L) + 1);
    }
    
    public Map<String, Long> getCommandUsageCount() { return commandUsageCount != null ? commandUsageCount : new HashMap<>(); }
    public void setCommandUsageCount(Map<String, Long> commandUsageCount) { this.commandUsageCount = commandUsageCount; }
    
    public void incrementCommandUsage(String command) {
        if (commandUsageCount == null) commandUsageCount = new HashMap<>();
        commandUsageCount.put(command, commandUsageCount.getOrDefault(command, 0L) + 1);
        totalCommandsUsed++;
    }
    
    public List<String> getCustomSubreddits() { return customSubreddits != null ? customSubreddits : new ArrayList<>(); }
    public void setCustomSubreddits(List<String> customSubreddits) { this.customSubreddits = customSubreddits; }
    
    public void addCustomSubreddit(String subreddit) {
        if (customSubreddits == null) customSubreddits = new ArrayList<>();
        customSubreddits.remove(subreddit);
        customSubreddits.add(0, subreddit);
        if (customSubreddits.size() > MAX_CUSTOM_SUBREDDITS) {
            customSubreddits = customSubreddits.subList(0, MAX_CUSTOM_SUBREDDITS);
        }
        incrementSubredditUsage(subreddit);
    }
    
    public Map<String, Integer> getSubredditUsageCount() { return subredditUsageCount != null ? subredditUsageCount : new HashMap<>(); }
    public void setSubredditUsageCount(Map<String, Integer> subredditUsageCount) { this.subredditUsageCount = subredditUsageCount; }
    
    public void incrementSubredditUsage(String subreddit) {
        if (subredditUsageCount == null) subredditUsageCount = new HashMap<>();
        subredditUsageCount.put(subreddit, subredditUsageCount.getOrDefault(subreddit, 0) + 1);
    }
    
    public List<String> getTopSubreddits(int limit) {
        if (subredditUsageCount == null) return new ArrayList<>();
        return subredditUsageCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toList());
    }
    
    public long getTotalCommandsUsed() { return totalCommandsUsed; }
    public void setTotalCommandsUsed(long totalCommandsUsed) { this.totalCommandsUsed = totalCommandsUsed; }
    
    public String getMostUsedSource() {
        if (sourceUsageCount == null || sourceUsageCount.isEmpty()) return "None";
        return sourceUsageCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("None");
    }
    
    public String getMostUsedCommand() {
        if (commandUsageCount == null || commandUsageCount.isEmpty()) return "None";
        return commandUsageCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("None");
    }
    
    public Map<String, List<String>> getCustomQueries() { return customQueries != null ? customQueries : new HashMap<>(); }
    public void setCustomQueries(Map<String, List<String>> customQueries) { this.customQueries = customQueries; }
    
    public List<String> getCustomQueries(String service) {
        if (customQueries == null) return new ArrayList<>();
        return customQueries.getOrDefault(service, new ArrayList<>());
    }
    
    public void addCustomQuery(String service, String query) {
        if (customQueries == null) customQueries = new HashMap<>();
        List<String> serviceQueries = customQueries.computeIfAbsent(service, k -> new ArrayList<>());
        serviceQueries.remove(query);
        serviceQueries.add(0, query);
        if (serviceQueries.size() > MAX_CUSTOM_SUBREDDITS) {
            serviceQueries = serviceQueries.subList(0, MAX_CUSTOM_SUBREDDITS);
            customQueries.put(service, serviceQueries);
        }
    }
}
