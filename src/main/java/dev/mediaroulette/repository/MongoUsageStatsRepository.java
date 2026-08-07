package dev.mediaroulette.repository;

import dev.mediaroulette.domain.user.UsageStats;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private UsageStats mapToEntity(Document doc) {
        UsageStats stats = new UsageStats(doc.getString("_id"));
        
        Document sourceUsageDoc = doc.get("sourceUsageCount", Document.class);
        if (sourceUsageDoc != null) {
            Map<String, Long> sourceUsage = new HashMap<>();
            for (String key : sourceUsageDoc.keySet()) {
                sourceUsage.put(key, sourceUsageDoc.getLong(key));
            }
            stats.setSourceUsageCount(sourceUsage);
        }
        
        Document commandUsageDoc = doc.get("commandUsageCount", Document.class);
        if (commandUsageDoc != null) {
            Map<String, Long> commandUsage = new HashMap<>();
            for (String key : commandUsageDoc.keySet()) {
                commandUsage.put(key, commandUsageDoc.getLong(key));
            }
            stats.setCommandUsageCount(commandUsage);
        }
        
        List<String> subreddits = doc.getList("customSubreddits", String.class);
        if (subreddits != null) {
            stats.setCustomSubreddits(new ArrayList<>(subreddits));
        }
        
        Document subredditUsageDoc = doc.get("subredditUsageCount", Document.class);
        if (subredditUsageDoc != null) {
            Map<String, Integer> subredditUsage = new HashMap<>();
            for (String key : subredditUsageDoc.keySet()) {
                subredditUsage.put(key, subredditUsageDoc.getInteger(key));
            }
            stats.setSubredditUsageCount(subredditUsage);
        }
        
        Document customQueriesDoc = doc.get("customQueries", Document.class);
        if (customQueriesDoc != null) {
            Map<String, List<String>> customQueries = new HashMap<>();
            for (String key : customQueriesDoc.keySet()) {
                customQueries.put(key, customQueriesDoc.getList(key, String.class));
            }
            stats.setCustomQueries(customQueries);
        }
        
        if (doc.containsKey("totalCommandsUsed")) {
            stats.setTotalCommandsUsed(doc.getLong("totalCommandsUsed"));
        }
        
        return stats;
    }

    private Document mapToDocument(UsageStats entity) {
        Document doc = new Document("_id", entity.getUserId());
        
        if (entity.getSourceUsageCount() != null) {
            Document sourceUsageDoc = new Document();
            for (Map.Entry<String, Long> entry : entity.getSourceUsageCount().entrySet()) {
                sourceUsageDoc.append(entry.getKey(), entry.getValue());
            }
            doc.append("sourceUsageCount", sourceUsageDoc);
        }
        
        if (entity.getCommandUsageCount() != null) {
            Document commandUsageDoc = new Document();
            for (Map.Entry<String, Long> entry : entity.getCommandUsageCount().entrySet()) {
                commandUsageDoc.append(entry.getKey(), entry.getValue());
            }
            doc.append("commandUsageCount", commandUsageDoc);
        }
        
        if (entity.getCustomSubreddits() != null) {
            doc.append("customSubreddits", entity.getCustomSubreddits());
        }
        
        if (entity.getSubredditUsageCount() != null) {
            Document subredditUsageDoc = new Document();
            for (Map.Entry<String, Integer> entry : entity.getSubredditUsageCount().entrySet()) {
                subredditUsageDoc.append(entry.getKey(), entry.getValue());
            }
            doc.append("subredditUsageCount", subredditUsageDoc);
        }
        
        if (entity.getCustomQueries() != null) {
            Document customQueriesDoc = new Document();
            for (Map.Entry<String, List<String>> entry : entity.getCustomQueries().entrySet()) {
                customQueriesDoc.append(entry.getKey(), entry.getValue());
            }
            doc.append("customQueries", customQueriesDoc);
        }
        
        doc.append("totalCommandsUsed", entity.getTotalCommandsUsed());
        
        return doc;
    }
}
