package dev.mediaroulette.repository;

import dev.mediaroulette.domain.economy.Inventory;
import java.util.Optional;

public interface InventoryRepository {
    Optional<Inventory> findById(String userId);
    Inventory save(Inventory entity);
    boolean exists(String userId);
}
