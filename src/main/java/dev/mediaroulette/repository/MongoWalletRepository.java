package dev.mediaroulette.repository;

import dev.mediaroulette.domain.economy.Wallet;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import java.util.Optional;

public class MongoWalletRepository implements WalletRepository {
    private final MongoCollection<Document> collection;

    public MongoWalletRepository(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    @Override
    public Optional<Wallet> findById(String userId) {
        Document doc = collection.find(new Document("_id", userId)).first();
        if (doc != null) {
            return Optional.of(mapToEntity(doc));
        }
        return Optional.empty();
    }

    @Override
    public Wallet save(Wallet entity) {
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
    private Wallet mapToEntity(Document doc) {
        // Dummy mapping for now
        return new Wallet(doc.getString("_id"));
    }

    private Document mapToDocument(Wallet entity) {
        // Dummy mapping for now
        return new Document("_id", entity.getUserId());
    }
}
