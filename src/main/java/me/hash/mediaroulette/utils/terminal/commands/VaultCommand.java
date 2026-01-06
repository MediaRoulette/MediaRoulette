package me.hash.mediaroulette.utils.terminal.commands;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.utils.terminal.Command;
import me.hash.mediaroulette.utils.terminal.CommandResult;
import me.hash.mediaroulette.utils.vault.VaultSecretManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static me.hash.mediaroulette.utils.terminal.TerminalColors.*;

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
            return CommandResult.error("Usage: " + getUsage());
        }

        VaultSecretManager vault = Main.getVaultSecretManager();
        if (vault == null) {
            return CommandResult.error("Vault secret manager not initialized");
        }

        String subcommand = args[0].toLowerCase();

        return switch (subcommand) {
            case "status", "s" -> handleStatus(vault);
            case "list", "ls" -> handleList(vault);
            case "get", "g" -> handleGet(vault, args);
            case "write", "set", "w" -> handleWrite(vault, args);
            case "delete", "remove", "rm" -> handleDelete(vault, args);
            case "refresh", "r" -> handleRefresh(vault);
            case "test", "t" -> handleTest(vault);
            default -> CommandResult.error("Unknown subcommand: " + subcommand + 
                    "\nUse 'help vault' for available commands.");
        };
    }

    private CommandResult handleStatus(VaultSecretManager vault) {
        StringBuilder sb = new StringBuilder();
        sb.append(header("Vault Status")).append("\n");
        sb.append(dim("─".repeat(50))).append("\n\n");
        
        sb.append("  ").append(bold("Enabled:")).append("    ");
        sb.append(vault.isVaultEnabled() ? green("Yes") : yellow("No")).append("\n");

        if (vault.isVaultEnabled()) {
            boolean connected = vault.testConnection();
            sb.append("  ").append(bold("Connected:")).append("  ");
            sb.append(connected ? green("Yes") : red("No")).append("\n");

            Map<String, String> secrets = vault.getAllSecrets();
            sb.append("  ").append(bold("Secrets:")).append("    ");
            sb.append(cyan(String.valueOf(secrets.size()))).append(" cached\n");
        } else {
            sb.append("\n  ").append(dim("Using fallback to .env and environment variables"));
        }

        return CommandResult.success(sb.toString());
    }

    private CommandResult handleList(VaultSecretManager vault) {
        if (!vault.isVaultEnabled()) {
            return CommandResult.error("Vault is not enabled. Enable it in vault-config.properties");
        }

        Map<String, String> secrets = vault.getAllSecrets();
        if (secrets.isEmpty()) {
            return CommandResult.success(dim("No secrets cached. Run 'vault refresh' to load from Vault."));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(header("Cached Secrets")).append(" ").append(dim("(" + secrets.size() + ")")).append("\n");
        sb.append(dim("─".repeat(50))).append("\n\n");

        secrets.keySet().stream()
                .sorted()
                .forEach(key -> {
                    String value = secrets.get(key);
                    String masked = maskSecret(value);
                    sb.append("  ").append(cyan(key)).append("\n");
                    sb.append("    ").append(dim(masked)).append("\n");
                });

        return CommandResult.success(sb.toString());
    }

    private CommandResult handleGet(VaultSecretManager vault, String[] args) {
        if (args.length < 2) {
            return CommandResult.error("Usage: vault get <key>");
        }

        String key = args[1];
        String value = vault.getSecret(key);

        if (value == null) {
            return CommandResult.error("Secret not found: " + key);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(header("Secret")).append("\n");
        sb.append(dim("─".repeat(40))).append("\n\n");
        sb.append("  ").append(bold("Key:")).append("   ").append(cyan(key)).append("\n");
        sb.append("  ").append(bold("Value:")).append(" ").append(dim(maskSecret(value))).append("\n\n");
        sb.append(dim("Use Main.getEnv(\"" + key + "\") to access in code"));

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
        String value = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));

        boolean success = vault.writeSecret(key, value);
        if (success) {
            return CommandResult.success(green("✓") + " Wrote secret: " + bold(key));
        } else {
            return CommandResult.error("Failed to write secret. Check logs for details.");
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
            return CommandResult.success(green("✓") + " Deleted secret: " + bold(key));
        } else {
            return CommandResult.error("Failed to delete secret. Check logs for details.");
        }
    }

    private CommandResult handleRefresh(VaultSecretManager vault) {
        if (!vault.isVaultEnabled()) {
            return CommandResult.error("Vault is not enabled. Enable it in vault-config.properties");
        }

        vault.refreshSecrets();
        Map<String, String> secrets = vault.getAllSecrets();

        return CommandResult.success(green("✓") + " Refreshed " + cyan(String.valueOf(secrets.size())) + " secrets from Vault");
    }

    private CommandResult handleTest(VaultSecretManager vault) {
        if (!vault.isVaultEnabled()) {
            return CommandResult.error("Vault is not enabled. Enable it in vault-config.properties");
        }

        boolean connected = vault.testConnection();
        if (connected) {
            return CommandResult.success(green("✓") + " Vault connection test successful");
        } else {
            return CommandResult.error(red("✗") + " Vault connection test failed. Check logs for details.");
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

    @Override
    public List<String> getCompletions(String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> subcommands = List.of("status", "list", "get", "write", "delete", "refresh", "test");
            for (String cmd : subcommands) {
                if (cmd.startsWith(partial)) {
                    completions.add(cmd);
                }
            }
        } else if (args.length == 2) {
            String action = args[0].toLowerCase();
            String partial = args[1].toLowerCase();
            
            // Complete secret keys for get/delete
            if (List.of("get", "g", "delete", "remove", "rm").contains(action)) {
                VaultSecretManager vault = Main.getVaultSecretManager();
                if (vault != null && vault.isVaultEnabled()) {
                    completions.addAll(vault.getAllSecrets().keySet().stream()
                            .filter(k -> k.toLowerCase().startsWith(partial))
                            .sorted()
                            .collect(Collectors.toList()));
                }
            }
        }

        return completions;
    }

    @Override
    public String getDetailedHelp() {
        StringBuilder help = new StringBuilder();
        
        help.append(header("Command: ")).append(command("vault")).append("\n");
        help.append("Manage HashiCorp Vault secrets for secure configuration.\n\n");
        
        help.append(header("Subcommands:")).append("\n");
        help.append("  ").append(cyan("status")).append("              - Show Vault connection status\n");
        help.append("  ").append(cyan("list")).append("                - List all cached secrets (masked)\n");
        help.append("  ").append(cyan("get <key>")).append("           - Get a specific secret (masked)\n");
        help.append("  ").append(cyan("write <key> <value>")).append(" - Write a secret to Vault\n");
        help.append("  ").append(cyan("delete <key>")).append("        - Delete a secret from Vault\n");
        help.append("  ").append(cyan("refresh")).append("             - Refresh cached secrets from Vault\n");
        help.append("  ").append(cyan("test")).append("                - Test Vault connection\n\n");
        
        help.append(header("Examples:")).append("\n");
        help.append("  ").append(dim("vault status")).append("               - Check connection\n");
        help.append("  ").append(dim("vault get DISCORD_TOKEN")).append("    - Get a secret\n");
        help.append("  ").append(dim("vault write API_KEY abc123")).append(" - Write a secret\n");
        help.append("  ").append(dim("vault refresh")).append("              - Reload from Vault\n\n");
        
        help.append(header("Tab Completion:")).append("\n");
        help.append("  Press Tab to complete subcommands and secret keys.\n\n");
        
        help.append(header("Note:")).append("\n");
        help.append("  Enable Vault in vault-config.properties first.\n\n");
        
        help.append(header("Aliases:")).append("\n");
        help.append("  vlt\n");
        
        return help.toString();
    }
}
