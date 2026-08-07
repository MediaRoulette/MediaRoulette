package dev.mediaroulette.domain.economy;

import java.time.Instant;

/**
 * Represents an item in a user's inventory
 */
public class InventoryItem {
    private String id;
    private String name;
    private String description;
    private String type; // "consumable", "collectible", "tool", etc.
    private String rarity; // "common", "rare", "epic", "legendary"
    private int quantity;
    private Instant acquiredAt;
    private String source; // Where the item was obtained from
    
    public InventoryItem() {
        this.acquiredAt = Instant.now();
        this.quantity = 1;
    }
    
    public InventoryItem(String id, String name, String description, String type, String rarity) {
        this();
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.rarity = rarity;
    }
    
    public InventoryItem(String id, String name, String description, String type, String rarity, int quantity, String source) {
        this(id, name, description, type, rarity);
        this.quantity = quantity;
        this.source = source;
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
    
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    
    public Instant getAcquiredAt() { return acquiredAt; }
    public void setAcquiredAt(Instant acquiredAt) { this.acquiredAt = acquiredAt; }
    
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    
    // Business logic methods
    public void addQuantity(int amount) {
        this.quantity += amount;
    }
    
    public boolean removeQuantity(int amount) {
        if (this.quantity >= amount) {
            this.quantity -= amount;
            return true;
        }
        return false;
    }
    
    public boolean isStackable() {
        return "consumable".equals(type) || "material".equals(type);
    }
    
    public String getRarityEmoji() {
        return switch (rarity.toLowerCase()) {
            case "common" -> "⚪";
            case "uncommon" -> "🟢";
            case "rare" -> "🔵";
            case "epic" -> "🟣";
            case "legendary" -> "🟡";
            case "mythic" -> "🔴";
            default -> "⚫";
        };
    }
    
    public String getTypeEmoji() {
        return switch (type.toLowerCase()) {
            case "consumable" -> "🍎";
            case "collectible" -> "💎";
            case "tool" -> "🔧";
            case "material" -> "📦";
            case "trophy" -> "🏆";
            default -> "❓";
        };
    }
    
    @Override
    public String toString() {
        return String.format("%s %s %s (x%d)", getRarityEmoji(), getTypeEmoji(), name, quantity);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        InventoryItem that = (InventoryItem) obj;
        return id != null ? id.equals(that.id) : that.id == null;
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
