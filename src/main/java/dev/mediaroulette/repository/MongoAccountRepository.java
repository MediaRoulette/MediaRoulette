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

    private Account mapToEntity(Document doc) {
        Account account = new Account(doc.getString("_id"));
        if (doc.containsKey("imagesGenerated")) account.setImagesGenerated(doc.getLong("imagesGenerated"));
        if (doc.containsKey("nsfw")) account.setNsfw(doc.getBoolean("nsfw"));
        if (doc.containsKey("premium")) account.setPremium(doc.getBoolean("premium"));
        if (doc.containsKey("admin")) account.setAdmin(doc.getBoolean("admin"));
        if (doc.containsKey("locale")) account.setLocale(doc.getString("locale"));
        if (doc.containsKey("theme")) account.setTheme(doc.getString("theme"));
        if (doc.containsKey("accountCreatedDate")) account.setAccountCreatedDate(java.time.LocalDateTime.parse(doc.getString("accountCreatedDate")));
        if (doc.containsKey("lastActiveDate")) account.setLastActiveDate(java.time.LocalDateTime.parse(doc.getString("lastActiveDate")));
        return account;
    }

    private Document mapToDocument(Account entity) {
        return new Document("_id", entity.getUserId())
                .append("imagesGenerated", entity.getImagesGenerated())
                .append("nsfw", entity.isNsfw())
                .append("premium", entity.isPremium())
                .append("admin", entity.isAdmin())
                .append("locale", entity.getLocale())
                .append("theme", entity.getTheme())
                .append("accountCreatedDate", entity.getAccountCreatedDate() != null ? entity.getAccountCreatedDate().toString() : null)
                .append("lastActiveDate", entity.getLastActiveDate() != null ? entity.getLastActiveDate().toString() : null);
    }
}
