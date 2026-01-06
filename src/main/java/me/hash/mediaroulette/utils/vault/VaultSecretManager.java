package me.hash.mediaroulette.utils.vault;

import com.bettercloud.vault.SslConfig;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.response.AuthResponse;
import com.bettercloud.vault.response.LogicalResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages secrets from HashiCorp Vault with AppRole authentication.
 * Vault is disabled by default. Caches secrets for 5 minutes.
 * Automatically re-authenticates on token expiry or auth failures.
 */
public class VaultSecretManager {
    private static final Logger logger = LoggerFactory.getLogger(VaultSecretManager.class);
    private static VaultSecretManager instance;

    private final VaultConfig appConfig;
    private Vault vault;
    private final Map<String, String> secretCache;
    private final boolean vaultEnabled;

    private String clientToken;
    private long tokenExpiryTime;
    private long lastRefreshTime;
    
    private static final long CACHE_REFRESH_INTERVAL_MS = 5 * 60 * 1000L; // 5 minutes
    private static final long TOKEN_RENEW_BUFFER_MS = 60 * 1000L; // Renew 60s before expiry
    private static final int MAX_AUTH_RETRIES = 3;

    private VaultSecretManager(VaultConfig config) {
        this.appConfig = config;
        this.secretCache = new ConcurrentHashMap<>();

        boolean enabled = config.isEnabled() 
                && config.getRoleId() != null && !config.getRoleId().isEmpty()
                && config.getSecretId() != null && !config.getSecretId().isEmpty();

        logger.info("Vault Init Debug: enabled={} (config.enabled={}, roleIdPresent={}, secretIdPresent={})", 
                enabled, config.isEnabled(), 
                config.getRoleId() != null && !config.getRoleId().isEmpty(),
                config.getSecretId() != null && !config.getSecretId().isEmpty());

        if (enabled) {
            boolean loginSuccess = false;
            try {
                // Initial login to get client token
                loginSuccess = login();
            } catch (Exception e) {
                logger.error("Failed to login to Vault: {}", e.getMessage());
                loginSuccess = false;
            }
            
            if (loginSuccess) {
                logger.info("Vault client initialized with AppRole for: {}", config.getVaultAddress());
                this.vaultEnabled = true;
                // Load initial secrets
                refreshSecrets();
            } else {
                logger.warn("Vault login failed - falling back to .env and environment variables");
                this.vaultEnabled = false;
            }
        } else {
            this.vaultEnabled = false;
            this.vault = null;
            logger.info("Vault is disabled - using .env and environment variables");
        }
    }

    /**
     * Authenticate with Vault using AppRole credentials.
     * @return true if login successful, false otherwise
     */
    private boolean login() {
        try {
            // Create unauthenticated Vault config for login
            com.bettercloud.vault.VaultConfig loginConfig = new com.bettercloud.vault.VaultConfig()
                    .address(appConfig.getVaultAddress())
                    .engineVersion(2)
                    .readTimeout(appConfig.getTimeoutSeconds())
                    .openTimeout(appConfig.getTimeoutSeconds());

            if (appConfig.getVaultNamespace() != null && !appConfig.getVaultNamespace().isEmpty()) {
                loginConfig = loginConfig.nameSpace(appConfig.getVaultNamespace());
            }

            if (!appConfig.isSslVerify()) {
                SslConfig sslConfig = new SslConfig()
                        .verify(false)
                        .build();
                loginConfig = loginConfig.sslConfig(sslConfig);
            }

            loginConfig = loginConfig.build();
            Vault loginVault = new Vault(loginConfig);

            // Perform AppRole login
            AuthResponse authResponse = loginVault.auth().loginByAppRole(
                    appConfig.getRoleId(),
                    appConfig.getSecretId()
            );

            if (authResponse != null && authResponse.getAuthClientToken() != null) {
                this.clientToken = authResponse.getAuthClientToken();
                
                // Calculate token expiry time
                long leaseDurationSeconds = authResponse.getAuthLeaseDuration();
                this.tokenExpiryTime = System.currentTimeMillis() + (leaseDurationSeconds * 1000L);
                
                logger.info("AppRole login successful. Token valid for {} seconds", leaseDurationSeconds);
                
                // Create authenticated Vault client
                this.vault = createAuthenticatedVault();
                return true;
            } else {
                logger.error("AppRole login returned null or empty token");
                return false;
            }
        } catch (VaultException e) {
            logger.error("AppRole login failed: {} (HTTP {})", e.getMessage(), e.getHttpStatusCode(), e);
            return false;
        }
    }

    /**
     * Create an authenticated Vault client using the current client token.
     */
    private Vault createAuthenticatedVault() throws VaultException {
        com.bettercloud.vault.VaultConfig vaultConfig = new com.bettercloud.vault.VaultConfig()
                .address(appConfig.getVaultAddress())
                .token(clientToken)
                .engineVersion(2)
                .readTimeout(appConfig.getTimeoutSeconds())
                .openTimeout(appConfig.getTimeoutSeconds());

        if (appConfig.getVaultNamespace() != null && !appConfig.getVaultNamespace().isEmpty()) {
            vaultConfig = vaultConfig.nameSpace(appConfig.getVaultNamespace());
        }

        if (!appConfig.isSslVerify()) {
            SslConfig sslConfig = new SslConfig()
                    .verify(false)
                    .build();
            vaultConfig = vaultConfig.sslConfig(sslConfig);
        }

        vaultConfig = vaultConfig.build();
        return new Vault(vaultConfig);
    }

    /**
     * Check if token is expired or about to expire.
     */
    private boolean isTokenExpired() {
        return System.currentTimeMillis() >= (tokenExpiryTime - TOKEN_RENEW_BUFFER_MS);
    }

