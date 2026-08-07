package dev.mediaroulette.repository;

import dev.mediaroulette.domain.user.Account;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import java.util.Optional;

public class MongoAccountRepository implements AccountRepository {
    private final MongoCollection<Document> collection;

    public MongoAccountRepository(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    @Override
    public Optional<Account> findById(String userId) {
        Document doc = collection.find(new Document("_id", userId)).first();
        if (doc != null) {
            return Optional.of(mapToEntity(doc));
        }
        return Optional.empty();
    }

    @Override
    public Account save(Account entity) {
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
    private Account mapToEntity(Document doc) {
        // Dummy mapping for now
        return new Account(doc.getString("_id"));
    }

    private Document mapToDocument(Account entity) {
        // Dummy mapping for now
        return new Document("_id", entity.getUserId());
    }
}
