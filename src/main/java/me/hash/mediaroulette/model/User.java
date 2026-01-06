package me.hash.mediaroulette.model;

import me.hash.mediaroulette.exceptions.InvalidChancesException;
import me.hash.mediaroulette.exceptions.NoEnabledOptionsException;
import me.hash.mediaroulette.utils.user.ImageSelector;

import java.util.*;
import java.util.Comparator;

public class User {
    public static final int DEFAULT_FAVORITE_LIMIT = 25;
    public static final int MAX_CUSTOM_SUBREDDITS = 50;

    private String userId;
    private long imagesGenerated;
    private boolean nsfw;
    private boolean premium;
    private boolean admin;
    private List<Favorite> favorites;
    private Map<String, ImageOptions> imageOptions;
    private String locale; // locale support
    private String theme;
    private long coins; // User's currency balance
    private long totalCoinsEarned; // Total coins earned lifetime
    private long totalCoinsSpent; // Total coins spent lifetime
    private List<Quest> dailyQuests; // Current daily quests
    private List<Transaction> transactionHistory; // Transaction history for monitoring
    private java.time.LocalDate lastQuestReset; // Last time quests were reset
    private long totalQuestsCompleted; // Total number of quests completed lifetime
    private long questsCompletedToday; // Number of quests completed today
    private java.time.LocalDate lastQuestCompletionDate; // Last date a quest was completed
    private List<InventoryItem> inventory; // User's inventory items
    
    // Usage Statistics
    private Map<String, Long> sourceUsageCount; // Track usage count per source (reddit, imgur, etc.)
    private Map<String, Long> commandUsageCount; // Track usage count per command
    private List<String> customSubreddits; // User's custom subreddits for autocomplete
    private Map<String, Integer> subredditUsageCount; // Track how often each subreddit is used
    private Map<String, List<String>> customQueries; // User's custom queries per service (google, tenor, 4chan)
    private long totalCommandsUsed; // Total commands used by user
    private java.time.LocalDateTime lastActiveDate; // Last time user was active
    private java.time.LocalDateTime accountCreatedDate; // When the user account was created
    
    // Source-specific preferences (sort method, time range, etc.)
    private Map<String, SourcePreference> sourcePreferences;

    public User(String userId) {
        this.userId = userId;
        this.imagesGenerated = 0;
        this.nsfw = false;
        this.premium = false;
        this.admin = false;
        this.favorites = new ArrayList<>();
        this.imageOptions = new HashMap<>();
        this.locale = "en_US"; // default locale
        this.coins = 100; // Starting balance
        this.totalCoinsEarned = 100; // Starting coins count as earned
        this.totalCoinsSpent = 0;
        this.dailyQuests = new ArrayList<>();
        this.transactionHistory = new ArrayList<>();
        this.lastQuestReset = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        this.totalQuestsCompleted = 0;
        this.questsCompletedToday = 0;
        this.lastQuestCompletionDate = null;
        
        // Initialize usage statistics
        this.sourceUsageCount = new HashMap<>();
        this.commandUsageCount = new HashMap<>();
        this.customSubreddits = new ArrayList<>();
        this.subredditUsageCount = new HashMap<>();
        this.customQueries = new HashMap<>();
        this.totalCommandsUsed = 0;
        this.lastActiveDate = java.time.LocalDateTime.now();
        this.accountCreatedDate = java.time.LocalDateTime.now();
        this.inventory = new ArrayList<>();
        this.sourcePreferences = new HashMap<>();
    }

