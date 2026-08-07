package dev.mediaroulette.repository;

import dev.mediaroulette.domain.economy.Inventory;
import dev.mediaroulette.domain.economy.InventoryItem;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MongoInventoryRepository implements InventoryRepository {
    private final MongoCollection<Document> collection;

    public MongoInventoryRepository(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    @Override
    public Optional<Inventory> findById(String userId) {
        Document doc = collection.find(new Document("_id", userId)).first();
        if (doc != null) {
            return Optional.of(mapToEntity(doc));
        }
        return Optional.empty();
    }

    @Override
    public Inventory save(Inventory entity) {
        Document doc = mapToDocument(entity);
        collection.replaceOne(new Document("_id", entity.getUserId()), doc,
                new com.mongodb.client.model.ReplaceOptions().upsert(true));
        return entity;
    }

    @Override
    public boolean exists(String userId) {
        return collection.find(new Document("_id", userId)).first() != null;
    }

    private Inventory mapToEntity(Document doc) {
        Inventory inventory = new Inventory(doc.getString("_id"));
        List<Document> itemDocs = doc.getList("items", Document.class);
        if (itemDocs != null) {
            List<InventoryItem> items = new ArrayList<>();
            for (Document itemDoc : itemDocs) {
                InventoryItem item = new InventoryItem(
                        itemDoc.getString("id"),
                        itemDoc.getString("name"),
                        itemDoc.getString("description"),
                        itemDoc.getString("type"),
                        itemDoc.getString("rarity")
                );
                if (itemDoc.containsKey("quantity")) item.setQuantity(itemDoc.getInteger("quantity"));
                if (itemDoc.containsKey("acquiredAt")) item.setAcquiredAt(java.time.Instant.parse(itemDoc.getString("acquiredAt")));
                
                
                items.add(item);
            }
            inventory.setItems(items);
        }
        return inventory;
    }

    private Document mapToDocument(Inventory entity) {
        List<Document> itemDocs = new ArrayList<>();
        for (InventoryItem item : entity.getItems()) {
            itemDocs.add(new Document("id", item.getId())
                    .append("name", item.getName())
                    .append("description", item.getDescription())
                    .append("type", item.getType())
                    .append("rarity", item.getRarity())
                    .append("quantity", item.getQuantity())
                    .append("acquiredAt", item.getAcquiredAt() != null ? item.getAcquiredAt().toString() : null)
                    
                    );
        }
        return new Document("_id", entity.getUserId()).append("items", itemDocs);
    }
}
