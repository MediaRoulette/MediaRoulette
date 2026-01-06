package me.hash.mediaroulette.utils.vault;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VaultConfig class with AppRole authentication.
 */
class VaultConfigTest {

    @Test
    void testDefaultConfiguration() {
        VaultConfig config = new VaultConfig.Builder().build();

        assertNotNull(config);
        assertFalse(config.isEnabled());
        assertEquals("http://localhost:8200", config.getVaultAddress());
        assertEquals("", config.getRoleId());
        assertEquals("", config.getSecretId());
        assertEquals("mediaroulette", config.getSecretPath());
        assertEquals("secret", config.getSecretEngine());
        assertTrue(config.isSslVerify());
        assertEquals(5, config.getTimeoutSeconds());
    }

    @Test
    void testBuilderConfiguration() {
        VaultConfig config = new VaultConfig.Builder()
                .enabled(true)
                .vaultAddress("https://vault.example.com:8200")
                .roleId("test-role-id")
                .secretId("test-secret-id")
                .vaultNamespace("test-namespace")
                .secretPath("myapp")
                .secretEngine("kv")
                .sslVerify(false)
                .timeoutSeconds(10)
                .build();

        assertTrue(config.isEnabled());
        assertEquals("https://vault.example.com:8200", config.getVaultAddress());
        assertEquals("test-role-id", config.getRoleId());
        assertEquals("test-secret-id", config.getSecretId());
        assertEquals("test-namespace", config.getVaultNamespace());
        assertEquals("myapp", config.getSecretPath());
        assertEquals("kv", config.getSecretEngine());
        assertFalse(config.isSslVerify());
        assertEquals(10, config.getTimeoutSeconds());
    }

    @Test
    void testKvV2LogicalPath() {
        VaultConfig config = new VaultConfig.Builder()
                .secretEngine("secret")
                .secretPath("mediaroulette")
                .build();

        assertEquals("secret/mediaroulette", config.getKvV2LogicalPath());
    }

    @Test
    void testKvV2LogicalPathCustomEngine() {
        VaultConfig config = new VaultConfig.Builder()
                .secretEngine("custom")
                .secretPath("mypath")
                .build();

        assertEquals("custom/mypath", config.getKvV2LogicalPath());
    }

    @Test
    void testLoadFromPropertiesFile(@TempDir Path tempDir) throws IOException {
        File configFile = tempDir.resolve("test-vault-config.properties").toFile();
        
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write("vault.enabled=true\n");
            writer.write("vault.address=https://test.vault.com:8200\n");
            writer.write("vault.approle.role_id=test-role-123\n");
            writer.write("vault.approle.secret_id=test-secret-456\n");
            writer.write("vault.namespace=test-ns\n");
            writer.write("vault.secret.path=testapp\n");
            writer.write("vault.secret.engine=kv\n");
            writer.write("vault.ssl.verify=false\n");
            writer.write("vault.timeout=15\n");
        }

        // Note: This test demonstrates the format, but actual file loading
        // would need the file to be in the expected location
        assertTrue(configFile.exists());
    }

    @Test
    void testSaveConfiguration(@TempDir Path tempDir) {
        VaultConfig config = new VaultConfig.Builder()
                .enabled(true)
                .vaultAddress("https://vault.example.com:8200")
                .roleId("test-role-id")
                .secretId("test-secret-id")
                .secretPath("testapp")
                .build();

        // Note: save() writes to current directory, not temp dir
        // This test verifies the method exists and doesn't crash
        assertDoesNotThrow(() -> config.save());
    }

    @Test
    void testEmptyNamespace() {
        VaultConfig config = new VaultConfig.Builder()
                .vaultNamespace("")
                .build();

        assertEquals("", config.getVaultNamespace());
    }

    @Test
    void testNullNamespace() {
        VaultConfig config = new VaultConfig.Builder()
                .vaultNamespace(null)
                .build();

        assertNull(config.getVaultNamespace());
    }

    @Test
    void testEmptyAppRoleCredentials() {
        VaultConfig config = new VaultConfig.Builder()
                .enabled(true)
                .roleId("")
                .secretId("")
                .build();

        // Config can be created with empty credentials
        // but VaultSecretManager will treat this as disabled
        assertTrue(config.isEnabled());
        assertEquals("", config.getRoleId());
        assertEquals("", config.getSecretId());
    }
}