    /**
     * Ensure we have a valid token, re-authenticating if necessary.
     * @return true if we have a valid token, false otherwise
     */
    private synchronized boolean ensureValidToken() {
        if (clientToken == null || isTokenExpired()) {
            logger.info("Token expired or missing, re-authenticating...");
            return login();
        }
        return true;
    }

    public static synchronized void initialize(VaultConfig config) {
        if (instance == null) {
            instance = new VaultSecretManager(config);
        }
    }

    public static VaultSecretManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("VaultSecretManager not initialized. Call initialize() first.");
        }
        return instance;
    }

    public String getSecret(String key) {
        return getSecret(key, null);
    }

    public String getSecret(String key, String defaultValue) {
        if (!vaultEnabled) {
            String value = System.getenv(key);
            return value != null ? value : defaultValue;
        }

        String cached = secretCache.get(key);
        if (cached != null) {
            return cached;
        }

        if (shouldRefreshCache()) {
            refreshSecrets();
            cached = secretCache.get(key);
            if (cached != null) {
                return cached;
            }
        }

        String envValue = System.getenv(key);
        return envValue != null ? envValue : defaultValue;
    }

    public synchronized void refreshSecrets() {
        if (!vaultEnabled) {
            return;
        }

        // Ensure we have a valid token before making requests
        if (!ensureValidToken()) {
            logger.error("Cannot refresh secrets: failed to obtain valid token");
            return;
        }

        int retries = 0;
        while (retries < MAX_AUTH_RETRIES) {
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
                    return; // Success
                } else {
                    int status = response != null && response.getRestResponse() != null ? response.getRestResponse().getStatus() : -1;
                    if (status == 403) {
                        logger.warn("Vault permission denied (403). Attempting re-authentication...");
                        if (login()) {
                            retries++;
                            continue; // Retry with new token
                        }
                        logger.error("Re-authentication failed. Check your AppRole policy includes: path \"secret/data/{}\" {{ capabilities = [\"read\"] }}", appConfig.getSecretPath());
                        return;
                    } else if (status == 404) {
                        logger.warn("Vault path '{}' not found (404). Secrets may not be initialized yet.", path);
                    } else {
                        logger.warn("Failed to read from Vault (HTTP {})", status);
                    }
                    return;
                }
            } catch (VaultException e) {
                if (e.getHttpStatusCode() == 403) {
                    logger.warn("Auth error during secret read, attempting re-login...");
                    if (login()) {
                        retries++;
                        continue; // Retry with new token
                    }
                }
                logger.error("Vault error: {} (HTTP {})", e.getMessage(), e.getHttpStatusCode(), e);
                return;
            }
        }
        logger.error("Max auth retries ({}) exceeded while refreshing secrets", MAX_AUTH_RETRIES);
    }

    public boolean writeSecret(String key, String value) {
        if (!vaultEnabled) {
            logger.warn("Cannot write secret - Vault is not enabled");
            return false;
        }

        if (!ensureValidToken()) {
            logger.error("Cannot write secret: failed to obtain valid token");
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
            if (e.getHttpStatusCode() == 403 && login()) {
                // Retry once with fresh token
                try {
                    Map<String, Object> secrets = new HashMap<>(secretCache);
                    secrets.put(key, value);
                    vault.logical().write(appConfig.getKvV2LogicalPath(), secrets);
                    secretCache.put(key, value);
                    logger.info("Successfully wrote secret '{}' to Vault (after re-auth)", key);
                    return true;
                } catch (VaultException e2) {
                    logger.error("Failed to write secret to Vault after re-auth: {}", e2.getMessage(), e2);
                }
            } else {
                logger.error("Failed to write secret to Vault: {}", e.getMessage(), e);
            }
            return false;
        }
    }

    public boolean deleteSecret(String key) {
        if (!vaultEnabled) {
            logger.warn("Cannot delete secret - Vault is not enabled");
            return false;
        }

        if (!ensureValidToken()) {
            logger.error("Cannot delete secret: failed to obtain valid token");
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
            if (e.getHttpStatusCode() == 403 && login()) {
                // Retry once with fresh token
                try {
                    Map<String, Object> secrets = new HashMap<>(secretCache);
                    secrets.remove(key);
                    vault.logical().write(appConfig.getKvV2LogicalPath(), secrets);
                    secretCache.remove(key);
                    logger.info("Successfully deleted secret '{}' from Vault (after re-auth)", key);
                    return true;
                } catch (VaultException e2) {
                    logger.error("Failed to delete secret from Vault after re-auth: {}", e2.getMessage(), e2);
                }
            } else {
                logger.error("Failed to delete secret from Vault: {}", e.getMessage(), e);
            }
            return false;
        }
    }

    private boolean shouldRefreshCache() {
        return (System.currentTimeMillis() - lastRefreshTime) > CACHE_REFRESH_INTERVAL_MS;
    }

    public boolean isVaultEnabled() {
        return vaultEnabled;
    }

    public Map<String, String> getAllSecrets() {
        if (!vaultEnabled) {
            logger.warn("Vault is not enabled, returning empty secret map");
            return new HashMap<>();
        }
        return new HashMap<>(secretCache);
    }

    public void clearCache() {
        secretCache.clear();
        lastRefreshTime = 0L;
        logger.info("Secret cache cleared");
    }

    public boolean testConnection() {
        if (!vaultEnabled) {
            return false;
        }

        if (!ensureValidToken()) {
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

    /**
     * Get remaining token validity time in seconds.
     * Returns 0 if token is expired or Vault is disabled.
     */
    public long getTokenTTL() {
        if (!vaultEnabled || clientToken == null) {
            return 0;
        }
        long remaining = tokenExpiryTime - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000 : 0;
    }
}
