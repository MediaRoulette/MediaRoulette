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
        List<Transaction> result = new ArrayList<>();
        // TODO: implement
        return result;
    }

    @Override
    public Transaction save(Transaction transaction) {
        // TODO: implement
        return transaction;
    }
}
