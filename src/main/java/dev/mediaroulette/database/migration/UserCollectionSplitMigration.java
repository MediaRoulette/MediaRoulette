package dev.mediaroulette.database.migration;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class UserCollectionSplitMigration {
    private static final Logger logger = LoggerFactory.getLogger(UserCollectionSplitMigration.class);

    private final MongoDatabase database;

    public UserCollectionSplitMigration(MongoDatabase database) {
        this.database = database;
    }

    public void runMigration() {
        MongoCollection<Document> legacyCollection = database.getCollection("user");
        if (legacyCollection.countDocuments() == 0) {
            logger.info("Legacy 'user' collection is empty. Nothing to migrate.");
            return;
        }

        MongoCollection<Document> accounts = database.getCollection("accounts");
        MongoCollection<Document> wallets = database.getCollection("wallets");
        MongoCollection<Document> transactions = database.getCollection("transactions");
        MongoCollection<Document> preferences = database.getCollection("preferences");
        MongoCollection<Document> questProgress = database.getCollection("quest_progress");
        MongoCollection<Document> inventories = database.getCollection("inventories");
        MongoCollection<Document> usageStats = database.getCollection("usage_stats");

        logger.info("Starting User collection split migration...");
        long count = 0;

        try (MongoCursor<Document> cursor = legacyCollection.find().iterator()) {
            while (cursor.hasNext()) {
                Document legacyDoc = cursor.next();
                String userId = legacyDoc.getString("_id");
                
                if (userId == null) continue;

                // 1. Account
                Document accountDoc = new Document("_id", userId)
                        .append("imagesGenerated", legacyDoc.getLong("imagesGenerated"))
                        .append("nsfw", legacyDoc.getBoolean("nsfw"))
                        .append("premium", legacyDoc.getBoolean("premium"))
                        .append("admin", legacyDoc.getBoolean("admin"))
                        .append("locale", legacyDoc.getString("locale"))
                        .append("theme", legacyDoc.getString("theme"))
                        .append("accountCreatedDate", legacyDoc.getString("accountCreatedDate"))
                        .append("lastActiveDate", legacyDoc.getString("lastActiveDate"));
                accounts.replaceOne(new Document("_id", userId), accountDoc, new com.mongodb.client.model.ReplaceOptions().upsert(true));

                // 2. Wallet
                Document walletDoc = new Document("_id", userId)
                        .append("coins", legacyDoc.getLong("coins"))
                        .append("totalCoinsEarned", legacyDoc.getLong("totalCoinsEarned"))
                        .append("totalCoinsSpent", legacyDoc.getLong("totalCoinsSpent"));
                wallets.replaceOne(new Document("_id", userId), walletDoc, new com.mongodb.client.model.ReplaceOptions().upsert(true));

                // 3. Transactions
                List<Document> transactionList = legacyDoc.getList("transactionHistory", Document.class);
                if (transactionList != null && !transactionList.isEmpty()) {
                    for (Document tx : transactionList) {
                        // Ensure userId is present on individual transaction docs
                        tx.put("userId", userId);
                    }
                    // We simply insert many, since they are historic. If running multiple times, we might get duplicates unless we upsert by transactionId.
                    for (Document tx : transactionList) {
                        String txId = tx.getString("transactionId");
                        if (txId != null) {
                            transactions.replaceOne(new Document("transactionId", txId), tx, new com.mongodb.client.model.ReplaceOptions().upsert(true));
                        }
                    }
                }

                // 4. Preferences
                Document prefDoc = new Document("_id", userId)
                        .append("favorites", legacyDoc.get("favorites"))
                        .append("images", legacyDoc.get("images"))
                        .append("sourcePreferences", legacyDoc.get("sourcePreferences"));
                preferences.replaceOne(new Document("_id", userId), prefDoc, new com.mongodb.client.model.ReplaceOptions().upsert(true));

                // 5. Quest Progress
                Document questDoc = new Document("_id", userId)
                        .append("dailyQuests", legacyDoc.get("dailyQuests"))
                        .append("lastQuestReset", legacyDoc.getString("lastQuestReset"))
                        .append("totalQuestsCompleted", legacyDoc.getLong("totalQuestsCompleted"))
                        .append("questsCompletedToday", legacyDoc.getLong("questsCompletedToday"))
                        .append("lastQuestCompletionDate", legacyDoc.getString("lastQuestCompletionDate"));
                questProgress.replaceOne(new Document("_id", userId), questDoc, new com.mongodb.client.model.ReplaceOptions().upsert(true));

                // 6. Inventories
                Document invDoc = new Document("_id", userId)
                        .append("items", legacyDoc.get("inventory"));
                inventories.replaceOne(new Document("_id", userId), invDoc, new com.mongodb.client.model.ReplaceOptions().upsert(true));

                // 7. Usage Stats
                Document statsDoc = new Document("_id", userId)
                        .append("sourceUsageCount", legacyDoc.get("sourceUsageCount"))
                        .append("commandUsageCount", legacyDoc.get("commandUsageCount"))
                        .append("customSubreddits", legacyDoc.get("customSubreddits"))
                        .append("subredditUsageCount", legacyDoc.get("subredditUsageCount"))
                        .append("customQueries", legacyDoc.get("customQueries"))
                        .append("totalCommandsUsed", legacyDoc.getLong("totalCommandsUsed"));
                usageStats.replaceOne(new Document("_id", userId), statsDoc, new com.mongodb.client.model.ReplaceOptions().upsert(true));

                count++;
                if (count % 100 == 0) {
                    logger.info("Migrated {} users...", count);
                }
            }
        }

        logger.info("Successfully migrated {} user documents to separated collections.", count);
        
        // Rename the old collection to prevent using it accidentally
        try {
            legacyCollection.renameCollection(new com.mongodb.MongoNamespace(database.getName(), "user_legacy_backup"));
            logger.info("Renamed legacy 'user' collection to 'user_legacy_backup'");
        } catch (Exception e) {
            logger.warn("Could not rename legacy collection, it might have already been renamed. Error: {}", e.getMessage());
        }
    }
}
