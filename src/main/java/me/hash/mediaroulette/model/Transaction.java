package me.hash.mediaroulette.model;

import java.time.Instant;

public class Transaction {
    public enum TransactionType {
        QUEST_REWARD,
        QUEST_COMPLETION,
        SHOP_PURCHASE,
        ADMIN_GRANT,
        ADMIN_REMOVE,
        DAILY_BONUS,
        PREMIUM_BONUS,
        GAMBLING_WIN,
        GAMBLING_LOSS,
        REFUND,
        PENALTY,
        GIVEAWAY_WIN,
        VOTE_REWARD
    }

    private String transactionId;
    private String userId;
    private TransactionType type;
    private long amount; // Positive for earning, negative for spending
    private long balanceBefore;
    private long balanceAfter;
    private String description;
    private String metadata; // JSON string for additional data
    private Instant timestamp;
    private String adminId; // For admin-initiated transactions
    private boolean flagged; // For suspicious activity

    public Transaction() {
        this.timestamp = Instant.now();
        this.transactionId = generateTransactionId();
        this.flagged = false;
    }

    public Transaction(String userId, TransactionType type, long amount, long balanceBefore, String description) {
        this();
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceBefore + amount;
        this.description = description;
    }

    private String generateTransactionId() {
        return "TXN_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }

    // Getters and Setters
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public TransactionType getType() { return type; }
    public void setType(TransactionType type) { this.type = type; }

    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }

    public long getBalanceBefore() { return balanceBefore; }
    public void setBalanceBefore(long balanceBefore) { this.balanceBefore = balanceBefore; }

    public long getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(long balanceAfter) { this.balanceAfter = balanceAfter; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getAdminId() { return adminId; }
    public void setAdminId(String adminId) { this.adminId = adminId; }

    public boolean isFlagged() { return flagged; }
    public void setFlagged(boolean flagged) { this.flagged = flagged; }

    // Helper methods
    public boolean isEarning() {
        return amount > 0;
    }

    public boolean isSpending() {
        return amount < 0;
    }

    public String getFormattedAmount() {
        return (amount >= 0 ? "+" : "") + amount + " coins";
    }

    @Override
    public String toString() {
        return String.format("Transaction{id='%s', user='%s', type=%s, amount=%d, desc='%s', time=%s}",
                transactionId, userId, type, amount, description, timestamp);
    }
}