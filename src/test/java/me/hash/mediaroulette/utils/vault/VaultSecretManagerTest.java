package me.hash.mediaroulette.utils.vault;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for VaultSecretManager.
 * 
 * These tests require a running Vault instance.
 * Set VAULT_TEST_ENABLED=true and VAULT_TEST_TOKEN to run these tests.
 * 
 * To run locally with Docker:
 *   docker run --rm --cap-add=IPC_LOCK -p 8200:8200 \
 *     -e VAULT_DEV_ROOT_TOKEN_ID=testtoken \
 *     -e VAULT_DEV_LISTEN_ADDRESS=0.0.0.0:8200 \
 *     vault:latest
 * 
 * Then set environment variables:
 *   export VAULT_TEST_ENABLED=true
 *   export VAULT_TEST_TOKEN=testtoken
 */
class VaultSecretManagerTest {

    private static final String TEST_TOKEN_ENV = "VAULT_TEST_TOKEN";
    private static final String TEST_ENABLED_ENV = "VAULT_TEST_ENABLED";

    @BeforeEach
    void setUp() {
        // Reset singleton for each test
        try {
            java.lang.reflect.Field instance = VaultSecretManager.class.getDeclaredField("instance");
            instance.setAccessible(true);
            instance.set(null, null);
        } catch (Exception e) {
            // Ignore if field access fails
        }
    }

    @AfterEach
    void tearDown() {
        // Clean up singleton
        try {
            java.lang.reflect.Field instance = VaultSecretManager.class.getDeclaredField("instance");
            instance.setAccessible(true);
            instance.set(null, null);
        } catch (Exception e) {
            // Ignore if field access fails
        }
    }

    @Test
    void testDisabledVaultUsesEnvironmentVariables() {
        // Create disabled config
        VaultConfig config = new VaultConfig.Builder()
                .enabled(false)
                .build();

        VaultSecretManager.initialize(config);
        VaultSecretManager manager = VaultSecretManager.getInstance();

        assertFalse(manager.isVaultEnabled());
        
        // Should fall back to environment variables
        String pathEnv = manager.getSecret("PATH");
        assertNotNull(pathEnv); // PATH should exist in environment
    }

    @Test
    void testGetSecretWithDefaultValue() {
        VaultConfig config = new VaultConfig.Builder()
                .enabled(false)
                .build();

        VaultSecretManager.initialize(config);
        VaultSecretManager manager = VaultSecretManager.getInstance();

        String secret = manager.getSecret("NONEXISTENT_SECRET_KEY_12345", "default-value");
        assertEquals("default-value", secret);
    }

    @Test
    void testGetSecretReturnsNullWhenNotFound() {
        VaultConfig config = new VaultConfig.Builder()
                .enabled(false)
                .build();

        VaultSecretManager.initialize(config);
        VaultSecretManager manager = VaultSecretManager.getInstance();

        String secret = manager.getSecret("NONEXISTENT_SECRET_KEY_12345");
        assertNull(secret);
    }

    @Test
    void testClearCache() {
        VaultConfig config = new VaultConfig.Builder()
                .enabled(false)
                .build();

        VaultSecretManager.initialize(config);
        VaultSecretManager manager = VaultSecretManager.getInstance();

        assertDoesNotThrow(() -> manager.clearCache());
    }

    @Test
    void testGetAllSecretsWhenDisabled() {
        VaultConfig config = new VaultConfig.Builder()
                .enabled(false)
                .build();

        VaultSecretManager.initialize(config);
        VaultSecretManager manager = VaultSecretManager.getInstance();

        Map<String, String> secrets = manager.getAllSecrets();
        assertNotNull(secrets);
        assertTrue(secrets.isEmpty());
    }

    @Test
    void testWriteSecretWhenDisabled() {
        VaultConfig config = new VaultConfig.Builder()
                .enabled(false)
                .build();

        VaultSecretManager.initialize(config);
        VaultSecretManager manager = VaultSecretManager.getInstance();

        boolean result = manager.writeSecret("TEST_KEY", "test-value");
        assertFalse(result);
    }

    @Test
    void testDeleteSecretWhenDisabled() {
        VaultConfig config = new VaultConfig.Builder()
                .enabled(false)
                .build();

        VaultSecretManager.initialize(config);
        VaultSecretManager manager = VaultSecretManager.getInstance();

        boolean result = manager.deleteSecret("TEST_KEY");
        assertFalse(result);
    }

