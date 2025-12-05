package me.hash.mediaroulette.utils.terminal.commands;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.utils.terminal.Command;
import me.hash.mediaroulette.utils.terminal.CommandResult;
import me.hash.mediaroulette.utils.vault.VaultSecretManager;

import java.util.List;
import java.util.Map;

/**
 * Terminal command for managing Vault secrets.
 */
public class VaultCommand extends Command {

    public VaultCommand() {
        super(
                "vault",
                "Manage HashiCorp Vault secrets",
                "vault <status|list|get|write|delete|refresh|test> [key] [value]",
                List.of("vlt")
        );
    }

    @Override
    public CommandResult execute(String[] args) {
        if (args.length < 1) {
            return CommandResult.error(getHelp());
        }

        VaultSecretManager vault = Main.getVaultSecretManager();
        if (vault == null) {
            return CommandResult.error("Vault secret manager not initialized");
        }

        String subcommand = args[0].toLowerCase();

        return switch (subcommand) {
            case "status" -> handleStatus(vault);
            case "list" -> handleList(vault);
            case "get" -> handleGet(vault, args);
            case "write", "set" -> handleWrite(vault, args);
            case "delete", "remove" -> handleDelete(vault, args);
            case "refresh" -> handleRefresh(vault);
            case "test" -> handleTest(vault);
            default -> CommandResult.error("Unknown subcommand: " + subcommand + "\n" + getHelp());
        };
    }

    private CommandResult handleStatus(VaultSecretManager vault) {
        StringBuilder sb = new StringBuilder();
        sb.append("Vault Status\n");
        sb.append("=".repeat(50)).append("\n");
        sb.append("Enabled:    ").append(vault.isVaultEnabled() ? "Yes" : "No").append("\n");

        if (vault.isVaultEnabled()) {
            boolean connected = vault.testConnection();
            sb.append("Connected:  ").append(connected ? "Yes" : "No").append("\n");

            Map<String, String> secrets = vault.getAllSecrets();
            sb.append("Secrets:    ").append(secrets.size()).append(" cached\n");
        } else {
            sb.append("Using fallback to .env and environment variables\n");
        }

        sb.append("=".repeat(50));
        return CommandResult.success(sb.toString());
    }

    private CommandResult handleList(VaultSecretManager vault) {
        if (!vault.isVaultEnabled()) {
            return CommandResult.error("Vault is not enabled. Enable it in vault-config.properties");
        }

        Map<String, String> secrets = vault.getAllSecrets();
        if (secrets.isEmpty()) {
            return CommandResult.success("No secrets cached. Run 'vault refresh' to load from Vault.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Cached Secrets (").append(secrets.size()).append(")\n");
        sb.append("=".repeat(50)).append("\n");

        secrets.keySet().stream()
                .sorted()
                .forEach(key -> {
                    String value = secrets.get(key);
                    String masked = maskSecret(value);
                    sb.append(String.format("%-30s : %s\n", key, masked));
                });

        sb.append("=".repeat(50));
        return CommandResult.success(sb.toString());
    }

    private CommandResult handleGet(VaultSecretManager vault, String[] args) {
        if (args.length < 2) {
            return CommandResult.error("Usage: vault get <key>");
        }

        String key = args[1];
        String value = vault.getSecret(key);

        if (value == null) {
            return CommandResult.error("Secret '" + key + "' not found");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Secret: ").append(key).append("\n");
        sb.append("Value:  ").append(maskSecret(value)).append("\n");
        sb.append("\nUse Main.getEnv(\"").append(key).append("\") to access in code");

        return CommandResult.success(sb.toString());
    }

    private CommandResult handleWrite(VaultSecretManager vault, String[] args) {
        if (!vault.isVaultEnabled()) {
            return CommandResult.error("Vault is not enabled. Enable it in vault-config.properties");
        }

        if (args.length < 3) {
            return CommandResult.error("Usage: vault write <key> <value>");
        }

        String key = args[1];
        // Join remaining args as value (in case value has spaces)
        String value = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));

        boolean success = vault.writeSecret(key, value);
        if (success) {
            return CommandResult.success("Successfully wrote secret '" + key + "' to Vault");
        } else {
            return CommandResult.error("Failed to write secret to Vault. Check logs for details.");
        }
    }

    private CommandResult handleDelete(VaultSecretManager vault, String[] args) {
        if (!vault.isVaultEnabled()) {
            return CommandResult.error("Vault is not enabled. Enable it in vault-config.properties");
        }

        if (args.length < 2) {
            return CommandResult.error("Usage: vault delete <key>");
        }

        String key = args[1];
        boolean success = vault.deleteSecret(key);

        if (success) {
            return CommandResult.success("Successfully deleted secret '" + key + "' from Vault");
        } else {
            return CommandResult.error("Failed to delete secret from Vault. Check logs for details.");
        }
    }

    private CommandResult handleRefresh(VaultSecretManager vault) {
        if (!vault.isVaultEnabled()) {
            return CommandResult.error("Vault is not enabled. Enable it in vault-config.properties");
        }

        vault.refreshSecrets();
        Map<String, String> secrets = vault.getAllSecrets();

        return CommandResult.success(
                "Successfully refreshed secrets from Vault. Loaded " + secrets.size() + " secrets."
        );
    }

    private CommandResult handleTest(VaultSecretManager vault) {
        if (!vault.isVaultEnabled()) {
            return CommandResult.error("Vault is not enabled. Enable it in vault-config.properties");
        }

        boolean connected = vault.testConnection();
        if (connected) {
            return CommandResult.success("✓ Vault connection test successful");
        } else {
            return CommandResult.error("✗ Vault connection test failed. Check logs for details.");
        }
    }

    private String maskSecret(String value) {
        if (value == null || value.isEmpty()) {
            return "(empty)";
        }
        if (value.length() <= 8) {
            return "*".repeat(value.length());
        }
        return value.substring(0, 4) + "*".repeat(value.length() - 8) + value.substring(value.length() - 4);
    }

    private String getHelp() {
        return """
                Vault Secret Management

                Usage: vault <command> [arguments]

                Commands:
                  status              Show Vault connection status
                  list                List all cached secrets (masked)
                  get <key>           Get a specific secret (masked)
                  write <key> <value> Write a secret to Vault
                  delete <key>        Delete a secret from Vault
                  refresh             Refresh secrets from Vault
                  test                Test Vault connection

                Examples:
                  vault status
                  vault list
                  vault get DISCORD_TOKEN
                  vault write NEW_API_KEY abc123xyz
                  vault delete OLD_API_KEY
                  vault refresh
                  vault test

                Note: Enable Vault in vault-config.properties first
                """;
    }
}
