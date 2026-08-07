package dev.mediaroulette.repository;

import dev.mediaroulette.domain.user.Preferences;
import dev.mediaroulette.domain.user.Favorite;
import dev.mediaroulette.domain.user.ImageOptions;
import dev.mediaroulette.domain.user.SourcePreference;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MongoPreferencesRepository implements PreferencesRepository {
    private final MongoCollection<Document> collection;

    public MongoPreferencesRepository(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    @Override
    public Optional<Preferences> findById(String userId) {
        Document doc = collection.find(new Document("_id", userId)).first();
        if (doc != null) {
            return Optional.of(mapToEntity(doc));
        }
        return Optional.empty();
    }

    @Override
    public Preferences save(Preferences entity) {
        Document doc = mapToDocument(entity);
        collection.replaceOne(new Document("_id", entity.getUserId()), doc,
                new com.mongodb.client.model.ReplaceOptions().upsert(true));
        return entity;
    }

    @Override
    public boolean exists(String userId) {
        return collection.find(new Document("_id", userId)).first() != null;
    }

    private Preferences mapToEntity(Document doc) {
        Preferences pref = new Preferences(doc.getString("_id"));
        
        List<Document> favDocs = doc.getList("favorites", Document.class);
        if (favDocs != null) {
            List<Favorite> favorites = new ArrayList<>();
            for (Document favDoc : favDocs) {
                Favorite fav = new Favorite(
                        favDoc.getInteger("id", 0),
                        favDoc.getString("title"),
                        favDoc.getString("description"),
                        favDoc.getString("image"),
                        favDoc.getString("type"),
                        favDoc.getInteger("accentColor")
                );
                favorites.add(fav);
            }
            pref.setFavorites(favorites);
        }
        
        Document imgDoc = doc.get("images", Document.class);
        if (imgDoc != null) {
            Map<String, ImageOptions> options = new HashMap<>();
            for (String key : imgDoc.keySet()) {
                Document optDoc = imgDoc.get(key, Document.class);
                if (optDoc != null) {
                    options.put(key, new ImageOptions(
                            optDoc.getString("imageType"),
                            optDoc.getBoolean("enabled", true),
                            optDoc.getInteger("chance", 0)
                    ));
                }
            }
            pref.setImageOptionsMap(options);
        }
        
        Document sourcePrefsDoc = doc.get("sourcePreferences", Document.class);
        if (sourcePrefsDoc != null) {
            Map<String, SourcePreference> sourcePrefs = new HashMap<>();
            for (String sourceKey : sourcePrefsDoc.keySet()) {
                Document spDoc = sourcePrefsDoc.get(sourceKey, Document.class);
                if (spDoc != null) {
                    SourcePreference sp = new SourcePreference(sourceKey);
                    Document settingsDoc = spDoc.get("settings", Document.class);
                    if (settingsDoc != null) {
                        for (String settingKey : settingsDoc.keySet()) {
                            sp.setSetting(settingKey, settingsDoc.get(settingKey));
                        }
                    }
                    sourcePrefs.put(sourceKey, sp);
                }
            }
            pref.setSourcePreferences(sourcePrefs);
        }
        
        return pref;
    }

    private Document mapToDocument(Preferences entity) {
        Document doc = new Document("_id", entity.getUserId());
        
        List<Document> favDocs = new ArrayList<>();
        for (Favorite fav : entity.getFavorites()) {
            favDocs.add(new Document("id", fav.getId())
                    .append("title", fav.getTitle())
                    .append("description", fav.getDescription())
                    .append("image", fav.getImage())
                    .append("type", fav.getType())
                    .append("accentColor", fav.getAccentColor()));
        }
        doc.append("favorites", favDocs);
        
        Document imagesDoc = new Document();
        for (Map.Entry<String, ImageOptions> entry : entity.getImageOptionsMap().entrySet()) {
            ImageOptions opt = entry.getValue();
            imagesDoc.append(entry.getKey(), new Document("imageType", opt.getImageType())
                    .append("enabled", opt.isEnabled())
                    .append("chance", opt.getChance()));
        }
        doc.append("images", imagesDoc);
        
        Document sourcePrefsDoc = new Document();
        if (entity.getSourcePreferences() != null) {
            for (Map.Entry<String, SourcePreference> entry : entity.getSourcePreferences().entrySet()) {
                SourcePreference pref = entry.getValue();
                Document settingsDoc = new Document();
                if (pref.getSettings() != null) {
                    for (Map.Entry<String, Object> setting : pref.getSettings().entrySet()) {
                        settingsDoc.append(setting.getKey(), setting.getValue());
                    }
                }
                sourcePrefsDoc.append(entry.getKey(), new Document("source", pref.getSource()).append("settings", settingsDoc));
            }
        }
        doc.append("sourcePreferences", sourcePrefsDoc);
        
        return doc;
    }
}
