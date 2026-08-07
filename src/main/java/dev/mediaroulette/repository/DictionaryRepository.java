package dev.mediaroulette.repository;

import dev.mediaroulette.model.Dictionary;
import dev.mediaroulette.model.DictionaryAssignment;

import java.util.List;
import java.util.Optional;

public interface DictionaryRepository {
    
    // Dictionary CRUD operations
    Optional<Dictionary> findById(String id);
    Dictionary save(Dictionary dictionary);
    boolean delete(String id);
    boolean exists(String id);
    
    // Dictionary queries
    List<Dictionary> findByCreatedBy(String userId);
    List<Dictionary> findPublicDictionaries();
    List<Dictionary> findDefaultDictionaries();
    List<Dictionary> findAccessibleDictionaries(String userId); // public + user's own + defaults
    List<Dictionary> findByNameContaining(String namePattern, String userId);
    
    // Dictionary assignment operations
    DictionaryAssignment saveAssignment(DictionaryAssignment assignment);
    Optional<DictionaryAssignment> findAssignment(String userId, String source);
    List<DictionaryAssignment> findAssignmentsByUser(String userId);
    boolean deleteAssignment(String userId, String source);
    
    // Statistics and usage
    void incrementDictionaryUsage(String dictionaryId);
    List<Dictionary> findMostUsedDictionaries(int limit);
}