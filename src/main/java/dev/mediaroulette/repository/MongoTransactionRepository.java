package dev.mediaroulette.repository;

import dev.mediaroulette.domain.economy.Transaction;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import java.util.ArrayList;
import java.util.List;

public class MongoTransactionRepository implements TransactionRepository {
    private final MongoCollection<Document> collection;

    public MongoTransactionRepository(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    @Override
    public List<Transaction> findByUserId(String userId, int limit) {
        List<Transaction> transactions = new ArrayList<>();
        com.mongodb.client.FindIterable<Document> docs = collection.find(new Document("userId", userId))
                .sort(new Document("timestamp", -1))
                .limit(limit);
                
        for (Document doc : docs) {
            transactions.add(mapToEntity(doc));
        }
        return transactions;
    }

    @Override
    public Transaction save(Transaction transaction) {
        Document doc = mapToDocument(transaction);
        collection.replaceOne(new Document("transactionId", transaction.getTransactionId()), doc,
                new com.mongodb.client.model.ReplaceOptions().upsert(true));
        return transaction;
    }

    private Transaction mapToEntity(Document doc) {
        Transaction tx = new Transaction();
        if (doc.containsKey("transactionId")) tx.setTransactionId(doc.getString("transactionId"));
        if (doc.containsKey("userId")) tx.setUserId(doc.getString("userId"));
        if (doc.containsKey("amount")) tx.setAmount(doc.getLong("amount"));
        if (doc.containsKey("type")) tx.setType(Transaction.TransactionType.valueOf(doc.getString("type")));
        if (doc.containsKey("description")) tx.setDescription(doc.getString("description"));
        if (doc.containsKey("timestamp")) tx.setTimestamp(java.time.Instant.parse(doc.getString("timestamp")));
        if (doc.containsKey("adminId")) tx.setAdminId(doc.getString("adminId"));
        
        return tx;
    }

    private Document mapToDocument(Transaction tx) {
        return new Document("transactionId", tx.getTransactionId())
                .append("userId", tx.getUserId())
                .append("amount", tx.getAmount())
                .append("type", tx.getType().name())
                .append("description", tx.getDescription())
                .append("timestamp", tx.getTimestamp() != null ? tx.getTimestamp().toString() : null)
                .append("adminId", tx.getAdminId())
                ;
    }
}
