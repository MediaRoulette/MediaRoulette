package dev.mediaroulette.domain.user;

public class Account {
    private String userId;
    private long imagesGenerated;
    private boolean nsfw;
    private boolean premium;
    private boolean admin;
    private String locale;
    private String theme;
    private java.time.LocalDateTime accountCreatedDate;
    private java.time.LocalDateTime lastActiveDate;

    public Account() {}

    public Account(String userId) {
        this.userId = userId;
        this.imagesGenerated = 0;
        this.nsfw = false;
        this.premium = false;
        this.admin = false;
        this.locale = "en_US";
        this.theme = "default";
        this.accountCreatedDate = java.time.LocalDateTime.now();
        this.lastActiveDate = java.time.LocalDateTime.now();
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public long getImagesGenerated() { return imagesGenerated; }
    public void setImagesGenerated(long imagesGenerated) { this.imagesGenerated = imagesGenerated; }
    public void incrementImagesGenerated() { this.imagesGenerated++; }
    
    public boolean isNsfw() { return nsfw; }
    public void setNsfw(boolean nsfw) { this.nsfw = nsfw; }
    
    public boolean isPremium() { return premium; }
    public void setPremium(boolean premium) { this.premium = premium; }
    
    public boolean isAdmin() { return admin; }
    public void setAdmin(boolean admin) { this.admin = admin; }
    
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    
    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }
    
    public java.time.LocalDateTime getAccountCreatedDate() { return accountCreatedDate; }
    public void setAccountCreatedDate(java.time.LocalDateTime accountCreatedDate) { this.accountCreatedDate = accountCreatedDate; }
    
    public java.time.LocalDateTime getLastActiveDate() { return lastActiveDate; }
    public void setLastActiveDate(java.time.LocalDateTime lastActiveDate) { this.lastActiveDate = lastActiveDate; }
    
    public void updateLastActive() {
        this.lastActiveDate = java.time.LocalDateTime.now();
    }
}
