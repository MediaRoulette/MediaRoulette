package dev.mediaroulette.repository;

import dev.mediaroulette.domain.quest.QuestProgress;
import dev.mediaroulette.domain.quest.Quest;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import java.util.ArrayList;
import java.util.List;
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

    private QuestProgress mapToEntity(Document doc) {
        QuestProgress progress = new QuestProgress(doc.getString("_id"));
        
        List<Document> questDocs = doc.getList("dailyQuests", Document.class);
        if (questDocs != null) {
            List<Quest> quests = new ArrayList<>();
            for (Document questDoc : questDocs) {
                Quest quest = new Quest();
                if (questDoc.containsKey("type")) quest.setType(Quest.QuestType.valueOf(questDoc.getString("type")));
                if (questDoc.containsKey("difficulty")) quest.setDifficulty(Quest.QuestDifficulty.valueOf(questDoc.getString("difficulty")));
                if (questDoc.containsKey("description")) quest.setDescription(questDoc.getString("description"));
                
                
                if (questDoc.containsKey("coinReward")) quest.setCoinReward(questDoc.getLong("coinReward").intValue());
                if (questDoc.containsKey("completed")) quest.setCompleted(questDoc.getBoolean("completed"));
                if (questDoc.containsKey("claimed")) quest.setClaimed(questDoc.getBoolean("claimed"));
                quests.add(quest);
            }
            progress.setDailyQuests(quests);
        }
        
        if (doc.containsKey("lastQuestReset") && doc.getString("lastQuestReset") != null) {
            progress.setLastQuestReset(java.time.LocalDate.parse(doc.getString("lastQuestReset")));
        }
        if (doc.containsKey("totalQuestsCompleted")) progress.setTotalQuestsCompleted(doc.getLong("totalQuestsCompleted"));
        if (doc.containsKey("questsCompletedToday")) progress.setQuestsCompletedToday(doc.getLong("questsCompletedToday"));
        if (doc.containsKey("lastQuestCompletionDate") && doc.getString("lastQuestCompletionDate") != null) {
            progress.setLastQuestCompletionDate(java.time.LocalDate.parse(doc.getString("lastQuestCompletionDate")));
        }
        
        return progress;
    }

    private Document mapToDocument(QuestProgress entity) {
        List<Document> questDocs = new ArrayList<>();
        for (Quest quest : entity.getDailyQuests()) {
            questDocs.add(new Document("type", quest.getType().name())
                    .append("difficulty", quest.getDifficulty().name())
                    .append("description", quest.getDescription())
                    
                    
                    .append("coinReward", quest.getCoinReward())
                    .append("completed", quest.isCompleted())
                    .append("claimed", quest.isClaimed()));
        }
        
        Document doc = new Document("_id", entity.getUserId())
                .append("dailyQuests", questDocs)
                .append("totalQuestsCompleted", entity.getTotalQuestsCompleted())
                .append("questsCompletedToday", entity.getQuestsCompletedToday());
                
        if (entity.getLastQuestReset() != null) {
            doc.append("lastQuestReset", entity.getLastQuestReset().toString());
        }
        if (entity.getLastQuestCompletionDate() != null) {
            doc.append("lastQuestCompletionDate", entity.getLastQuestCompletionDate().toString());
        }
        return doc;
    }
}
