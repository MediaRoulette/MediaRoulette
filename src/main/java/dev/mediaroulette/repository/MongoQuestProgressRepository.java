package dev.mediaroulette.repository;

import dev.mediaroulette.domain.quest.QuestProgress;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import java.util.Optional;

public class MongoQuestProgressRepository implements QuestProgressRepository {
    private final MongoCollection<Document> collection;

    public MongoQuestProgressRepository(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    @Override
    public Optional<QuestProgress> findById(String userId) {
        Document doc = collection.find(new Document("_id", userId)).first();
        if (doc != null) {
            return Optional.of(mapToEntity(doc));
        }
        return Optional.empty();
    }

    @Override
    public QuestProgress save(QuestProgress entity) {
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
    private QuestProgress mapToEntity(Document doc) {
        // Dummy mapping for now
        return new QuestProgress(doc.getString("_id"));
    }

    private Document mapToDocument(QuestProgress entity) {
        // Dummy mapping for now
        return new Document("_id", entity.getUserId());
    }
}
