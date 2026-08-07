package dev.mediaroulette.repository;

import dev.mediaroulette.model.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(String userId);
    User save(User user);
    boolean exists(String userId);
    long getTotalUsers();
    long getTotalImagesGenerated();
}
