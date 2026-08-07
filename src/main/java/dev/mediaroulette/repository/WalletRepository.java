package dev.mediaroulette.repository;

import dev.mediaroulette.domain.economy.Wallet;
import java.util.Optional;

public interface WalletRepository {
    Optional<Wallet> findById(String userId);
    Wallet save(Wallet entity);
    boolean exists(String userId);
}
