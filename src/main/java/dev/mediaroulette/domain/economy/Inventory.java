package dev.mediaroulette.domain.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Inventory {
    public static final int MAX_INVENTORY_SIZE = 100;

    private String userId;
    private List<InventoryItem> items;

    public Inventory() {}

    public Inventory(String userId) {
        this.userId = userId;
        this.items = new ArrayList<>();
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public List<InventoryItem> getItems() { return items; }
    public void setItems(List<InventoryItem> items) { this.items = items; }

    public boolean addItem(InventoryItem item) {
        if (items.size() >= MAX_INVENTORY_SIZE) {
            return false;
        }
        
        if (item.isStackable()) {
            for (InventoryItem existingItem : items) {
                if (existingItem.getId().equals(item.getId())) {
                    existingItem.addQuantity(item.getQuantity());
                    return true;
                }
            }
        }
        
        items.add(item);
        return true;
    }

    public boolean removeItem(String itemId, int quantity) {
        for (int i = 0; i < items.size(); i++) {
            InventoryItem item = items.get(i);
            if (item.getId().equals(itemId)) {
                if (item.getQuantity() <= quantity) {
                    items.remove(i);
                    return true;
                } else {
                    return item.removeQuantity(quantity);
                }
            }
        }
        return false;
    }

    public InventoryItem getItem(String itemId) {
        return items.stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElse(null);
    }

    public boolean hasItem(String itemId, int quantity) {
        InventoryItem item = getItem(itemId);
        return item != null && item.getQuantity() >= quantity;
    }

    public List<InventoryItem> getItemsByType(String type) {
        return items.stream()
                .filter(item -> type.equals(item.getType()))
                .toList();
    }

    public List<InventoryItem> getItemsByRarity(String rarity) {
        return items.stream()
                .filter(item -> rarity.equals(item.getRarity()))
                .toList();
    }

    public int getTotalItems() {
        return items.stream()
                .mapToInt(InventoryItem::getQuantity)
                .sum();
    }

    public int getUniqueItemsCount() {
        return items.size();
    }

    public boolean hasSpace() {
        return items.size() < MAX_INVENTORY_SIZE;
    }

    public int getAvailableSpace() {
        return MAX_INVENTORY_SIZE - items.size();
    }

    public void sortItems(String sortBy) {
        switch (sortBy.toLowerCase()) {
            case "name" -> items.sort(Comparator.comparing(InventoryItem::getName));
            case "type" -> items.sort(Comparator.comparing(InventoryItem::getType));
            case "rarity" -> items.sort(Comparator.comparing(InventoryItem::getRarity));
            case "quantity" -> items.sort(Comparator.comparing(InventoryItem::getQuantity).reversed());
            case "acquired" -> items.sort(Comparator.comparing(InventoryItem::getAcquiredAt).reversed());
            default -> items.sort(Comparator.comparing(InventoryItem::getName));
        }
    }

    public void clear() {
        items.clear();
    }
}
