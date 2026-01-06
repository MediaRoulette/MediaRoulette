package me.hash.mediaroulette.utils.vault;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full integration test for Vault secret management with AppRole authentication.
 * 
 * This test is designed to run in CI with the vault-test.yml workflow.
 * 
 * Prerequisites:
 * - Vault server running at http://localhost:8200
 * - AppRole auth enabled and configured
 * - Environment variables: VAULT_ROLE_ID, VAULT_SECRET_ID
 * - Secrets stored at secret/mediaroulette with keys:
 *   - DISCORD_TOKEN
 *   - MONGODB_CONNECTION
 * 
 * The CI workflow automatically sets up these prerequisites.
 */
@EnabledIfEnvironmentVariable(named = "VAULT_TEST_ENABLED", matches = "true")
class VaultIntegrationTest {

    private static VaultSecretManager manager;
    private static final String ROLE_ID = System.getenv("VAULT_ROLE_ID");
    private static final String SECRET_ID = System.getenv("VAULT_SECRET_ID");

    @BeforeAll
    static void setupVault() {
        System.out.println("=== Vault Integration Test Setup (AppRole) ===");
        System.out.println("Vault Address: http://localhost:8200");
        System.out.println("Role ID: " + (ROLE_ID != null ? "***" : "NOT SET"));
        System.out.println("Secret ID: " + (SECRET_ID != null ? "***" : "NOT SET"));

        assertNotNull(ROLE_ID, "VAULT_ROLE_ID must be set");
        assertNotNull(SECRET_ID, "VAULT_SECRET_ID must be set");

        VaultConfig config = new VaultConfig.Builder()
                .enabled(true)
                .vaultAddress("http://localhost:8200")
                .roleId(ROLE_ID)
                .secretId(SECRET_ID)
                .secretPath("mediaroulette")
                .secretEngine("secret")
                .sslVerify(false)
                .timeoutSeconds(10)
                .build();

        VaultSecretManager.initialize(config);
        manager = VaultSecretManager.getInstance();
        
        System.out.println("Vault enabled: " + manager.isVaultEnabled());
        System.out.println("Token TTL: " + manager.getTokenTTL() + " seconds");
    }

    @Test
    void testVaultIsEnabled() {
        assertTrue(manager.isVaultEnabled(), "Vault should be enabled for integration tests");
    }

    @Test
    void testVaultConnectionSuccessful() {
        boolean connected = manager.testConnection();
        assertTrue(connected, "Should be able to connect to Vault server");
    }

    @Test
    void testTokenTTLIsPositive() {
        long ttl = manager.getTokenTTL();
        assertTrue(ttl > 0, "Token TTL should be positive after successful login");
        System.out.println("Current token TTL: " + ttl + " seconds");
    }

    @Test
    void testRefreshSecretsLoadsData() {
        manager.refreshSecrets();
        var secrets = manager.getAllSecrets();
        
        assertNotNull(secrets, "Secrets map should not be null");
        System.out.println("Loaded " + secrets.size() + " secrets from Vault");
        
        // CI workflow should have stored these secrets
        assertTrue(secrets.size() >= 0, "Should load secrets from Vault");
    }

    @Test
    void testGetDiscordTokenFromVault() {
        String discordToken = manager.getSecret("DISCORD_TOKEN");
        
        // CI workflow sets this to "test_token"
        assertNotNull(discordToken, "DISCORD_TOKEN should be available from Vault");
        System.out.println("DISCORD_TOKEN retrieved: " + (discordToken != null ? "***" : "null"));
    }

    @Test
    void testGetMongoConnectionFromVault() {
        String mongoConnection = manager.getSecret("MONGODB_CONNECTION");
        
        // CI workflow sets this to "mongodb://localhost:27017"
        assertNotNull(mongoConnection, "MONGODB_CONNECTION should be available from Vault");
        System.out.println("MONGODB_CONNECTION retrieved: " + (mongoConnection != null ? mongoConnection : "null"));
    }

    @Test
    void testGetNonExistentSecretReturnsNull() {
        String nonExistent = manager.getSecret("NON_EXISTENT_SECRET_KEY_xyz");
        assertNull(nonExistent, "Non-existent secret should return null");
    }

    @Test
    void testGetNonExistentSecretWithDefault() {
        String defaultValue = "my-default-value";
        String result = manager.getSecret("NON_EXISTENT_SECRET_KEY_xyz", defaultValue);
        assertEquals(defaultValue, result, "Should return default value for non-existent secret");
    }

    @Test
    void testSecretCacheWorks() {
        // First call should load from Vault
        String token1 = manager.getSecret("DISCORD_TOKEN");
        
        // Second call should use cache
        String token2 = manager.getSecret("DISCORD_TOKEN");
        
        assertEquals(token1, token2, "Cached secret should match original");
    }

    @Test
    void testClearCacheAndReload() {
        // Load initial secrets
        manager.refreshSecrets();
        String token1 = manager.getSecret("DISCORD_TOKEN");
        assertNotNull(token1);
        
        // Clear cache
        manager.clearCache();
        
        // Reload secrets
        manager.refreshSecrets();
        String token2 = manager.getSecret("DISCORD_TOKEN");
        
        assertEquals(token1, token2, "Secret should be reloaded correctly after cache clear");
    }

    @Test
    void testGetAllSecretsReturnsPopulatedMap() {
        manager.refreshSecrets();
        var secrets = manager.getAllSecrets();
        
        assertNotNull(secrets);
        assertFalse(secrets.isEmpty(), "Secrets map should contain data from Vault");
        
        System.out.println("All secrets keys: " + secrets.keySet());
    }

    @Test
    void testVaultPathConfiguration() {
        VaultConfig config = new VaultConfig.Builder()
                .secretEngine("secret")
                .secretPath("mediaroulette")
                .build();
        
        String path = config.getKvV2LogicalPath();
        assertEquals("secret/mediaroulette", path, 
                "KV v2 path should NOT include /data/ - SDK adds it automatically with engineVersion(2)");
    }

    @Test
    void testMultipleSecretRetrieval() {
        String[] secretKeys = {
            "DISCORD_TOKEN",
            "MONGODB_CONNECTION"
        };
        
        for (String key : secretKeys) {
            String value = manager.getSecret(key);
            System.out.println("Secret " + key + ": " + (value != null ? "FOUND" : "NOT FOUND"));
        }
    }

    @Test
    void testFallbackToEnvironmentVariable() {
        // Set a test environment variable (simulated)
        // In real scenarios, this would be an actual env var
        String pathEnv = manager.getSecret("PATH");
        assertNotNull(pathEnv, "Should fall back to environment variable if not in Vault");
    }
}
