package me.hash.mediaroulette.utils.vault;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for VaultSecretManager with AppRole authentication.
 * 
 * These tests require a running Vault instance with AppRole enabled.
 * Set VAULT_TEST_ENABLED=true, VAULT_ROLE_ID, and VAULT_SECRET_ID to run these tests.
 * 
 * To run locally with Docker:
 *   docker run --rm --cap-add=IPC_LOCK -p 8200:8200 \
 *     -e VAULT_DEV_ROOT_TOKEN_ID=testtoken \
 *     -e VAULT_DEV_LISTEN_ADDRESS=0.0.0.0:8200 \
 *     vault:latest
 * 
 * Then configure AppRole:
 *   export VAULT_ADDR=http://localhost:8200
 *   export VAULT_TOKEN=testtoken
 *   vault auth enable approle
 *   vault write auth/approle/role/mediaroulette policies="default" token_ttl="1h"
 *   vault read -format=json auth/approle/role/mediaroulette/role-id
 *   vault write -format=json -f auth/approle/role/mediaroulette/secret-id
 * 
 * Set environment variables:
 *   export VAULT_TEST_ENABLED=true
 *   export VAULT_ROLE_ID=<role-id-from-above>
 *   export VAULT_SECRET_ID=<secret-id-from-above>
 */
class VaultSecretManagerTest {

    private static final String TEST_ENABLED_ENV = "VAULT_TEST_ENABLED";
    private static final String ROLE_ID_ENV = "VAULT_ROLE_ID";
    private static final String SECRET_ID_ENV = "VAULT_SECRET_ID";

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
    void testEmptyAppRoleCredentialsDisablesVault() {
        // Even with enabled=true, empty credentials should disable Vault
        VaultConfig config = new VaultConfig.Builder()
                .enabled(true)
                .roleId("")
                .secretId("")
                .build();

        VaultSecretManager.initialize(config);
        VaultSecretManager manager = VaultSecretManager.getInstance();

        assertFalse(manager.isVaultEnabled());
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
    void testGetTokenTTLWhenDisabled() {
        VaultConfig config = new VaultConfig.Builder()
                .enabled(false)
                .build();

        VaultSecretManager.initialize(config);
        VaultSecretManager manager = VaultSecretManager.getInstance();

        assertEquals(0, manager.getTokenTTL());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = TEST_ENABLED_ENV, matches = "true")
    void testVaultConnectionWithAppRole() {
        String roleId = System.getenv(ROLE_ID_ENV);
        String secretId = System.getenv(SECRET_ID_ENV);
        assertNotNull(roleId, "VAULT_ROLE_ID must be set for integration tests");
        assertNotNull(secretId, "VAULT_SECRET_ID must be set for integration tests");

        VaultConfig config = new VaultConfig.Builder()
                .enabled(true)
                .vaultAddress("http://localhost:8200")
                .roleId(roleId)
                .secretId(secretId)
                .secretPath("mediaroulette")
                .secretEngine("secret")
                .sslVerify(false)
                .timeoutSeconds(5)
                .build();

        VaultSecretManager.initialize(config);
        VaultSecretManager manager = VaultSecretManager.getInstance();

        assertTrue(manager.isVaultEnabled());
        assertTrue(manager.testConnection());
        assertTrue(manager.getTokenTTL() > 0);
    }

    @Test
    @EnabledIfEnvironmentVariable(named = TEST_ENABLED_ENV, matches = "true")
    void testReadSecretsFromVaultWithAppRole() {
        String roleId = System.getenv(ROLE_ID_ENV);
        String secretId = System.getenv(SECRET_ID_ENV);
        assertNotNull(roleId, "VAULT_ROLE_ID must be set for integration tests");
        assertNotNull(secretId, "VAULT_SECRET_ID must be set for integration tests");

        VaultConfig config = new VaultConfig.Builder()
                .enabled(true)
                .vaultAddress("http://localhost:8200")
                .roleId(roleId)
                .secretId(secretId)
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
                .roleId("test-role")
                .secretId("test-secret")
                .secretPath("mediaroulette")
                .sslVerify(false)
                .timeoutSeconds(1)
                .build();

        // With AppRole, if login fails due to invalid address, Vault will be disabled
        // This allows graceful fallback to environment variables
        assertDoesNotThrow(() -> {
            VaultSecretManager.initialize(config);
            VaultSecretManager manager = VaultSecretManager.getInstance();
            
            // Vault should be disabled since login failed
            assertFalse(manager.isVaultEnabled(), "Vault should be disabled when login fails");
            
            // Should still fall back to environment variables
            String pathEnv = manager.getSecret("PATH");
            assertNotNull(pathEnv, "Should fall back to environment variable");
        });
    }
}
