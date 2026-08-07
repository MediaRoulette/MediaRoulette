package dev.mediaroulette.domain.economy;

public class ShopItem {
    public enum ItemType {
        THEME,
        PREMIUM_UPGRADE,
        QUEST_SKIP,
        COIN_MULTIPLIER,
        FAVORITE_SLOTS,
        CUSTOM_EMOJI,
        PROFILE_BADGE,
        DAILY_BONUS
    }

    public enum ItemRarity {
        COMMON(new java.awt.Color(169, 169, 169)),    // Gray
        UNCOMMON(new java.awt.Color(30, 255, 0)),     // Green
        RARE(new java.awt.Color(0, 112, 255)),        // Blue
        EPIC(new java.awt.Color(163, 53, 238)),       // Purple
        LEGENDARY(new java.awt.Color(255, 128, 0));   // Orange

        private final java.awt.Color color;

        ItemRarity(java.awt.Color color) {
            this.color = color;
        }

        public java.awt.Color getColor() {
            return color;
        }
    }

    private String itemId;
    private String name;
    private String description;
    private String emoji;
    private ItemType type;
    private ItemRarity rarity;
    private long price;
    private boolean premiumOnly;
    private boolean limited;
    private int stock; // -1 for unlimited
    private String metadata; // JSON for additional item data

    public ShopItem() {
        this.itemId = generateItemId();
        this.stock = -1; // Unlimited by default
    }

    public ShopItem(String name, String description, String emoji, ItemType type, ItemRarity rarity, long price) {
        this();
        this.name = name;
        this.description = description;
        this.emoji = emoji;
        this.type = type;
        this.rarity = rarity;
        this.price = price;
    }

    private String generateItemId() {
        return "ITEM_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    public boolean isAvailable() {
        return stock != 0;
    }

    public boolean isUnlimited() {
        return stock == -1;
    }

    public void purchase() {
        if (stock > 0) {
            stock--;
        }
    }

    public String getFormattedPrice() {
        java.text.NumberFormat formatter = java.text.NumberFormat.getInstance(java.util.Locale.US);
        return formatter.format(price) + " coins";
    }

    public String getRarityDisplay() {
        return switch (rarity) {
            case COMMON -> "⚪ Common";
            case UNCOMMON -> "🟢 Uncommon";
            case RARE -> "🔵 Rare";
            case EPIC -> "🟣 Epic";
            case LEGENDARY -> "🟠 Legendary";
        };
    }

    public String getStockDisplay() {
        if (isUnlimited()) {
            return "∞ Unlimited";
        } else if (stock == 0) {
            return "❌ Out of Stock";
        } else {
            return stock + " remaining";
        }
    }

    // Getters and Setters
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getEmoji() { return emoji; }
    public void setEmoji(String emoji) { this.emoji = emoji; }

    public ItemType getType() { return type; }
    public void setType(ItemType type) { this.type = type; }

    public ItemRarity getRarity() { return rarity; }
    public void setRarity(ItemRarity rarity) { this.rarity = rarity; }

    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }

    public boolean isPremiumOnly() { return premiumOnly; }
    public void setPremiumOnly(boolean premiumOnly) { this.premiumOnly = premiumOnly; }

    public boolean isLimited() { return limited; }
    public void setLimited(boolean limited) { this.limited = limited; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    @Override
    public String toString() {
        return String.format("ShopItem{id='%s', name='%s', type=%s, rarity=%s, price=%d, stock=%d}",
                itemId, name, type, rarity, price, stock);
    }
}
