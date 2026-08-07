package dev.mediaroulette.repository;

import dev.mediaroulette.domain.user.Account;
import java.util.Optional;

public interface AccountRepository {
    Optional<Account> findById(String userId);
    Account save(Account entity);
    boolean exists(String userId);
}
