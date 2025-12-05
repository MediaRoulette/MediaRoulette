package me.hash.mediaroulette.utils.vault;

import com.bettercloud.vault.SslConfig;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.response.LogicalResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages secret retrieval from HashiCorp Vault.
 *
 * Features:
 *  - Optional Vault integration (can fall back to environment variables)
 *  - Caching of secrets with periodic refresh
 *  - Simple write/delete helpers for KV v2
 *
 * Expects KV v2 mounted at vault.secret.engine (e.g. "secret") and
 * secrets stored at vault.secret.path (e.g. "mediaroulette"):
 *
 *   vault kv put secret/mediaroulette DISCORD_TOKEN=your_token ...
 */
public class VaultSecretManager {
    private static final Logger logger = LoggerFactory.getLogger(VaultSecretManager.class);
    private static VaultSecretManager instance;

    private final VaultConfig appConfig;
    private final Vault vault;
    private final Map<String, String> secretCache;
    private final boolean vaultEnabled;

    private long lastRefreshTime;
    private static final long CACHE_REFRESH_INTERVAL_MS = 5 * 60 * 1000L; // 5 minutes

    private VaultSecretManager(VaultConfig config) {
        this.appConfig = config;
        this.secretCache = new ConcurrentHashMap<>();

        boolean enabled = config.isEnabled() && config.getVaultToken() != null && !config.getVaultToken().isEmpty();
        Vault v = null;

        if (enabled) {
            try {
                com.bettercloud.vault.VaultConfig vaultConfig = new com.bettercloud.vault.VaultConfig()
                        .address(config.getVaultAddress())
                        .token(config.getVaultToken())
                        .engineVersion(2)                      // KV v2
                        .readTimeout(config.getTimeoutSeconds())
                        .openTimeout(config.getTimeoutSeconds());

                if (config.getVaultNamespace() != null && !config.getVaultNamespace().isEmpty()) {
                    vaultConfig = vaultConfig.nameSpace(config.getVaultNamespace());
                }

                if (!config.isSslVerify()) {
                    SslConfig sslConfig = new SslConfig()
                            .verify(false)
                            .build();
                    vaultConfig = vaultConfig.sslConfig(sslConfig);
                }

                vaultConfig = vaultConfig.build();
                v = new Vault(vaultConfig);

                logger.info("Vault client initialized for: {}", config.getVaultAddress());
                this.vaultEnabled = true;
                this.vault = v;

                // Load initial secrets
                refreshSecrets();
            } catch (VaultException e) {
                logger.error("Failed to initialize Vault client: {}", e.getMessage(), e);
                throw new RuntimeException("Vault initialization failed", e);
            }
        } else {
            this.vaultEnabled = false;
            this.vault = null;
            logger.info("Vault integration disabled - using environment variables only");
        }
    }

    /**
     * Initialize the VaultSecretManager singleton.
     */
    public static synchronized void initialize(VaultConfig config) {
        if (instance == null) {
            instance = new VaultSecretManager(config);
        }
    }

