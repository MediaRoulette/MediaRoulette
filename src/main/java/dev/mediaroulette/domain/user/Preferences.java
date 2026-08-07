package dev.mediaroulette.domain.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import dev.mediaroulette.exceptions.InvalidChancesException;
import dev.mediaroulette.exceptions.NoEnabledOptionsException;
import dev.mediaroulette.utils.user.ImageSelector;

public class Preferences {
    public static final int DEFAULT_FAVORITE_LIMIT = 25;
    
    private String userId;
    private List<Favorite> favorites;
    private Map<String, ImageOptions> imageOptions;
    private Map<String, SourcePreference> sourcePreferences;

    
    private java.util.List<String> customSubreddits = new java.util.ArrayList<>();
    private java.util.Map<String, java.util.List<String>> customQueries = new java.util.HashMap<>();
    private static final int MAX_CUSTOM_SUBREDDITS = 20;
    
    public Preferences() {}

    public Preferences(String userId) {
        this.userId = userId;
        this.favorites = new ArrayList<>();
        this.imageOptions = new HashMap<>();
        this.sourcePreferences = new HashMap<>();
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public List<Favorite> getFavorites() { return favorites; }
    public void setFavorites(List<Favorite> favorites) { this.favorites = favorites; }
    
    public Map<String, ImageOptions> getImageOptionsMap() { return imageOptions; }
    public void setImageOptionsMap(Map<String, ImageOptions> imageOptions) { this.imageOptions = imageOptions; }
    
    public Map<String, SourcePreference> getSourcePreferences() { return sourcePreferences; }
    public void setSourcePreferences(Map<String, SourcePreference> sourcePreferences) { this.sourcePreferences = sourcePreferences; }

    public int getFavoriteLimit(boolean isPremium) {
        return isPremium ? DEFAULT_FAVORITE_LIMIT * 2 : DEFAULT_FAVORITE_LIMIT;
    }

    public void addFavorite(String title, String description, String image, String type, Integer accentColor, boolean isPremium) {
        if (favorites.size() >= getFavoriteLimit(isPremium)) {
            return;
        }
        int id = favorites.size();
        favorites.add(new Favorite(id, title, description, image, type, accentColor));
    }

    public void removeFavorite(int id) {
        if (id < 0 || id >= favorites.size()) return;
        favorites.remove(id);
        for (int i = id; i < favorites.size(); i++) {
            favorites.get(i).setId(i);
        }
    }

    public void setChances(ImageOptions... options) {
        for (ImageOptions option : options) {
            imageOptions.put(option.getImageType(), option);
        }
    }

    public ImageOptions getImageOptions(String imageType) {
        return imageOptions.get(imageType);
    }

    public Map<String, String> getImage() throws InvalidChancesException, NoEnabledOptionsException {
        ImageSelector selector = new ImageSelector(imageOptions);
        return selector.selectImage(this.userId);
    }

    public SourcePreference getSourcePreference(String source) {
        if (sourcePreferences == null) sourcePreferences = new HashMap<>();
        return sourcePreferences.computeIfAbsent(source, SourcePreference::new);
    }
    
    public void setSourcePreference(String source, SourcePreference preference) {
        if (sourcePreferences == null) sourcePreferences = new HashMap<>();
        sourcePreferences.put(source, preference);
    }
    
    public <T> T getSourceSetting(String source, String key, T defaultValue) {
        SourcePreference pref = sourcePreferences != null ? sourcePreferences.get(source) : null;
        return pref != null ? pref.getSetting(key, defaultValue) : defaultValue;
    }
    
    public void setSourceSetting(String source, String key, Object value) {
        getSourcePreference(source).setSetting(key, value);
    }

    public java.util.List<String> getCustomSubreddits() {
        return customSubreddits != null ? customSubreddits : new java.util.ArrayList<>();
    }
    
    public void setCustomSubreddits(java.util.List<String> customSubreddits) {
        this.customSubreddits = customSubreddits;
    }
    
    public void addCustomSubreddit(String subreddit) {
        if (customSubreddits == null) customSubreddits = new java.util.ArrayList<>();
        customSubreddits.remove(subreddit);
        customSubreddits.add(0, subreddit);
        if (customSubreddits.size() > MAX_CUSTOM_SUBREDDITS) {
            customSubreddits = customSubreddits.subList(0, MAX_CUSTOM_SUBREDDITS);
        }
    }
    
    public java.util.Map<String, java.util.List<String>> getCustomQueries() {
        return customQueries != null ? customQueries : new java.util.HashMap<>();
    }
    
    public void setCustomQueries(java.util.Map<String, java.util.List<String>> customQueries) {
        this.customQueries = customQueries;
    }
    
    public java.util.List<String> getCustomQueries(String service) {
        if (customQueries == null) return new java.util.ArrayList<>();
        return customQueries.getOrDefault(service, new java.util.ArrayList<>());
    }
    
    public void addCustomQuery(String service, String query) {
        if (customQueries == null) customQueries = new java.util.HashMap<>();
        java.util.List<String> serviceQueries = customQueries.computeIfAbsent(service, k -> new java.util.ArrayList<>());
        serviceQueries.remove(query);
        serviceQueries.add(0, query);
        if (serviceQueries.size() > MAX_CUSTOM_SUBREDDITS) {
            customQueries.put(service, serviceQueries.subList(0, MAX_CUSTOM_SUBREDDITS));
        }
    }
    }