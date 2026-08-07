package dev.mediaroulette.repository;

import dev.mediaroulette.domain.user.Preferences;
import java.util.Optional;

public interface PreferencesRepository {
    Optional<Preferences> findById(String userId);
    Preferences save(Preferences entity);
    boolean exists(String userId);
}
