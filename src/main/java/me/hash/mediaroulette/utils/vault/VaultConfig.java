package me.hash.mediaroulette.utils.vault;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Configuration class for HashiCorp Vault integration.
 * Manages Vault connection settings and authentication credentials.
 *
 * Loads configuration from:
 *  1) External file: ./vault-config.properties
 *  2) Classpath:     /vault-config.properties
 *  3) Default values (and writes them to ./vault-config.properties)
 */
public class VaultConfig {
    private static final Logger logger = LoggerFactory.getLogger(VaultConfig.class);
    private static final String CONFIG_FILE = "vault-config.properties";

    private final String vaultAddress;
    private final String vaultToken;
    private final String vaultNamespace;
    private final String secretPath;    // e.g. "mediaroulette"
    private final String secretEngine;  // e.g. "secret"
    private final boolean enabled;
    private final boolean sslVerify;
    private final int timeoutSeconds;

    private VaultConfig(Builder builder) {
        this.vaultAddress = builder.vaultAddress;
        this.vaultToken = builder.vaultToken;
        this.vaultNamespace = builder.vaultNamespace;
        this.secretPath = builder.secretPath;
        this.secretEngine = builder.secretEngine;
        this.enabled = builder.enabled;
        this.sslVerify = builder.sslVerify;
        this.timeoutSeconds = builder.timeoutSeconds;
    }

    /**
     * Load Vault configuration from file / classpath / defaults.
     */
    public static VaultConfig load() {
        Properties props = new Properties();

        // 1) External file
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                logger.info("Loaded Vault configuration from external file: {}", CONFIG_FILE);
                return buildFromProperties(props);
            } catch (IOException e) {
                logger.error("Failed to load external Vault configuration: {}", e.getMessage());
            }
        } else {
            logger.info("External Vault configuration file not found: {}", CONFIG_FILE);
        }

        // 2) Classpath resource
        try (var is = VaultConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is != null) {
                props.load(is);
                logger.info("Loaded Vault configuration from classpath resource: {}", CONFIG_FILE);
                return buildFromProperties(props);
            } else {
                logger.warn("Classpath Vault configuration resource '{}' not found", CONFIG_FILE);
            }
        } catch (IOException e) {
            logger.error("Failed to load Vault configuration from classpath: {}", e.getMessage());
        }

        // 3) Default configuration
        logger.info("Using default Vault configuration");
        VaultConfig defaultConfig = createDefaultConfig();
        defaultConfig.save();
        return defaultConfig;
    }

    private static VaultConfig buildFromProperties(Properties props) {
        return new Builder()
                .enabled(Boolean.parseBoolean(props.getProperty("vault.enabled", "false")))
                .vaultAddress(props.getProperty("vault.address", "http://localhost:8200"))
                .vaultToken(props.getProperty("vault.token", ""))
                .vaultNamespace(props.getProperty("vault.namespace", ""))
                .secretPath(props.getProperty("vault.secret.path", "mediaroulette"))
                .secretEngine(props.getProperty("vault.secret.engine", "secret"))
                .sslVerify(Boolean.parseBoolean(props.getProperty("vault.ssl.verify", "true")))
                .timeoutSeconds(Integer.parseInt(props.getProperty("vault.timeout", "5")))
                .build();
    }

    private static VaultConfig createDefaultConfig() {
        return new Builder()
                .enabled(false)
                .vaultAddress("http://localhost:8200")
                .vaultToken("")
                .vaultNamespace("")
                .secretPath("mediaroulette")
                .secretEngine("secret")
                .sslVerify(true)
                .timeoutSeconds(5)
                .build();
    }

    /**
     * Save current configuration to external properties file.
     */
    public void save() {
        Properties props = new Properties();
        props.setProperty("vault.enabled", String.valueOf(enabled));
        props.setProperty("vault.address", vaultAddress);
        props.setProperty("vault.token", vaultToken);
        props.setProperty("vault.namespace", vaultNamespace);
        props.setProperty("vault.secret.path", secretPath);
        props.setProperty("vault.secret.engine", secretEngine);
        props.setProperty("vault.ssl.verify", String.valueOf(sslVerify));
        props.setProperty("vault.timeout", String.valueOf(timeoutSeconds));

        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "HashiCorp Vault Configuration for MediaRoulette");
            logger.info("Saved Vault configuration to external file: {}", CONFIG_FILE);
        } catch (IOException e) {
            logger.error("Failed to save Vault configuration: {}", e.getMessage());
        }
    }

    // Basic getters

    public String getVaultAddress() {
        return vaultAddress;
    }

    public String getVaultToken() {
        return vaultToken;
    }

    public String getVaultNamespace() {
        return vaultNamespace;
    }

    public String getSecretPath() {
        return secretPath;
    }

    public String getSecretEngine() {
        return secretEngine;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isSslVerify() {
        return sslVerify;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * Logical path for KV v2 when using BetterCloud with engineVersion(2).
     *
     * IMPORTANT: When using engineVersion(2), the BetterCloud SDK automatically
     * adds /data/ for reads/writes and /metadata/ for metadata operations.
     * We should NOT include /data/ in the path ourselves.
     *
     * Example:
     *  secretEngine = "secret"
     *  secretPath   = "mediaroulette"
     *  => "secret/mediaroulette"
     *
     * The SDK converts this to "secret/data/mediaroulette" internally.
     *
     * Your CLI usage:
     *  vault kv put secret/mediaroulette DISCORD_TOKEN=...
     * matches this path.
     */
    public String getKvV2LogicalPath() {
        return secretEngine + "/" + secretPath;
    }

    // Builder

    public static class Builder {
        private String vaultAddress = "http://localhost:8200";
        private String vaultToken = "";
        private String vaultNamespace = "";
        private String secretPath = "mediaroulette";
        private String secretEngine = "secret";
        private boolean enabled = false;
        private boolean sslVerify = true;
        private int timeoutSeconds = 5;

        public Builder vaultAddress(String vaultAddress) {
            this.vaultAddress = vaultAddress;
            return this;
        }

        public Builder vaultToken(String vaultToken) {
            this.vaultToken = vaultToken;
            return this;
        }

        public Builder vaultNamespace(String vaultNamespace) {
            this.vaultNamespace = vaultNamespace;
            return this;
        }

        public Builder secretPath(String secretPath) {
            this.secretPath = secretPath;
            return this;
        }

        public Builder secretEngine(String secretEngine) {
            this.secretEngine = secretEngine;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder sslVerify(boolean sslVerify) {
            this.sslVerify = sslVerify;
            return this;
        }

        public Builder timeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public VaultConfig build() {
            return new VaultConfig(this);
        }
    }
}
