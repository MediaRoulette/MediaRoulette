package me.hash.mediaroulette.utils.vault;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Configuration for HashiCorp Vault integration.
 * Auto-creates vault-config.properties template on first run (disabled by default).
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
     * Load configuration. Creates template if missing.
     */
    public static VaultConfig load() {
        File configFile = new File(CONFIG_FILE);
        
        if (!configFile.exists()) {
            logger.info("Creating config template: {}", CONFIG_FILE);
            VaultConfig template = createDefaultConfig();
            template.save();
            return template;
        }

        try (FileInputStream fis = new FileInputStream(configFile)) {
            Properties props = new Properties();
            props.load(fis);
            logger.info("Loaded config from: {}", CONFIG_FILE);
            return buildFromProperties(props);
        } catch (IOException e) {
            logger.error("Failed to load config: {}", e.getMessage());
            return createDefaultConfig();
        }
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
     * Save config file with comments.
     */
    public void save() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            StringBuilder sb = new StringBuilder();
            sb.append("# HashiCorp Vault Configuration\n");
            sb.append("# Vault is DISABLED by default. Set vault.enabled=true to enable.\n");
            sb.append("# If disabled, secrets are loaded from .env and environment variables.\n\n");
            
            sb.append("vault.enabled=").append(enabled).append("\n");
            sb.append("vault.address=").append(vaultAddress).append("\n");
            sb.append("vault.token=").append(vaultToken).append("\n");
            sb.append("vault.namespace=").append(vaultNamespace).append("\n");
            sb.append("vault.secret.path=").append(secretPath).append("\n");
            sb.append("vault.secret.engine=").append(secretEngine).append("\n");
            sb.append("vault.ssl.verify=").append(sslVerify).append("\n");
            sb.append("vault.timeout=").append(timeoutSeconds).append("\n");
            
            fos.write(sb.toString().getBytes());
            logger.info("Created config template: {} (disabled by default)", CONFIG_FILE);
        } catch (IOException e) {
            logger.error("Failed to save config: {}", e.getMessage());
        }
    }

    public String getVaultAddress() { return vaultAddress; }
    public String getVaultToken() { return vaultToken; }
    public String getVaultNamespace() { return vaultNamespace; }
    public String getSecretPath() { return secretPath; }
    public String getSecretEngine() { return secretEngine; }
    public boolean isEnabled() { return enabled; }
    public boolean isSslVerify() { return sslVerify; }
    public int getTimeoutSeconds() { return timeoutSeconds; }

    /**
     * Returns the KV v2 path (SDK automatically adds /data/ internally).
     * Example: "secret/mediaroulette" -> SDK converts to "secret/data/mediaroulette"
     */
    public String getKvV2LogicalPath() {
        return secretEngine + "/" + secretPath;
    }

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
