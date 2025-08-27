package me.hash.mediaroulette.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Accumulators;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.model.Favorite;
import me.hash.mediaroulette.model.ImageOptions;
import me.hash.mediaroulette.model.InventoryItem;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MongoUserRepository implements UserRepository {
    private static final Logger logger = LoggerFactory.getLogger(MongoUserRepository.class);
    private final MongoCollection<Document> userCollection;

    public MongoUserRepository(MongoCollection<Document> userCollection) {
        this.userCollection = userCollection;
    }

    @Override
    public Optional<User> findById(String userId) {
        Document doc = userCollection.find(new Document("_id", userId)).first();
        if (doc != null) {
            User user = mapDocumentToUser(doc);
            return Optional.of(user);
        }
        return Optional.empty();
    }

    @Override
    public User save(User user) {
        Document doc = mapUserToDocument(user);
        userCollection.replaceOne(new Document("_id", user.getUserId()), doc,
                new com.mongodb.client.model.ReplaceOptions().upsert(true));
        return user;
    }

    @Override
    public boolean exists(String userId) {
        return userCollection.find(new Document("_id", userId)).first() != null;
    }

    // --- Helper Methods for Mapping ---

    private User mapDocumentToUser(Document doc) {
        String userId = doc.getString("_id");
        User user = new User(userId);
        user.setImagesGenerated(doc.getLong("imagesGenerated") != null ? doc.getLong("imagesGenerated") : 0L);
        user.setNsfw(doc.getBoolean("nsfw", false));
        user.setPremium(doc.getBoolean("premium", false));
        user.setAdmin(doc.getBoolean("admin", false));
        user.setLocale(doc.getString("locale") != null ? doc.getString("locale") : "en_US");
        user.setTheme(doc.getString("theme") != null ? doc.getString("theme") : "default");
        
        // Map economy fields
        user.setCoins(doc.getLong("coins") != null ? doc.getLong("coins") : 100L);
        user.setTotalCoinsEarned(doc.getLong("totalCoinsEarned") != null ? doc.getLong("totalCoinsEarned") : 100L);
        user.setTotalCoinsSpent(doc.getLong("totalCoinsSpent") != null ? doc.getLong("totalCoinsSpent") : 0L);
        
        // Map last quest reset date
        String lastQuestResetStr = doc.getString("lastQuestReset");
        if (lastQuestResetStr != null) {
            user.setLastQuestReset(java.time.LocalDate.parse(lastQuestResetStr));
        }
        
        // Map quest completion tracking
        user.setTotalQuestsCompleted(doc.getLong("totalQuestsCompleted") != null ? doc.getLong("totalQuestsCompleted") : 0L);
        user.setQuestsCompletedToday(doc.getLong("questsCompletedToday") != null ? doc.getLong("questsCompletedToday") : 0L);
        
        String lastQuestCompletionDateStr = doc.getString("lastQuestCompletionDate");
        if (lastQuestCompletionDateStr != null) {
            user.setLastQuestCompletionDate(java.time.LocalDate.parse(lastQuestCompletionDateStr));
        }

        // Map favorites (assumes favorites is stored as a List of Documents)
        List<Document> favDocs = (List<Document>) doc.get("favorites", new ArrayList<Document>());
        for (Document favDoc : favDocs) {
            int id = favDoc.getInteger("id", 0);
            String description = favDoc.getString("description");
            String image = favDoc.getString("image");
            String type = favDoc.getString("type");
            user.getFavorites().add(new Favorite(id, description, image, type));
        }

        // Map quests
        List<Document> questDocs = (List<Document>) doc.get("dailyQuests", new ArrayList<Document>());
        for (Document questDoc : questDocs) {
            me.hash.mediaroulette.model.Quest quest = mapDocumentToQuest(questDoc);
            user.getDailyQuests().add(quest);
        }
        
        // Map transaction history
        List<Document> transactionDocs = (List<Document>) doc.get("transactionHistory", new ArrayList<Document>());
        for (Document transactionDoc : transactionDocs) {
            me.hash.mediaroulette.model.Transaction transaction = mapDocumentToTransaction(transactionDoc);
            user.getTransactionHistory().add(transaction);
        }

        // Map image options (assumes a sub-document "images")
        Document imagesDoc = (Document) doc.get("images");
        if (imagesDoc != null) {
            for (String key : imagesDoc.keySet()) {
                Document optionDoc = (Document) imagesDoc.get(key);
                boolean enabled = optionDoc.getBoolean("enabled", false);
                double chance = optionDoc.getDouble("chance") != null ? optionDoc.getDouble("chance") : 0.0;
                user.getImageOptionsMap().put(key, new ImageOptions(key, enabled, chance));
            }
        }
        
        // Parse inventory
        List<Document> inventoryDocs = doc.getList("inventory", Document.class);
        if (inventoryDocs != null) {
            List<InventoryItem> inventory = new ArrayList<>();
            for (Document inventoryDoc : inventoryDocs) {
                InventoryItem item = parseInventoryItem(inventoryDoc);
                if (item != null) {
                    inventory.add(item);
                }
            }
            user.setInventory(inventory);
        }
        
        // Map usage statistics
        mapUsageStatistics(doc, user);
        
        return user;
    }

    private Document mapUserToDocument(User user) {
        Document doc = new Document("_id", user.getUserId())
                .append("imagesGenerated", user.getImagesGenerated())
                .append("nsfw", user.isNsfw())
                .append("premium", user.isPremium())
                .append("admin", user.isAdmin())
                .append("locale", user.getLocale())
                .append("theme", user.getTheme())
                .append("coins", user.getCoins())
                .append("totalCoinsEarned", user.getTotalCoinsEarned())
                .append("totalCoinsSpent", user.getTotalCoinsSpent())
                .append("lastQuestReset", user.getLastQuestReset() != null ? user.getLastQuestReset().toString() : null)
                .append("totalQuestsCompleted", user.getTotalQuestsCompleted())
                .append("questsCompletedToday", user.getQuestsCompletedToday())
                .append("lastQuestCompletionDate", user.getLastQuestCompletionDate() != null ? user.getLastQuestCompletionDate().toString() : null);

        // Map favorites
        List<Document> favDocs = new ArrayList<>();
        for (Favorite fav : user.getFavorites()) {
            Document favDoc = new Document("id", fav.getId())
                    .append("description", fav.getDescription())
                    .append("image", fav.getImage())
                    .append("type", fav.getType());
            favDocs.add(favDoc);
        }
        doc.append("favorites", favDocs);

        // Map quests
        List<Document> questDocs = new ArrayList<>();
        for (me.hash.mediaroulette.model.Quest quest : user.getDailyQuests()) {
            Document questDoc = mapQuestToDocument(quest);
            questDocs.add(questDoc);
        }
        doc.append("dailyQuests", questDocs);
        
        // Map transaction history
        List<Document> transactionDocs = new ArrayList<>();
        for (me.hash.mediaroulette.model.Transaction transaction : user.getTransactionHistory()) {
            Document transactionDoc = mapTransactionToDocument(transaction);
            transactionDocs.add(transactionDoc);
        }
        doc.append("transactionHistory", transactionDocs);

        // Map image options
        Document imagesDoc = new Document();
        for (Map.Entry<String, ImageOptions> entry : user.getImageOptionsMap().entrySet()) {
            ImageOptions option = entry.getValue();
            Document optionDoc = new Document("enabled", option.isEnabled())
                    .append("chance", option.getChance());
            imagesDoc.append(entry.getKey(), optionDoc);
        }
        doc.append("images", imagesDoc);
        
        // Map inventory
        List<Document> inventoryDocs = new ArrayList<>();
        for (InventoryItem item : user.getInventory()) {
            Document itemDoc = inventoryItemToDocument(item);
            inventoryDocs.add(itemDoc);
        }
        doc.append("inventory", inventoryDocs);
        
        // Map usage statistics
        mapUsageStatisticsToDocument(doc, user);
        
        return doc;
    }
    
    // Helper methods for Quest mapping
    private me.hash.mediaroulette.model.Quest mapDocumentToQuest(Document doc) {
        me.hash.mediaroulette.model.Quest quest = new me.hash.mediaroulette.model.Quest();
        quest.setQuestId(doc.getString("questId"));
        quest.setType(me.hash.mediaroulette.model.Quest.QuestType.valueOf(doc.getString("type")));
        quest.setDifficulty(me.hash.mediaroulette.model.Quest.QuestDifficulty.valueOf(doc.getString("difficulty")));
        quest.setTitle(doc.getString("title"));
        quest.setDescription(doc.getString("description"));
        quest.setEmoji(doc.getString("emoji"));
        quest.setTargetValue(doc.getInteger("targetValue", 0));
        quest.setCurrentProgress(doc.getInteger("currentProgress", 0));
        quest.setCoinReward(doc.getInteger("coinReward", 0));
        quest.setCompleted(doc.getBoolean("completed", false));
        quest.setClaimed(doc.getBoolean("claimed", false));
        
        String assignedDateStr = doc.getString("assignedDate");
        if (assignedDateStr != null) {
            quest.setAssignedDate(java.time.LocalDate.parse(assignedDateStr));
        }
        
        String completedAtStr = doc.getString("completedAt");
        if (completedAtStr != null) {
            quest.setCompletedAt(java.time.Instant.parse(completedAtStr));
        }
        
        quest.setMetadata(doc.getString("metadata"));
        return quest;
    }
    
    private Document mapQuestToDocument(me.hash.mediaroulette.model.Quest quest) {
        return new Document("questId", quest.getQuestId())
                .append("type", quest.getType().name())
                .append("difficulty", quest.getDifficulty().name())
                .append("title", quest.getTitle())
                .append("description", quest.getDescription())
                .append("emoji", quest.getEmoji())
                .append("targetValue", quest.getTargetValue())
                .append("currentProgress", quest.getCurrentProgress())
                .append("coinReward", quest.getCoinReward())
                .append("completed", quest.isCompleted())
                .append("claimed", quest.isClaimed())
                .append("assignedDate", quest.getAssignedDate() != null ? quest.getAssignedDate().toString() : null)
                .append("completedAt", quest.getCompletedAt() != null ? quest.getCompletedAt().toString() : null)
                .append("metadata", quest.getMetadata());
    }
    
    // Helper methods for Transaction mapping
    private me.hash.mediaroulette.model.Transaction mapDocumentToTransaction(Document doc) {
        me.hash.mediaroulette.model.Transaction transaction = new me.hash.mediaroulette.model.Transaction();
        transaction.setTransactionId(doc.getString("transactionId"));
        transaction.setUserId(doc.getString("userId"));
        transaction.setType(me.hash.mediaroulette.model.Transaction.TransactionType.valueOf(doc.getString("type")));
        transaction.setAmount(doc.getLong("amount"));
        transaction.setBalanceBefore(doc.getLong("balanceBefore"));
        transaction.setBalanceAfter(doc.getLong("balanceAfter"));
        transaction.setDescription(doc.getString("description"));
        
        String timestampStr = doc.getString("timestamp");
        if (timestampStr != null) {
            transaction.setTimestamp(java.time.Instant.parse(timestampStr));
        }
        
        return transaction;
    }
    
    private Document mapTransactionToDocument(me.hash.mediaroulette.model.Transaction transaction) {
        return new Document("transactionId", transaction.getTransactionId())
                .append("userId", transaction.getUserId())
                .append("type", transaction.getType().name())
                .append("amount", transaction.getAmount())
                .append("balanceBefore", transaction.getBalanceBefore())
                .append("balanceAfter", transaction.getBalanceAfter())
                .append("description", transaction.getDescription())
                .append("timestamp", transaction.getTimestamp() != null ? transaction.getTimestamp().toString() : null);
    }

    private InventoryItem parseInventoryItem(Document doc) {
        if (doc == null) return null;
        
        InventoryItem item = new InventoryItem();
        item.setId(doc.getString("id"));
        item.setName(doc.getString("name"));
        item.setDescription(doc.getString("description"));
        item.setType(doc.getString("type"));
        item.setRarity(doc.getString("rarity"));
        item.setQuantity(doc.getInteger("quantity", 1));
        item.setSource(doc.getString("source"));
        
        String acquiredAtStr = doc.getString("acquiredAt");
        if (acquiredAtStr != null) {
            try {
                item.setAcquiredAt(java.time.Instant.parse(acquiredAtStr));
            } catch (Exception e) {
                item.setAcquiredAt(java.time.Instant.now());
            }
        }
        
        return item;
    }

    private Document inventoryItemToDocument(InventoryItem item) {
        return new Document()
                .append("id", item.getId())
                .append("name", item.getName())
                .append("description", item.getDescription())
                .append("type", item.getType())
                .append("rarity", item.getRarity())
                .append("quantity", item.getQuantity())
                .append("source", item.getSource())
                .append("acquiredAt", item.getAcquiredAt() != null ? item.getAcquiredAt().toString() : null);
    }

    @Override
    public long getTotalUsers() {
        return userCollection.countDocuments();
    }

    @Override
    public long getTotalImagesGenerated() {
        try {
            // Use MongoDB aggregation to sum all imagesGenerated fields
            Document result = userCollection.aggregate(Arrays.asList(
                Aggregates.group(null, Accumulators.sum("totalImages", "$imagesGenerated"))
            )).first();
            
            if (result != null && result.containsKey("totalImages")) {
                return result.getLong("totalImages");
            }
            return 0L;
        } catch (Exception e) {
            logger.error("Error calculating total images generated: {}", e.getMessage());
            return 0L;
        }
    }
    
    // Helper methods for usage statistics mapping
    private void mapUsageStatistics(Document doc, User user) {
        // Map source usage count
        Document sourceUsageDoc = doc.get("sourceUsageCount", Document.class);
        if (sourceUsageDoc != null) {
            Map<String, Long> sourceUsageCount = new java.util.HashMap<>();
            for (String key : sourceUsageDoc.keySet()) {
                Object value = sourceUsageDoc.get(key);
                if (value instanceof Number) {
                    sourceUsageCount.put(key, ((Number) value).longValue());
                }
            }
            user.setSourceUsageCount(sourceUsageCount);
        }
        
        // Map command usage count
        Document commandUsageDoc = doc.get("commandUsageCount", Document.class);
        if (commandUsageDoc != null) {
            Map<String, Long> commandUsageCount = new java.util.HashMap<>();
            for (String key : commandUsageDoc.keySet()) {
                Object value = commandUsageDoc.get(key);
                if (value instanceof Number) {
                    commandUsageCount.put(key, ((Number) value).longValue());
                }
            }
            user.setCommandUsageCount(commandUsageCount);
        }
        
        // Map custom subreddits
        List<String> customSubreddits = doc.getList("customSubreddits", String.class);
        if (customSubreddits != null) {
            user.setCustomSubreddits(new ArrayList<>(customSubreddits));
        }
        
        // Map subreddit usage count
        Document subredditUsageDoc = doc.get("subredditUsageCount", Document.class);
        if (subredditUsageDoc != null) {
            Map<String, Integer> subredditUsageCount = new java.util.HashMap<>();
            for (String key : subredditUsageDoc.keySet()) {
                Object value = subredditUsageDoc.get(key);
                if (value instanceof Number) {
                    subredditUsageCount.put(key, ((Number) value).intValue());
                }
            }
            user.setSubredditUsageCount(subredditUsageCount);
        }
        
        // Map custom queries
        Document customQueriesDoc = doc.get("customQueries", Document.class);
        if (customQueriesDoc != null) {
            Map<String, List<String>> customQueries = new java.util.HashMap<>();
            for (String service : customQueriesDoc.keySet()) {
                List<String> queries = customQueriesDoc.getList(service, String.class);
                if (queries != null) {
                    customQueries.put(service, new ArrayList<>(queries));
                }
            }
            user.setCustomQueries(customQueries);
        }
        
        // Map total commands used
        user.setTotalCommandsUsed(doc.getLong("totalCommandsUsed") != null ? doc.getLong("totalCommandsUsed") : 0L);
        
        // Map last active date
        String lastActiveDateStr = doc.getString("lastActiveDate");
        if (lastActiveDateStr != null) {
            try {
                user.setLastActiveDate(java.time.LocalDateTime.parse(lastActiveDateStr));
            } catch (Exception e) {
                user.setLastActiveDate(java.time.LocalDateTime.now());
            }
        }
        
        // Map account created date
        String accountCreatedDateStr = doc.getString("accountCreatedDate");
        if (accountCreatedDateStr != null) {
            try {
                user.setAccountCreatedDate(java.time.LocalDateTime.parse(accountCreatedDateStr));
            } catch (Exception e) {
                user.setAccountCreatedDate(java.time.LocalDateTime.now());
            }
        }
    }
    
    private void mapUsageStatisticsToDocument(Document doc, User user) {
        // Map source usage count
        if (user.getSourceUsageCount() != null && !user.getSourceUsageCount().isEmpty()) {
            Document sourceUsageDoc = new Document();
            for (Map.Entry<String, Long> entry : user.getSourceUsageCount().entrySet()) {
                sourceUsageDoc.append(entry.getKey(), entry.getValue());
            }
            doc.append("sourceUsageCount", sourceUsageDoc);
        }
        
        // Map command usage count
        if (user.getCommandUsageCount() != null && !user.getCommandUsageCount().isEmpty()) {
            Document commandUsageDoc = new Document();
            for (Map.Entry<String, Long> entry : user.getCommandUsageCount().entrySet()) {
                commandUsageDoc.append(entry.getKey(), entry.getValue());
            }
            doc.append("commandUsageCount", commandUsageDoc);
        }
        
        // Map custom subreddits
        if (user.getCustomSubreddits() != null) {
            doc.append("customSubreddits", user.getCustomSubreddits());
        }
        
        // Map subreddit usage count
        if (user.getSubredditUsageCount() != null && !user.getSubredditUsageCount().isEmpty()) {
            Document subredditUsageDoc = new Document();
            for (Map.Entry<String, Integer> entry : user.getSubredditUsageCount().entrySet()) {
                subredditUsageDoc.append(entry.getKey(), entry.getValue());
            }
            doc.append("subredditUsageCount", subredditUsageDoc);
        }
        
        // Map custom queries
        if (user.getCustomQueries() != null && !user.getCustomQueries().isEmpty()) {
            Document customQueriesDoc = new Document();
            for (Map.Entry<String, List<String>> entry : user.getCustomQueries().entrySet()) {
                customQueriesDoc.append(entry.getKey(), entry.getValue());
            }
            doc.append("customQueries", customQueriesDoc);
        }
        
        // Map total commands used
        doc.append("totalCommandsUsed", user.getTotalCommandsUsed());
        
        // Map last active date
        if (user.getLastActiveDate() != null) {
            doc.append("lastActiveDate", user.getLastActiveDate().toString());
        }
        
        // Map account created date
        if (user.getAccountCreatedDate() != null) {
            doc.append("accountCreatedDate", user.getAccountCreatedDate().toString());
        }
    }
}
