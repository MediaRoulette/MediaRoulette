package me.hash.mediaroulette.service;

import me.hash.mediaroulette.model.ShopItem;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ShopManager {
    private static ShopManager instance;
    private List<ShopItem> shopItems;

    private ShopManager() {
        this.shopItems = new ArrayList<>();
        initializeShopItems();
    }

    public static ShopManager getInstance() {
        if (instance == null) {
            synchronized (ShopManager.class) {
                if (instance == null) {
                    instance = new ShopManager();
                }
            }
        }
        return instance;
    }

    private void initializeShopItems() {
        // Themes
        shopItems.add(new ShopItem("Neon Theme", "Vibrant cyberpunk theme with electric colors", "🌈", 
                ShopItem.ItemType.THEME, ShopItem.ItemRarity.RARE, 500));
        shopItems.add(new ShopItem("Dark Theme", "Sleek dark theme for low-light environments", "⚫", 
                ShopItem.ItemType.THEME, ShopItem.ItemRarity.COMMON, 200));
        shopItems.add(new ShopItem("Minimal Theme", "Clean minimalist design with subtle shadows", "⚪", 
                ShopItem.ItemType.THEME, ShopItem.ItemRarity.UNCOMMON, 300));
        shopItems.add(new ShopItem("Galaxy Theme", "Cosmic theme with stellar visuals", "🌌", 
                ShopItem.ItemType.THEME, ShopItem.ItemRarity.EPIC, 1000));

        // Premium Features
        ShopItem premiumUpgrade = new ShopItem("Premium Upgrade (30 days)", "Unlock premium features for 30 days", "👑", 
                ShopItem.ItemType.PREMIUM_UPGRADE, ShopItem.ItemRarity.LEGENDARY, 2500);
        premiumUpgrade.setLimited(true);
        premiumUpgrade.setStock(100);
        shopItems.add(premiumUpgrade);

        // Quest Items
        shopItems.add(new ShopItem("Quest Skip Token", "Skip a quest and get instant completion", "⏭️", 
                ShopItem.ItemType.QUEST_SKIP, ShopItem.ItemRarity.UNCOMMON, 150));
        shopItems.add(new ShopItem("2x Coin Multiplier (1 hour)", "Double coin rewards for 1 hour", "💰", 
                ShopItem.ItemType.COIN_MULTIPLIER, ShopItem.ItemRarity.RARE, 400));
        shopItems.add(new ShopItem("2x Coin Multiplier (24 hours)", "Double coin rewards for 24 hours", "💎", 
                ShopItem.ItemType.COIN_MULTIPLIER, ShopItem.ItemRarity.EPIC, 1500));

        // Utility Items
        shopItems.add(new ShopItem("+10 Favorite Slots", "Increase your favorite limit by 10", "⭐", 
                ShopItem.ItemType.FAVORITE_SLOTS, ShopItem.ItemRarity.COMMON, 250));
        shopItems.add(new ShopItem("+25 Favorite Slots", "Increase your favorite limit by 25", "🌟", 
                ShopItem.ItemType.FAVORITE_SLOTS, ShopItem.ItemRarity.RARE, 600));

        // Cosmetic Items
        shopItems.add(new ShopItem("Custom Profile Badge", "Show off with a unique profile badge", "🏆", 
                ShopItem.ItemType.PROFILE_BADGE, ShopItem.ItemRarity.EPIC, 800));
        shopItems.add(new ShopItem("Custom Emoji Pack", "Unlock exclusive emoji reactions", "😎", 
                ShopItem.ItemType.CUSTOM_EMOJI, ShopItem.ItemRarity.RARE, 350));

        // Daily Bonuses
        shopItems.add(new ShopItem("Daily Bonus Booster", "Increase daily login bonus by 50%", "🎁", 
                ShopItem.ItemType.DAILY_BONUS, ShopItem.ItemRarity.UNCOMMON, 300));

        // Limited Time Items
        ShopItem limitedTheme = new ShopItem("Holiday Special Theme", "Exclusive seasonal theme", "🎄", 
                ShopItem.ItemType.THEME, ShopItem.ItemRarity.LEGENDARY, 1200);
        limitedTheme.setLimited(true);
        limitedTheme.setStock(50);
        shopItems.add(limitedTheme);
    }

    public List<ShopItem> getAllItems() {
        return new ArrayList<>(shopItems);
    }

    public List<ShopItem> getAvailableItems() {
        return shopItems.stream()
                .filter(ShopItem::isAvailable)
                .collect(Collectors.toList());
    }

    public List<ShopItem> getItemsByType(ShopItem.ItemType type) {
        return shopItems.stream()
                .filter(item -> item.getType() == type)
                .filter(ShopItem::isAvailable)
                .collect(Collectors.toList());
    }

    public List<ShopItem> getItemsByRarity(ShopItem.ItemRarity rarity) {
        return shopItems.stream()
                .filter(item -> item.getRarity() == rarity)
                .filter(ShopItem::isAvailable)
                .collect(Collectors.toList());
    }

    public List<ShopItem> getAffordableItems(long userCoins) {
        return shopItems.stream()
                .filter(item -> item.getPrice() <= userCoins)
                .filter(ShopItem::isAvailable)
                .collect(Collectors.toList());
    }

    public List<ShopItem> getPremiumItems() {
        return shopItems.stream()
                .filter(ShopItem::isPremiumOnly)
                .filter(ShopItem::isAvailable)
                .collect(Collectors.toList());
    }

    public List<ShopItem> getLimitedItems() {
        return shopItems.stream()
                .filter(ShopItem::isLimited)
                .filter(ShopItem::isAvailable)
                .collect(Collectors.toList());
    }

    public ShopItem getItemById(String itemId) {
        return shopItems.stream()
                .filter(item -> item.getItemId().equals(itemId))
                .findFirst()
                .orElse(null);
    }

    public boolean purchaseItem(String itemId) {
        ShopItem item = getItemById(itemId);
        if (item != null && item.isAvailable()) {
            item.purchase();
            return true;
        }
        return false;
    }

    public void addItem(ShopItem item) {
        shopItems.add(item);
    }

    public void removeItem(String itemId) {
        shopItems.removeIf(item -> item.getItemId().equals(itemId));
    }

    public int getTotalItemCount() {
        return shopItems.size();
    }

    public int getAvailableItemCount() {
        return (int) shopItems.stream().filter(ShopItem::isAvailable).count();
    }
}