    /**
     * Get the VaultSecretManager singleton instance.
     */
    public static VaultSecretManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("VaultSecretManager not initialized. Call initialize() first.");
        }
        return instance;
    }

    /**
     * Get a secret value by key.
     * Falls back to system environment if Vault is not enabled or secret is not found.
     */
    public String getSecret(String key) {
        return getSecret(key, null);
    }

    /**
     * Get a secret value by key with a default value.
     */
    public String getSecret(String key, String defaultValue) {
        // If Vault is off, use environment variables only
        if (!vaultEnabled) {
            String value = System.getenv(key);
            return value != null ? value : defaultValue;
        }

        // First try cache
        String cached = secretCache.get(key);
        if (cached != null) {
            return cached;
        }

        // Maybe refresh cache if stale
        if (shouldRefreshCache()) {
            refreshSecrets();
            cached = secretCache.get(key);
            if (cached != null) {
                return cached;
            }
        }

        // Fallback: environment variable
        String envValue = System.getenv(key);
        if (envValue != null) {
            return envValue;
        }

        return defaultValue;
    }

    /**
     * Refresh all secrets from Vault (KV v2).
     */
    public synchronized void refreshSecrets() {
        if (!vaultEnabled) {
            return;
        }

        try {
            String path = appConfig.getKvV2LogicalPath();
            LogicalResponse response = vault.logical().read(path);

            if (response != null && response.getRestResponse() != null && response.getRestResponse().getStatus() == 200) {
                Map<String, String> newSecrets = response.getData();
                if (newSecrets != null && !newSecrets.isEmpty()) {
                    secretCache.clear();
                    secretCache.putAll(newSecrets);
                    lastRefreshTime = System.currentTimeMillis();
                    logger.info("Loaded {} secrets from Vault", newSecrets.size());
                } else {
                    logger.warn("Vault path '{}' exists but contains no secrets", path);
                }
            } else {
                int status = response != null && response.getRestResponse() != null ? response.getRestResponse().getStatus() : -1;
                if (status == 403) {
                    logger.error("Vault permission denied (403). Check your token policy includes: path \"secret/data/{}\" {{ capabilities = [\"read\"] }}", appConfig.getSecretPath());
                } else if (status == 404) {
                    logger.warn("Vault path '{}' not found (404). Secrets may not be initialized yet.", path);
                } else {
                    logger.warn("Failed to read from Vault (HTTP {})", status);
                }
            }
        } catch (VaultException e) {
            logger.error("Vault error: {} (HTTP {})", e.getMessage(), e.getHttpStatusCode(), e);
        }
    }

    /**
     * Write or update a single secret key in Vault KV v2.
     * This merges with existing cached secrets and writes the combined map.
     */
    public boolean writeSecret(String key, String value) {
        if (!vaultEnabled) {
            logger.warn("Cannot write secret - Vault is not enabled");
            return false;
        }

        try {
            Map<String, Object> secrets = new HashMap<>(secretCache);
            secrets.put(key, value);

            String path = appConfig.getKvV2LogicalPath();
            vault.logical().write(path, secrets);
            secretCache.put(key, value);

            logger.info("Successfully wrote secret '{}' to Vault at '{}'", key, path);
            return true;
        } catch (VaultException e) {
            logger.error("Failed to write secret to Vault: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Delete a single secret key from Vault KV v2.
     * Effectively rewrites the path without that key.
     */
    public boolean deleteSecret(String key) {
        if (!vaultEnabled) {
            logger.warn("Cannot delete secret - Vault is not enabled");
            return false;
        }

        try {
            Map<String, Object> secrets = new HashMap<>(secretCache);
            secrets.remove(key);

            String path = appConfig.getKvV2LogicalPath();
            vault.logical().write(path, secrets);
            secretCache.remove(key);

            logger.info("Successfully deleted secret '{}' from Vault at '{}'", key, path);
            return true;
        } catch (VaultException e) {
            logger.error("Failed to delete secret from Vault: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if the secret cache should be refreshed.
     */
    private boolean shouldRefreshCache() {
        return (System.currentTimeMillis() - lastRefreshTime) > CACHE_REFRESH_INTERVAL_MS;
    }

    /**
     * Check if Vault is enabled and operational.
     */
    public boolean isVaultEnabled() {
        return vaultEnabled;
    }

    /**
     * Get all cached secrets (for debugging / diagnostics).
     */
    public Map<String, String> getAllSecrets() {
        if (!vaultEnabled) {
            logger.warn("Vault is not enabled, returning empty secret map");
            return new HashMap<>();
        }
        return new HashMap<>(secretCache);
    }

    /**
     * Clear the secret cache.
     */
    public void clearCache() {
        secretCache.clear();
        lastRefreshTime = 0L;
        logger.info("Secret cache cleared");
    }

    /**
     * Test Vault connection by attempting to read the configured path.
     * This method is primarily for testing/diagnostics - not called during normal startup.
     */
    public boolean testConnection() {
        if (!vaultEnabled) {
            return false;
        }

        try {
            String path = appConfig.getKvV2LogicalPath();
            LogicalResponse response = vault.logical().read(path);
            return response != null && response.getRestResponse() != null && response.getRestResponse().getStatus() == 200;
        } catch (VaultException e) {
            return false;
        }
    }
}
