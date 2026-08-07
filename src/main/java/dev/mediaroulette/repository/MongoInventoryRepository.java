package dev.mediaroulette.repository;

import dev.mediaroulette.domain.economy.Inventory;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
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

    // TODO: implement mapping logic
    private Inventory mapToEntity(Document doc) {
        // Dummy mapping for now
        return new Inventory(doc.getString("_id"));
    }

    private Document mapToDocument(Inventory entity) {
        // Dummy mapping for now
        return new Document("_id", entity.getUserId());
    }
}
