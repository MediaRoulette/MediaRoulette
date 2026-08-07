package dev.mediaroulette.repository;

import dev.mediaroulette.domain.user.Preferences;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import java.util.Optional;

public class MongoPreferencesRepository implements PreferencesRepository {
    private final MongoCollection<Document> collection;

    public MongoPreferencesRepository(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    @Override
    public Optional<Preferences> findById(String userId) {
        Document doc = collection.find(new Document("_id", userId)).first();
        if (doc != null) {
            return Optional.of(mapToEntity(doc));
        }
        return Optional.empty();
    }

    @Override
    public Preferences save(Preferences entity) {
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
    private Preferences mapToEntity(Document doc) {
        // Dummy mapping for now
        return new Preferences(doc.getString("_id"));
    }

    private Document mapToDocument(Preferences entity) {
        // Dummy mapping for now
        return new Document("_id", entity.getUserId());
    }
}
