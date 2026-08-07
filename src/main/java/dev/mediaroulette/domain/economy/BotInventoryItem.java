package dev.mediaroulette.domain.economy;

import java.time.Instant;

/**
 * Represents an item in the bot's inventory for giveaways and rewards
 */
public class BotInventoryItem {
    private String id;
    private String name;
    private String description;
    private String type; // "discord_nitro", "premium_voucher", "boost", "coins", etc.
    private String rarity; // "common", "rare", "epic", "legendary"
    private String value; // For nitro: encrypted gift link, for coins: amount, etc.
    private boolean isActive; // Whether the item can be given away
    private Instant addedAt;
    private String addedBy; // Admin who added it
    private Instant expiresAt; // When the item expires (for nitro links)
    private String metadata; // Additional JSON data
    
    public BotInventoryItem() {
        this.id = generateId();
        this.addedAt = Instant.now();
        this.isActive = true;
    }
    
    public BotInventoryItem(String name, String description, String type, String rarity, String value, String addedBy) {
        this();
        this.name = name;
        this.description = description;
        this.type = type;
        this.rarity = rarity;
        this.value = value;
        this.addedBy = addedBy;
    }
    
    private String generateId() {
        return "BOT_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getRarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }
    
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    
    public Instant getAddedAt() { return addedAt; }
    public void setAddedAt(Instant addedAt) { this.addedAt = addedAt; }
    
    public String getAddedBy() { return addedBy; }
    public void setAddedBy(String addedBy) { this.addedBy = addedBy; }
    
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    
    // Business logic methods
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
    
    public boolean canBeGivenAway() {
        return isActive && !isExpired();
    }
    
    public String getRarityEmoji() {
        return switch (rarity.toLowerCase()) {
            case "common" -> "O";
            case "uncommon" -> "G";
            case "rare" -> "B";
            case "epic" -> "P";
            case "legendary" -> "Y";
            case "mythic" -> "R";
            default -> "X";
        };
    }
    
    public String getTypeEmoji() {
        return switch (type.toLowerCase()) {
            case "discord_nitro" -> "D";
            case "premium_voucher" -> "C";
            case "boost" -> "R";
            case "coins" -> "C";
            case "special" -> "S";
            default -> "G";
        };
    }
    
    public String getDisplayValue() {
        return switch (type.toLowerCase()) {
            case "discord_nitro" -> "Discord Nitro Gift";
            case "premium_voucher" -> "Premium Upgrade";
            case "boost" -> "Server Boost";
            case "coins" -> value + " Coins";
            default -> "Special Item";
        };
    }
    
    public void markAsUsed() {
        this.isActive = false;
    }
    
    @Override
    public String toString() {
        return String.format("%s %s %s - %s", 
            getRarityEmoji(), getTypeEmoji(), name, getDisplayValue());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BotInventoryItem that = (BotInventoryItem) obj;
        return id != null ? id.equals(that.id) : that.id == null;
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
