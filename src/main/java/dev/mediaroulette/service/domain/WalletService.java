package dev.mediaroulette.service.domain;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.mediaroulette.domain.economy.Wallet;
import dev.mediaroulette.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WalletService {
    private static final Logger logger = LoggerFactory.getLogger(WalletService.class);
    
    private final WalletRepository repository;
    private final Cache<String, Wallet> cache;
    private final Set<String> dirtyIds = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService writeScheduler;

    public WalletService(WalletRepository repository) {
        this.repository = repository;
        this.cache = Caffeine.newBuilder()
                .maximumSize(5000)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build();
                
        this.writeScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "WalletService-Writer");
            t.setDaemon(true);
            return t;
        });
        this.writeScheduler.scheduleAtFixedRate(this::flushDirty, 5, 5, TimeUnit.SECONDS);
    }

    public Wallet getWallet(String userId) {
        return cache.get(userId, id -> repository.findById(id).orElseGet(() -> {
            Wallet newEntity = new Wallet(id);
            return repository.save(newEntity);
        }));
    }

    public void updateWallet(Wallet entity) {
        cache.put(entity.getUserId(), entity);
        dirtyIds.add(entity.getUserId());
    }

    private void flushDirty() {
        if (dirtyIds.isEmpty()) return;
        List<String> idsToFlush = new ArrayList<>(dirtyIds);
        dirtyIds.removeAll(idsToFlush);
        
        for (String id : idsToFlush) {
            Wallet entity = cache.getIfPresent(id);
            if (entity != null) {
                try {
                    repository.save(entity);
                } catch (Exception e) {
                    logger.error("Failed to save Wallet {} during flush: {}", id, e.getMessage());
                    dirtyIds.add(id);
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
