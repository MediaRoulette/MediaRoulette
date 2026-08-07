package dev.mediaroulette.repository;

import dev.mediaroulette.domain.quest.QuestProgress;
import java.util.Optional;

public interface QuestProgressRepository {
    Optional<QuestProgress> findById(String userId);
    QuestProgress save(QuestProgress entity);
    boolean exists(String userId);
}