    // --- Getters and Setters ---
    public String getUserId() { return userId; }
    public long getImagesGenerated() { return imagesGenerated; }
    public void setImagesGenerated(long imagesGenerated) { this.imagesGenerated = imagesGenerated; }
    public boolean isNsfw() { return nsfw; }
    public void setNsfw(boolean nsfw) { this.nsfw = nsfw; }
    public boolean isPremium() { return premium; }
    public void setPremium(boolean premium) { this.premium = premium; }
    public boolean isAdmin() { return admin; }
    public void setAdmin(boolean admin) { this.admin = admin; }
    public List<Favorite> getFavorites() { return favorites; }
    public Map<String, ImageOptions> getImageOptionsMap() { return imageOptions; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    public long getCoins() { return coins; }
    public void setCoins(long coins) { this.coins = coins; }
    public long getTotalCoinsEarned() { return totalCoinsEarned; }
    public void setTotalCoinsEarned(long totalCoinsEarned) { this.totalCoinsEarned = totalCoinsEarned; }
    public long getTotalCoinsSpent() { return totalCoinsSpent; }
    public void setTotalCoinsSpent(long totalCoinsSpent) { this.totalCoinsSpent = totalCoinsSpent; }
    public List<Quest> getDailyQuests() { return dailyQuests; }
    public void setDailyQuests(List<Quest> dailyQuests) { this.dailyQuests = dailyQuests; }
    public List<Transaction> getTransactionHistory() { return transactionHistory; }
    public void setTransactionHistory(List<Transaction> transactionHistory) { this.transactionHistory = transactionHistory; }
    public java.time.LocalDate getLastQuestReset() { return lastQuestReset; }
    public void setLastQuestReset(java.time.LocalDate lastQuestReset) { this.lastQuestReset = lastQuestReset; }
    public long getTotalQuestsCompleted() { return totalQuestsCompleted; }
    public void setTotalQuestsCompleted(long totalQuestsCompleted) { this.totalQuestsCompleted = totalQuestsCompleted; }
    public long getQuestsCompletedToday() { return questsCompletedToday; }
    public void setQuestsCompletedToday(long questsCompletedToday) { this.questsCompletedToday = questsCompletedToday; }
    public java.time.LocalDate getLastQuestCompletionDate() { return lastQuestCompletionDate; }
    public void setLastQuestCompletionDate(java.time.LocalDate lastQuestCompletionDate) { this.lastQuestCompletionDate = lastQuestCompletionDate; }
    public List<InventoryItem> getInventory() { return inventory; }
    public void setInventory(List<InventoryItem> inventory) { this.inventory = inventory; }

    // --- Business Logic Methods ---
    public void incrementImagesGenerated() {
        this.imagesGenerated++;
        // Note: Coins are now earned through quests, not automatic generation
    }

    // --- Currency Management Methods ---
    public Transaction addCoins(long amount, Transaction.TransactionType type, String description) {
        return addCoins(amount, type, description, null);
    }

    public Transaction addCoins(long amount, Transaction.TransactionType type, String description, String adminId) {
        if (amount <= 0) return null;
        
        long balanceBefore = this.coins;
        this.coins += amount;
        this.totalCoinsEarned += amount;
        
        Transaction transaction = new Transaction(this.userId, type, amount, balanceBefore, description);
        if (adminId != null) {
            transaction.setAdminId(adminId);
        }
        
        addTransaction(transaction);
        return transaction;
    }

    public Transaction spendCoins(long amount, Transaction.TransactionType type, String description) {
        return spendCoins(amount, type, description, null);
    }

    public Transaction spendCoins(long amount, Transaction.TransactionType type, String description, String adminId) {
        if (amount <= 0 || this.coins < amount) return null;
        
        long balanceBefore = this.coins;
        this.coins -= amount;
        this.totalCoinsSpent += amount;
        
        Transaction transaction = new Transaction(this.userId, type, -amount, balanceBefore, description);
        if (adminId != null) {
            transaction.setAdminId(adminId);
        }
        
        addTransaction(transaction);
        return transaction;
    }

    public boolean canAfford(long amount) {
        return this.coins >= amount;
    }

    public long getNetWorth() {
        return this.totalCoinsEarned - this.totalCoinsSpent;
    }

    private void addTransaction(Transaction transaction) {
        this.transactionHistory.add(transaction);
        
        // Keep only last 100 transactions to prevent memory issues
        if (this.transactionHistory.size() > 100) {
            this.transactionHistory.remove(0);
        }
        
        // Flag suspicious transactions
        flagSuspiciousActivity(transaction);
    }

    private void flagSuspiciousActivity(Transaction transaction) {
        // Flag large transactions
        if (Math.abs(transaction.getAmount()) > 10000) {
            transaction.setFlagged(true);
        }
        
        // Flag rapid transactions (more than 10 in last minute)
        long oneMinuteAgo = System.currentTimeMillis() - 60000;
        long recentTransactions = transactionHistory.stream()
                .filter(t -> t.getTimestamp().toEpochMilli() > oneMinuteAgo)
                .count();
        
        if (recentTransactions > 10) {
            transaction.setFlagged(true);
        }
    }

    // --- Quest Management Methods ---
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

    public int getQuestLimit() {
        // Regular users get 2 quests (1 easy, 1 hard), premium users get 3 (1 easy, 1 hard, 1 premium)
        return isPremium() ? 3 : 2;
    }

    public Transaction claimQuestReward(Quest quest) {
        if (quest.canClaim()) {
            quest.claim();
            
            // Update quest completion tracking
            this.totalQuestsCompleted++;
            
            java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
            if (this.lastQuestCompletionDate == null || !this.lastQuestCompletionDate.equals(today)) {
                // Reset daily count if it's a new day
                this.questsCompletedToday = 1;
                this.lastQuestCompletionDate = today;
            } else {
                // Same day, increment count
                this.questsCompletedToday++;
            }
            
            return addCoins(quest.getCoinReward(), Transaction.TransactionType.QUEST_REWARD, 
                    "Claimed reward for quest: " + quest.getTitle());
        }
        return null;
    }

    public int getFavoriteLimit() {
        return premium ? DEFAULT_FAVORITE_LIMIT * 2 : DEFAULT_FAVORITE_LIMIT;
    }

    public void addFavorite(String description, String image, String type) {
        // Backward-compatible path: default title
        addFavorite("Favorite", description, image, type, null);
    }

    public void addFavorite(String title, String description, String image, String type, Integer accentColor) {
        if (favorites.size() >= getFavoriteLimit()) {
            // Log warning as needed – favorite limit reached.
            return;
        }
        int id = favorites.size();
        favorites.add(new Favorite(id, title, description, image, type, accentColor));
    }

    public void removeFavorite(int id) {
        if (id < 0 || id >= favorites.size()) return;
        favorites.remove(id);
        // Reassign IDs so they remain sequential.
        for (int i = id; i < favorites.size(); i++) {
            favorites.get(i).setId(i);
        }
    }

    /**
     * Update (or set) image options with new chances.
     */
    public void setChances(ImageOptions... options) {
        for (ImageOptions option : options) {
            imageOptions.put(option.getImageType(), option);
        }
    }

    public ImageOptions getImageOptions(String imageType) {
        return imageOptions.get(imageType);
    }

    /**
     * Uses the ImageSelector to pick an image option.
     * @return a map containing the selected image details.
     * @throws NoEnabledOptionsException if no enabled options exist.
     * @throws InvalidChancesException if the chance values are invalid.
     */
    public Map<String, String> getImage() throws NoEnabledOptionsException, InvalidChancesException, me.hash.mediaroulette.exceptions.InvalidChancesException, me.hash.mediaroulette.exceptions.NoEnabledOptionsException {
        ImageSelector selector = new ImageSelector(imageOptions);
        return selector.selectImage(this.userId);
    }

    // --- Inventory Management Methods ---
    public static final int MAX_INVENTORY_SIZE = 100;
    
    /**
     * Add an item to the user's inventory
     * @param item The item to add
     * @return true if added successfully, false if inventory is full
     */
    public boolean addInventoryItem(InventoryItem item) {
        if (inventory.size() >= MAX_INVENTORY_SIZE) {
            return false; // Inventory full
        }
        
        // Check if item is stackable and already exists
        if (item.isStackable()) {
            for (InventoryItem existingItem : inventory) {
                if (existingItem.getId().equals(item.getId())) {
                    existingItem.addQuantity(item.getQuantity());
                    return true;
                }
            }
        }
        
        // Add as new item
        inventory.add(item);
        return true;
    }
    
    /**
     * Remove an item from inventory by ID
     * @param itemId The ID of the item to remove
     * @param quantity The quantity to remove (for stackable items)
     * @return true if removed successfully
     */
    public boolean removeInventoryItem(String itemId, int quantity) {
        for (int i = 0; i < inventory.size(); i++) {
            InventoryItem item = inventory.get(i);
            if (item.getId().equals(itemId)) {
                if (item.getQuantity() <= quantity) {
                    // Remove entire item
                    inventory.remove(i);
                    return true;
                } else {
                    // Reduce quantity
                    return item.removeQuantity(quantity);
                }
            }
        }
        return false;
    }
    
    /**
     * Get an item from inventory by ID
     * @param itemId The ID of the item
     * @return The item if found, null otherwise
     */
    public InventoryItem getInventoryItem(String itemId) {
        return inventory.stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Check if user has a specific item
     * @param itemId The ID of the item
     * @param quantity The minimum quantity needed
     * @return true if user has the item with sufficient quantity
     */
    public boolean hasInventoryItem(String itemId, int quantity) {
        InventoryItem item = getInventoryItem(itemId);
        return item != null && item.getQuantity() >= quantity;
    }
    
    /**
     * Get inventory items by type
     * @param type The type of items to get
     * @return List of items of the specified type
     */
    public List<InventoryItem> getInventoryItemsByType(String type) {
        return inventory.stream()
                .filter(item -> type.equals(item.getType()))
                .toList();
    }
    
    /**
     * Get inventory items by rarity
     * @param rarity The rarity of items to get
     * @return List of items of the specified rarity
     */
    public List<InventoryItem> getInventoryItemsByRarity(String rarity) {
        return inventory.stream()
                .filter(item -> rarity.equals(item.getRarity()))
                .toList();
    }
    
    /**
     * Get total number of items in inventory (counting quantities)
     * @return Total item count
     */
    public int getTotalInventoryItems() {
        return inventory.stream()
                .mapToInt(InventoryItem::getQuantity)
                .sum();
    }
    
    /**
     * Get number of unique items in inventory
     * @return Number of unique items
     */
    public int getUniqueInventoryItems() {
        return inventory.size();
    }
    
    /**
     * Check if inventory has space for more items
     * @return true if inventory has space
     */
    public boolean hasInventorySpace() {
        return inventory.size() < MAX_INVENTORY_SIZE;
    }
    
    /**
     * Get available inventory space
     * @return Number of slots available
     */
    public int getAvailableInventorySpace() {
        return MAX_INVENTORY_SIZE - inventory.size();
    }
    
    /**
     * Sort inventory by a specific criteria
     * @param sortBy "name", "type", "rarity", "quantity", "acquired"
     */
    public void sortInventory(String sortBy) {
        switch (sortBy.toLowerCase()) {
            case "name" -> inventory.sort(Comparator.comparing(InventoryItem::getName));
            case "type" -> inventory.sort(Comparator.comparing(InventoryItem::getType));
            case "rarity" -> inventory.sort(Comparator.comparing(InventoryItem::getRarity));
            case "quantity" -> inventory.sort(Comparator.comparing(InventoryItem::getQuantity).reversed());
            case "acquired" -> inventory.sort(Comparator.comparing(InventoryItem::getAcquiredAt).reversed());
            default -> inventory.sort(Comparator.comparing(InventoryItem::getName));
        }
    }
    
    /**
     * Clear all items from inventory
     */
    public void clearInventory() {
        inventory.clear();
    }
    
    // ===== USAGE STATISTICS METHODS =====
    
    public Map<String, Long> getSourceUsageCount() {
        return sourceUsageCount != null ? sourceUsageCount : new HashMap<>();
    }
    
    public void setSourceUsageCount(Map<String, Long> sourceUsageCount) {
        this.sourceUsageCount = sourceUsageCount;
    }
    
    public void incrementSourceUsage(String source) {
        if (sourceUsageCount == null) sourceUsageCount = new HashMap<>();
        sourceUsageCount.put(source, sourceUsageCount.getOrDefault(source, 0L) + 1);
        updateLastActive();
    }
    
    public Map<String, Long> getCommandUsageCount() {
        return commandUsageCount != null ? commandUsageCount : new HashMap<>();
    }
    
    public void setCommandUsageCount(Map<String, Long> commandUsageCount) {
        this.commandUsageCount = commandUsageCount;
    }
    
    public void incrementCommandUsage(String command) {
        if (commandUsageCount == null) commandUsageCount = new HashMap<>();
        commandUsageCount.put(command, commandUsageCount.getOrDefault(command, 0L) + 1);
        totalCommandsUsed++;
        updateLastActive();
    }
    
    public List<String> getCustomSubreddits() {
        return customSubreddits != null ? customSubreddits : new ArrayList<>();
    }
    
    public void setCustomSubreddits(List<String> customSubreddits) {
        this.customSubreddits = customSubreddits;
    }
    
    public void addCustomSubreddit(String subreddit) {
        if (customSubreddits == null) customSubreddits = new ArrayList<>();
        
        // Remove if already exists to avoid duplicates
        customSubreddits.remove(subreddit);
        
        // Add to front of list (most recently used)
        customSubreddits.add(0, subreddit);
        
        // Limit to MAX_CUSTOM_SUBREDDITS
        if (customSubreddits.size() > MAX_CUSTOM_SUBREDDITS) {
            customSubreddits = customSubreddits.subList(0, MAX_CUSTOM_SUBREDDITS);
        }
        
        // Track usage
        incrementSubredditUsage(subreddit);
    }
    
    public Map<String, Integer> getSubredditUsageCount() {
        return subredditUsageCount != null ? subredditUsageCount : new HashMap<>();
    }
    
    public void setSubredditUsageCount(Map<String, Integer> subredditUsageCount) {
        this.subredditUsageCount = subredditUsageCount;
    }
    
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
    
    public long getTotalCommandsUsed() {
        return totalCommandsUsed;
    }
    
    public void setTotalCommandsUsed(long totalCommandsUsed) {
        this.totalCommandsUsed = totalCommandsUsed;
    }
    
    public java.time.LocalDateTime getLastActiveDate() {
        return lastActiveDate;
    }
    
    public void setLastActiveDate(java.time.LocalDateTime lastActiveDate) {
        this.lastActiveDate = lastActiveDate;
    }
    
    public void updateLastActive() {
        this.lastActiveDate = java.time.LocalDateTime.now();
    }
    
    public java.time.LocalDateTime getAccountCreatedDate() {
        return accountCreatedDate;
    }
    
    public void setAccountCreatedDate(java.time.LocalDateTime accountCreatedDate) {
        this.accountCreatedDate = accountCreatedDate;
    }
    
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
    
    // ===== CUSTOM QUERIES METHODS =====
    
    public Map<String, List<String>> getCustomQueries() {
        return customQueries != null ? customQueries : new HashMap<>();
    }
    
    public void setCustomQueries(Map<String, List<String>> customQueries) {
        this.customQueries = customQueries;
    }
    
    public List<String> getCustomQueries(String service) {
        if (customQueries == null) return new ArrayList<>();
        return customQueries.getOrDefault(service, new ArrayList<>());
    }
    
    public void addCustomQuery(String service, String query) {
        if (customQueries == null) customQueries = new HashMap<>();
        
        List<String> serviceQueries = customQueries.computeIfAbsent(service, k -> new ArrayList<>());
        
        // Remove if already exists to avoid duplicates
        serviceQueries.remove(query);
        
        // Add to front of list (most recently used)
        serviceQueries.add(0, query);
        
        // Limit to MAX_CUSTOM_SUBREDDITS per service
        if (serviceQueries.size() > MAX_CUSTOM_SUBREDDITS) {
            serviceQueries = serviceQueries.subList(0, MAX_CUSTOM_SUBREDDITS);
            customQueries.put(service, serviceQueries);
        }
        
        updateLastActive();
    }
    
    // ===== SOURCE PREFERENCES METHODS =====
    
    /**
     * Get all source preferences.
     */
    public Map<String, SourcePreference> getSourcePreferences() {
        return sourcePreferences != null ? sourcePreferences : new HashMap<>();
    }
    
    /**
     * Set all source preferences (used for MongoDB deserialization).
     */
    public void setSourcePreferences(Map<String, SourcePreference> sourcePreferences) {
        this.sourcePreferences = sourcePreferences;
    }
    
    /**
     * Get preferences for a specific source.
     * @param source The source name (e.g., "reddit")
     * @return The SourcePreference, or a new empty one if not set
     */
    public SourcePreference getSourcePreference(String source) {
        if (sourcePreferences == null) sourcePreferences = new HashMap<>();
        return sourcePreferences.computeIfAbsent(source, SourcePreference::new);
    }
    
    /**
     * Set preferences for a specific source.
     */
    public void setSourcePreference(String source, SourcePreference preference) {
        if (sourcePreferences == null) sourcePreferences = new HashMap<>();
        sourcePreferences.put(source, preference);
    }
    
    /**
     * Get a specific setting value for a source, with a default.
     * @param source The source name
     * @param key The setting key
     * @param defaultValue The default value if not set
     * @return The setting value or default
     */
    public <T> T getSourceSetting(String source, String key, T defaultValue) {
        SourcePreference pref = sourcePreferences != null ? sourcePreferences.get(source) : null;
        return pref != null ? pref.getSetting(key, defaultValue) : defaultValue;
    }
    
    /**
     * Set a specific setting value for a source.
     * @param source The source name  
     * @param key The setting key
     * @param value The value to set
     */
    public void setSourceSetting(String source, String key, Object value) {
        getSourcePreference(source).setSetting(key, value);
    }
}
