// MediaSource.java - Enum for different sources
package me.hash.mediaroulette.model.content;

public enum MediaSource {
    CHAN_4("4Chan"),
    PICSUM("Picsum"),
    IMGUR("Imgur"),
    RULE34("Rule34"),
    GOOGLE("Google"),
    TENOR("Tenor"),
    TMDB("TMDB"),
    YOUTUBE("Youtube"),
    UNKNOWN("All"),
    URBAN_DICTIONARY("UrbanDictionary");

    private final String displayName;

    MediaSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}