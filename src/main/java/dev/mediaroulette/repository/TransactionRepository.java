package dev.mediaroulette.repository;

import dev.mediaroulette.domain.economy.Transaction;
import java.util.List;

public interface TransactionRepository {
    List<Transaction> findByUserId(String userId, int limit);
    Transaction save(Transaction transaction);
}
