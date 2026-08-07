package dev.mediaroulette.repository;

import dev.mediaroulette.domain.user.UsageStats;
import java.util.Optional;

public interface UsageStatsRepository {
    Optional<UsageStats> findById(String userId);
    UsageStats save(UsageStats entity);
    boolean exists(String userId);
}
