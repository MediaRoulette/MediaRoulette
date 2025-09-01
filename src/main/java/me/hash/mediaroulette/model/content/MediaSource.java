package me.hash.mediaroulette.model.content;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class MediaSource {
    private static final Map<String, MediaSource> REGISTRY = new ConcurrentHashMap<>();

    // Pre-defined built in sources
    public static final MediaSource CHAN_4 = new MediaSource("4CHAN", "4Chan");
    public static final MediaSource PICSUM = new MediaSource("PICSUM", "Picsum");
    public static final MediaSource IMGUR = new MediaSource("IMGUR", "Imgur");
    public static final MediaSource RULE34 = new MediaSource("RULE34", "Rule34");
    public static final MediaSource GOOGLE = new MediaSource("GOOGLE", "Google");
    public static final MediaSource TENOR = new MediaSource("TENOR", "Tenor");
    public static final MediaSource TMDB = new MediaSource("TMDB", "TMDB");
    public static final MediaSource YOUTUBE = new MediaSource("YOUTUBE", "Youtube");
    public static final MediaSource UNKNOWN = new MediaSource("UNKNOWN", "All");
    public static final MediaSource URBAN_DICTIONARY = new MediaSource("URBAN_DICTIONARY", "UrbanDictionary");

    private final String name;
    private final String displayName;

    private MediaSource(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
        REGISTRY.put(name.toLowerCase(), this);
    }

    // Static method for plugins to register new sources
    public static MediaSource register(String name, String displayName) {
        String key = name.toLowerCase();
        return REGISTRY.computeIfAbsent(key, k -> new MediaSource(name, displayName));
    }

    public static MediaSource valueOf(String name) {
        MediaSource source = REGISTRY.get(name.toLowerCase());
        if (source == null) {
            throw new IllegalArgumentException("No MediaSource found for: " + name);
        }
        return source;
    }

    // Get all registered sources
    public static Collection<MediaSource> values() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    // Check if a source exists
    public static boolean exists(String name) {
        return REGISTRY.containsKey(name.toLowerCase());
    }

    public String name() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        MediaSource that = (MediaSource) obj;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}