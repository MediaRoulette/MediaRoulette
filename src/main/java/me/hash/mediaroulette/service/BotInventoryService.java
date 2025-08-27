package me.hash.mediaroulette.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.model.BotInventoryItem;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing bot inventory items
 */
public class BotInventoryService {
    private static final Logger logger = LoggerFactory.getLogger(BotInventoryService.class);
    private final MongoCollection<Document> collection;

    public BotInventoryService() {
        this.collection = Main.database.getCollection("bot_inventory");
    }

    /**
     * Add an item to the bot inventory
     */
    public void addItem(BotInventoryItem item) {
        Document doc = itemToDocument(item);
        collection.insertOne(doc);
    }

    /**
     * Get all items in bot inventory
     */
    public List<BotInventoryItem> getAllItems() {
        List<BotInventoryItem> items = new ArrayList<>();
        for (Document doc : collection.find()) {
            BotInventoryItem item = documentToItem(doc);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    /**
     * Get all active items that can be given away
     */
    public List<BotInventoryItem> getActiveItems() {
        return getAllItems().stream()
                .filter(BotInventoryItem::canBeGivenAway)
                .toList();
    }

    /**
     * Get items by type
     */
    public List<BotInventoryItem> getItemsByType(String type) {
        List<BotInventoryItem> items = new ArrayList<>();
        for (Document doc : collection.find(Filters.eq("type", type))) {
            BotInventoryItem item = documentToItem(doc);
            if (item != null && item.canBeGivenAway()) {
                items.add(item);
            }
        }
        return items;
    }

    /**
     * Get a specific item by ID
     */
    public Optional<BotInventoryItem> getItem(String itemId) {
        Document doc = collection.find(Filters.eq("_id", itemId)).first();
        if (doc != null) {
            BotInventoryItem item = documentToItem(doc);
            return Optional.ofNullable(item);
        }
        return Optional.empty();
    }

    /**
     * Remove an item from inventory
     */
    public boolean removeItem(String itemId) {
        return collection.deleteOne(Filters.eq("_id", itemId)).getDeletedCount() > 0;
    }

    /**
     * Mark an item as used (deactivate it)
     */
    public void markItemAsUsed(String itemId) {
        collection.updateOne(
                Filters.eq("_id", itemId),
                new Document("$set", new Document("isActive", false))
        );
    }

    /**
     * Update an item
     */
    public void updateItem(BotInventoryItem item) {
        Document doc = itemToDocument(item);
        collection.replaceOne(Filters.eq("_id", item.getId()), doc);
    }

    /**
     * Get items by rarity
     */
    public List<BotInventoryItem> getItemsByRarity(String rarity) {
        return getAllItems().stream()
                .filter(item -> rarity.equals(item.getRarity()) && item.canBeGivenAway())
                .toList();
    }

    /**
     * Clean up expired items
     */
    public int cleanupExpiredItems() {
        List<BotInventoryItem> expiredItems = getAllItems().stream()
                .filter(BotInventoryItem::isExpired)
                .toList();

        int removed = 0;
        for (BotInventoryItem item : expiredItems) {
            if (removeItem(item.getId())) {
                removed++;
            }
        }
        return removed;
    }

    /**
     * Get statistics about bot inventory
     */
    public BotInventoryStats getStats() {
        List<BotInventoryItem> allItems = getAllItems();
        
        long total = allItems.size();
        long active = allItems.stream().filter(BotInventoryItem::canBeGivenAway).count();
        long expired = allItems.stream().filter(BotInventoryItem::isExpired).count();
        long nitroItems = allItems.stream().filter(i -> "discord_nitro".equals(i.getType())).count();
        
        return new BotInventoryStats(total, active, expired, nitroItems);
    }

    private Document itemToDocument(BotInventoryItem item) {
        return new Document("_id", item.getId())
                .append("name", item.getName())
                .append("description", item.getDescription())
                .append("type", item.getType())
                .append("rarity", item.getRarity())
                .append("value", item.getValue())
                .append("isActive", item.isActive())
                .append("addedAt", item.getAddedAt().toString())
                .append("addedBy", item.getAddedBy())
                .append("expiresAt", item.getExpiresAt() != null ? item.getExpiresAt().toString() : null)
                .append("metadata", item.getMetadata());
    }

    private BotInventoryItem documentToItem(Document doc) {
        try {
            BotInventoryItem item = new BotInventoryItem();
            item.setId(doc.getString("_id"));
            item.setName(doc.getString("name"));
            item.setDescription(doc.getString("description"));
            item.setType(doc.getString("type"));
            item.setRarity(doc.getString("rarity"));
            item.setValue(doc.getString("value"));
            item.setActive(doc.getBoolean("isActive", true));
            item.setAddedBy(doc.getString("addedBy"));
            item.setMetadata(doc.getString("metadata"));

            String addedAtStr = doc.getString("addedAt");
            if (addedAtStr != null) {
                item.setAddedAt(Instant.parse(addedAtStr));
            }

            String expiresAtStr = doc.getString("expiresAt");
            if (expiresAtStr != null) {
                item.setExpiresAt(Instant.parse(expiresAtStr));
            }

            return item;
        } catch (Exception e) {
            logger.error("Error parsing bot inventory item: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Statistics class for bot inventory
     */
    public static class BotInventoryStats {
        private final long total;
        private final long active;
        private final long expired;
        private final long nitroItems;

        public BotInventoryStats(long total, long active, long expired, long nitroItems) {
            this.total = total;
            this.active = active;
            this.expired = expired;
            this.nitroItems = nitroItems;
        }

        public long getTotal() { return total; }
        public long getActive() { return active; }
        public long getExpired() { return expired; }
        public long getNitroItems() { return nitroItems; }
    }
}