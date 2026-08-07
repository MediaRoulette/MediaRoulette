package dev.mediaroulette.domain.economy;

public class Wallet {
    private String userId;
    private long coins;
    private long totalCoinsEarned;
    private long totalCoinsSpent;

    public Wallet() {}

    public Wallet(String userId) {
        this.userId = userId;
        this.coins = 100; // Starting balance
        this.totalCoinsEarned = 100; // Starting coins count as earned
        this.totalCoinsSpent = 0;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public long getCoins() { return coins; }
    public void setCoins(long coins) { this.coins = coins; }
    
    public long getTotalCoinsEarned() { return totalCoinsEarned; }
    public void setTotalCoinsEarned(long totalCoinsEarned) { this.totalCoinsEarned = totalCoinsEarned; }
    
    public long getTotalCoinsSpent() { return totalCoinsSpent; }
    public void setTotalCoinsSpent(long totalCoinsSpent) { this.totalCoinsSpent = totalCoinsSpent; }

    public void addCoins(long amount) {
        if (amount > 0) {
            this.coins += amount;
            this.totalCoinsEarned += amount;
        }
    }

    public boolean spendCoins(long amount) {
        if (amount > 0 && this.coins >= amount) {
            this.coins -= amount;
            this.totalCoinsSpent += amount;
            return true;
        }
        return false;
    }

    public boolean canAfford(long amount) {
        return this.coins >= amount;
    }

    public long getNetWorth() {
        return this.totalCoinsEarned - this.totalCoinsSpent;
    }
}
