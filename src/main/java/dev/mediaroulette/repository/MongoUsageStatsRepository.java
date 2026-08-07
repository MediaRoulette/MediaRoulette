package dev.mediaroulette.repository;

import dev.mediaroulette.domain.user.UsageStats;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import java.util.Optional;

public class MongoUsageStatsRepository implements UsageStatsRepository {
    private final MongoCollection<Document> collection;

    public MongoUsageStatsRepository(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    @Override
    public Optional<UsageStats> findById(String userId) {
        Document doc = collection.find(new Document("_id", userId)).first();
        if (doc != null) {
            return Optional.of(mapToEntity(doc));
        }
        return Optional.empty();
    }

    @Override
    public UsageStats save(UsageStats entity) {
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
    private UsageStats mapToEntity(Document doc) {
        // Dummy mapping for now
        return new UsageStats(doc.getString("_id"));
    }

    private Document mapToDocument(UsageStats entity) {
        // Dummy mapping for now
        return new Document("_id", entity.getUserId());
    }
}