    @Test
    void testTestConnectionWhenDisabled() {
        VaultConfig config = new VaultConfig.Builder()
                .enabled(false)
                .build();

        VaultSecretManager.initialize(config);
        VaultSecretManager manager = VaultSecretManager.getInstance();

        boolean result = manager.testConnection();
        assertFalse(result);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = TEST_ENABLED_ENV, matches = "true")
    void testVaultConnectionWithRealServer() {
        String token = System.getenv(TEST_TOKEN_ENV);
        assertNotNull(token, "VAULT_TEST_TOKEN must be set for integration tests");

        VaultConfig config = new VaultConfig.Builder()
                .enabled(true)
                .vaultAddress("http://localhost:8200")
                .vaultToken(token)
                .secretPath("mediaroulette")
                .secretEngine("secret")
                .sslVerify(false)
                .timeoutSeconds(5)
                .build();

        VaultSecretManager.initialize(config);
        VaultSecretManager manager = VaultSecretManager.getInstance();

        assertTrue(manager.isVaultEnabled());
        assertTrue(manager.testConnection());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = TEST_ENABLED_ENV, matches = "true")
    void testReadSecretsFromRealVault() {
        String token = System.getenv(TEST_TOKEN_ENV);
        assertNotNull(token, "VAULT_TEST_TOKEN must be set for integration tests");

        VaultConfig config = new VaultConfig.Builder()
                .enabled(true)
                .vaultAddress("http://localhost:8200")
                .vaultToken(token)
                .secretPath("mediaroulette")
                .secretEngine("secret")
                .sslVerify(false)
                .timeoutSeconds(5)
                .build();

        VaultSecretManager.initialize(config);
        VaultSecretManager manager = VaultSecretManager.getInstance();

        manager.refreshSecrets();
        Map<String, String> secrets = manager.getAllSecrets();
        
        assertNotNull(secrets);
        // Secrets should be populated if they exist in Vault
        // The CI workflow sets DISCORD_TOKEN and MONGODB_CONNECTION
    }

    @Test
    @EnabledIfEnvironmentVariable(named = TEST_ENABLED_ENV, matches = "true")
    void testGetSpecificSecretFromVault() {
        String token = System.getenv(TEST_TOKEN_ENV);
        assertNotNull(token, "VAULT_TEST_TOKEN must be set for integration tests");

        VaultConfig config = new VaultConfig.Builder()
                .enabled(true)
                .vaultAddress("http://localhost:8200")
                .vaultToken(token)
                .secretPath("mediaroulette")
                .secretEngine("secret")
                .sslVerify(false)
                .timeoutSeconds(5)
                .build();

        VaultSecretManager.initialize(config);
        VaultSecretManager manager = VaultSecretManager.getInstance();

        String discordToken = manager.getSecret("DISCORD_TOKEN");
        // Should either return the value from Vault or null
        // CI workflow sets this to "test_token"
    }

    @Test
    void testSingletonPattern() {
        VaultConfig config = new VaultConfig.Builder()
                .enabled(false)
                .build();

        VaultSecretManager.initialize(config);
        VaultSecretManager instance1 = VaultSecretManager.getInstance();
        VaultSecretManager instance2 = VaultSecretManager.getInstance();

        assertSame(instance1, instance2);
    }

    @Test
    void testGetInstanceBeforeInitializeThrowsException() {
        // Reset singleton
        try {
            java.lang.reflect.Field instance = VaultSecretManager.class.getDeclaredField("instance");
            instance.setAccessible(true);
            instance.set(null, null);
        } catch (Exception e) {
            fail("Could not reset singleton");
        }

        assertThrows(IllegalStateException.class, VaultSecretManager::getInstance);
    }

    @Test
    void testInvalidVaultAddressHandledGracefully() {
        VaultConfig config = new VaultConfig.Builder()
                .enabled(true)
                .vaultAddress("http://invalid-vault-address-that-does-not-exist:8200")
                .vaultToken("test-token")
                .secretPath("mediaroulette")
                .sslVerify(false)
                .timeoutSeconds(1)
                .build();

        // Initialization should succeed (Vault client is created)
        // but refreshSecrets will log an error and continue
        // This allows fallback to environment variables
        assertDoesNotThrow(() -> {
            VaultSecretManager.initialize(config);
            VaultSecretManager manager = VaultSecretManager.getInstance();
            
            // Vault is considered "enabled" even if connection fails
            assertTrue(manager.isVaultEnabled());
            
            // But secrets will be empty since refresh failed
            Map<String, String> secrets = manager.getAllSecrets();
            assertTrue(secrets.isEmpty(), "Secrets should be empty when connection fails");
            
            // Should still fall back to environment variables
            String pathEnv = manager.getSecret("PATH");
            assertNotNull(pathEnv, "Should fall back to environment variable");
        });
    }
}
