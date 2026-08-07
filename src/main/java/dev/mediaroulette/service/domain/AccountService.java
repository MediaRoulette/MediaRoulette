package dev.mediaroulette.service.domain;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.mediaroulette.domain.user.Account;
import dev.mediaroulette.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AccountService {
    private static final Logger logger = LoggerFactory.getLogger(AccountService.class);
    
    private final AccountRepository repository;
    private final Cache<String, Account> cache;
    private final Set<String> dirtyIds = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService writeScheduler;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
        this.cache = Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build();
                
        this.writeScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AccountService-Writer");
            t.setDaemon(true);
            return t;
        });
        this.writeScheduler.scheduleAtFixedRate(this::flushDirty, 5, 5, TimeUnit.SECONDS);
    }

    public Account getAccount(String userId) {
        return cache.get(userId, id -> repository.findById(id).orElseGet(() -> {
            Account newAccount = new Account(id);
            return repository.save(newAccount);
        }));
    }

    public void updateAccount(Account account) {
        cache.put(account.getUserId(), account);
        dirtyIds.add(account.getUserId());
    }

    public void setNsfwEnabled(String userId, boolean enabled) {
        Account account = getAccount(userId);
        account.setNsfw(enabled);
        updateAccount(account);
    }

    public void setPremium(String userId, boolean premium) {
        Account account = getAccount(userId);
        account.setPremium(premium);
        updateAccount(account);
    }

    public void setAdmin(String userId, boolean admin) {
        Account account = getAccount(userId);
        account.setAdmin(admin);
        updateAccount(account);
    }

    public void setLocale(String userId, String locale) {
        Account account = getAccount(userId);
        account.setLocale(locale);
        updateAccount(account);
    }

    public void setTheme(String userId, String theme) {
        Account account = getAccount(userId);
        account.setTheme(theme);
        updateAccount(account);
    }

    public void incrementImagesGenerated(String userId) {
        Account account = getAccount(userId);
        account.setImagesGenerated(account.getImagesGenerated() + 1);
        updateAccount(account);
    }

    public void updateLastActive(String userId) {
        Account account = getAccount(userId);
        account.setLastActiveDate(java.time.LocalDateTime.now());
        updateAccount(account);
    }

    private void flushDirty() {
        if (dirtyIds.isEmpty()) return;
        List<String> idsToFlush = new ArrayList<>(dirtyIds);
        dirtyIds.removeAll(idsToFlush);
        
        for (String id : idsToFlush) {
            Account account = cache.getIfPresent(id);
            if (account != null) {
                try {
                    repository.save(account);
                } catch (Exception e) {
                    logger.error("Failed to save Account {} during flush: {}", id, e.getMessage());
                    dirtyIds.add(id); // Retry next time
                }
            }
        }
    }

    public void shutdown() {
        if (writeScheduler != null) {
            writeScheduler.shutdown();
            try {
                if (!writeScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    writeScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                writeScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        flushDirty();
    }
}
