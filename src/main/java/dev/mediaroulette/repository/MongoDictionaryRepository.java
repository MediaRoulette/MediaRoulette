package dev.mediaroulette.repository;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import dev.mediaroulette.model.Dictionary;
import dev.mediaroulette.model.DictionaryAssignment;
import org.bson.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class MongoDictionaryRepository implements DictionaryRepository {
    private final MongoCollection<Document> dictionaryCollection;
    private final MongoCollection<Document> assignmentCollection;
    
    public MongoDictionaryRepository(MongoCollection<Document> dictionaryCollection, 
                                   MongoCollection<Document> assignmentCollection) {
        this.dictionaryCollection = dictionaryCollection;
        this.assignmentCollection = assignmentCollection;
    }
    
    @Override
    public Optional<Dictionary> findById(String id) {
        Document doc = dictionaryCollection.find(Filters.eq("_id", id)).first();
        return doc != null ? Optional.of(mapDocumentToDictionary(doc)) : Optional.empty();
    }
    
    @Override
    public Dictionary save(Dictionary dictionary) {
        // Ensure updatedAt is set when saving
        dictionary.setUpdatedAt(java.time.Instant.now());
        
        Document doc = mapDictionaryToDocument(dictionary);
        dictionaryCollection.replaceOne(
            Filters.eq("_id", dictionary.getId()), 
            doc, 
            new com.mongodb.client.model.ReplaceOptions().upsert(true)
        );
        return dictionary;
    }
    
    @Override
    public boolean delete(String id) {
        long deletedCount = dictionaryCollection.deleteOne(Filters.eq("_id", id)).getDeletedCount();
        // Also delete any assignments using this dictionary
        assignmentCollection.deleteMany(Filters.eq("dictionaryId", id));
        return deletedCount > 0;
    }
    
    @Override
    public boolean exists(String id) {
        return dictionaryCollection.find(Filters.eq("_id", id)).first() != null;
    }
    
    @Override
    public List<Dictionary> findByCreatedBy(String userId) {
        List<Dictionary> dictionaries = new ArrayList<>();
        for (Document doc : dictionaryCollection.find(Filters.eq("createdBy", userId))) {
            dictionaries.add(mapDocumentToDictionary(doc));
        }
        return dictionaries;
    }
    
    @Override
    public List<Dictionary> findPublicDictionaries() {
        List<Dictionary> dictionaries = new ArrayList<>();
        for (Document doc : dictionaryCollection.find(Filters.eq("isPublic", true))
                .sort(Sorts.descending("usageCount"))) {
            dictionaries.add(mapDocumentToDictionary(doc));
        }
        return dictionaries;
    }
    
    @Override
    public List<Dictionary> findDefaultDictionaries() {
        List<Dictionary> dictionaries = new ArrayList<>();
        for (Document doc : dictionaryCollection.find(Filters.eq("isDefault", true))) {
            dictionaries.add(mapDocumentToDictionary(doc));
        }
        return dictionaries;
    }
    
    @Override
    public List<Dictionary> findAccessibleDictionaries(String userId) {
        List<Dictionary> dictionaries = new ArrayList<>();
        // Find public, default, or user's own dictionaries
        for (Document doc : dictionaryCollection.find(
                Filters.or(
                    Filters.eq("isPublic", true),
                    Filters.eq("isDefault", true),
                    Filters.eq("createdBy", userId)
                )
        ).sort(Sorts.descending("usageCount"))) {
            dictionaries.add(mapDocumentToDictionary(doc));
        }
        return dictionaries;
    }
    
    @Override
    public List<Dictionary> findByNameContaining(String namePattern, String userId) {
        List<Dictionary> dictionaries = new ArrayList<>();
        Pattern pattern = Pattern.compile(namePattern, Pattern.CASE_INSENSITIVE);
        
        for (Document doc : dictionaryCollection.find(
                Filters.and(
                    Filters.regex("name", pattern),
                    Filters.or(
                        Filters.eq("isPublic", true),
                        Filters.eq("isDefault", true),
                        Filters.eq("createdBy", userId)
                    )
                )
        )) {
            dictionaries.add(mapDocumentToDictionary(doc));
        }
        return dictionaries;
    }
    
    @Override
    public DictionaryAssignment saveAssignment(DictionaryAssignment assignment) {
        Document doc = mapAssignmentToDocument(assignment);
        assignmentCollection.replaceOne(
            Filters.and(
                Filters.eq("userId", assignment.getUserId()),
                Filters.eq("source", assignment.getSource())
            ),
            doc,
            new com.mongodb.client.model.ReplaceOptions().upsert(true)
        );
        return assignment;
    }
    
    @Override
    public Optional<DictionaryAssignment> findAssignment(String userId, String source) {
        Document doc = assignmentCollection.find(
            Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("source", source)
            )
        ).first();
        return doc != null ? Optional.of(mapDocumentToAssignment(doc)) : Optional.empty();
    }
    
    @Override
    public List<DictionaryAssignment> findAssignmentsByUser(String userId) {
        List<DictionaryAssignment> assignments = new ArrayList<>();
        for (Document doc : assignmentCollection.find(Filters.eq("userId", userId))) {
            assignments.add(mapDocumentToAssignment(doc));
        }
        return assignments;
    }
    
    @Override
    public boolean deleteAssignment(String userId, String source) {
        long deletedCount = assignmentCollection.deleteOne(
            Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("source", source)
            )
        ).getDeletedCount();
        return deletedCount > 0;
    }
    
    @Override
    public void incrementDictionaryUsage(String dictionaryId) {
        dictionaryCollection.updateOne(
            Filters.eq("_id", dictionaryId),
            Updates.inc("usageCount", 1)
        );
    }
    
    @Override
    public List<Dictionary> findMostUsedDictionaries(int limit) {
        List<Dictionary> dictionaries = new ArrayList<>();
        for (Document doc : dictionaryCollection.find(Filters.eq("isPublic", true))
                .sort(Sorts.descending("usageCount"))
                .limit(limit)) {
            dictionaries.add(mapDocumentToDictionary(doc));
        }
        return dictionaries;
    }
    
    // Helper methods for mapping
    private Dictionary mapDocumentToDictionary(Document doc) {
        Dictionary dictionary = new Dictionary();
        dictionary.setId(doc.getString("_id"));
        dictionary.setName(doc.getString("name"));
        dictionary.setDescription(doc.getString("description"));
        dictionary.setCreatedBy(doc.getString("createdBy"));
        
        String createdAtStr = doc.getString("createdAt");
        if (createdAtStr != null) {
            dictionary.setCreatedAt(Instant.parse(createdAtStr));
        }
        
        String updatedAtStr = doc.getString("updatedAt");
        if (updatedAtStr != null) {
            dictionary.setUpdatedAt(Instant.parse(updatedAtStr));
        }
        
        List<String> words = (List<String>) doc.get("words", new ArrayList<String>());
        dictionary.setWords(new ArrayList<>(words));
        
        dictionary.setPublic(doc.getBoolean("isPublic", false));
        dictionary.setDefault(doc.getBoolean("isDefault", false));
        dictionary.setUsageCount(doc.getInteger("usageCount", 0));
        
        return dictionary;
    }
    
    private Document mapDictionaryToDocument(Dictionary dictionary) {
        return new Document("_id", dictionary.getId())
                .append("name", dictionary.getName())
                .append("description", dictionary.getDescription())
                .append("createdBy", dictionary.getCreatedBy())
                .append("createdAt", dictionary.getCreatedAt().toString())
                .append("updatedAt", dictionary.getUpdatedAt().toString())
                .append("words", dictionary.getWords())
                .append("isPublic", dictionary.isPublic())
                .append("isDefault", dictionary.isDefault())
                .append("usageCount", dictionary.getUsageCount());
    }
    
    private DictionaryAssignment mapDocumentToAssignment(Document doc) {
        DictionaryAssignment assignment = new DictionaryAssignment();
        assignment.setUserId(doc.getString("userId"));
        assignment.setSource(doc.getString("source"));
        assignment.setDictionaryId(doc.getString("dictionaryId"));
        
        String assignedAtStr = doc.getString("assignedAt");
        if (assignedAtStr != null) {
            assignment.setAssignedAt(Instant.parse(assignedAtStr));
        }
        
        String lastUsedStr = doc.getString("lastUsed");
        if (lastUsedStr != null) {
            assignment.setLastUsed(Instant.parse(lastUsedStr));
        }
        
        assignment.setUsageCount(doc.getInteger("usageCount", 0));
        
        return assignment;
    }
    
    private Document mapAssignmentToDocument(DictionaryAssignment assignment) {
        Document doc = new Document("userId", assignment.getUserId())
                .append("source", assignment.getSource())
                .append("dictionaryId", assignment.getDictionaryId())
                .append("assignedAt", assignment.getAssignedAt().toString())
                .append("usageCount", assignment.getUsageCount());
        
        if (assignment.getLastUsed() != null) {
            doc.append("lastUsed", assignment.getLastUsed().toString());
        }
        
        return doc;
    }
}