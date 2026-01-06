package me.hash.mediaroulette.utils.terminal.commands;

import me.hash.mediaroulette.utils.resources.ResourceManager;
import me.hash.mediaroulette.utils.terminal.Command;
import me.hash.mediaroulette.utils.terminal.CommandResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static me.hash.mediaroulette.utils.terminal.TerminalColors.*;

/**
 * Terminal command for managing application resources.
 */
public class ResourceCommand extends Command {

    public ResourceCommand() {
        super(
                "resource",
                "Manage application resources",
                "resource <reload|status|list>",
                List.of("resources", "res")
        );
    }

    @Override
    public CommandResult execute(String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + getUsage());
        }

        String action = args[0].toLowerCase(Locale.ROOT);

        return switch (action) {
            case "reload" -> handleReload();
            case "status" -> handleStatus();
            case "list" -> handleList();
            default -> CommandResult.error("Unknown action: " + action + 
                    "\nAvailable actions: reload, status, list");
        };
    }

    private CommandResult handleReload() {
        try {
            ResourceManager manager = ResourceManager.getInstance();
            
            StringBuilder result = new StringBuilder();
            result.append(header("Resource Reload")).append("\n");
            result.append(dim("─".repeat(40))).append("\n\n");
            
            result.append("  ").append(yellow("⟳")).append(" Reloading resources from GitHub...\n");
            
            // Use the reload method
            manager.reload().thenRun(() -> {
                // Reload is async, log completion
            }).exceptionally(e -> {
                // Log error
                return null;
            });
            
            result.append("  ").append(green("✓")).append(" Resource reload initiated\n\n");
            result.append(dim("Resources will be downloaded in the background."));
            
            return CommandResult.success(result.toString());
        } catch (Exception e) {
            return CommandResult.error("Failed to reload resources: " + e.getMessage());
        }
    }

    private CommandResult handleStatus() {
        try {
            ResourceManager manager = ResourceManager.getInstance();
            
            StringBuilder status = new StringBuilder();
            status.append(header("Resource Manager Status")).append("\n");
            status.append(dim("─".repeat(40))).append("\n\n");
            
            boolean initialized = manager.isInitialized();
            int count = manager.getResourceCount();
            
            status.append("  ").append(bold("Initialized:")).append("  ");
            status.append(initialized ? green("Yes") : yellow("No")).append("\n");
            
            status.append("  ").append(bold("Resources:")).append("    ");
            status.append(cyan(String.valueOf(count))).append(" files tracked\n");
            
            status.append("  ").append(bold("Directory:")).append("    ");
            status.append(dim(manager.getResourcesDirectory().toAbsolutePath().toString())).append("\n");
            
            return CommandResult.success(status.toString());
        } catch (Exception e) {
            return CommandResult.error("Failed to get resource status: " + e.getMessage());
        }
    }

    private CommandResult handleList() {
        try {
            ResourceManager manager = ResourceManager.getInstance();
            
            StringBuilder list = new StringBuilder();
            list.append(header("Resource Types")).append("\n");
            list.append(dim("─".repeat(40))).append("\n\n");
            
            for (ResourceManager.ResourceType type : ResourceManager.ResourceType.values()) {
                list.append("  ").append(cyan("•")).append(" ");
                list.append(bold(type.name())).append(" ");
                list.append(dim("(" + type.getFolder() + ")")).append("\n");
            }
            
            list.append("\n").append(dim("Use 'resource status' to see detailed information."));
            
            return CommandResult.success(list.toString());
        } catch (Exception e) {
            return CommandResult.error("Failed to list resources: " + e.getMessage());
        }
    }

    @Override
    public List<String> getCompletions(String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> actions = List.of("reload", "status", "list");
            for (String action : actions) {
                if (action.startsWith(partial)) {
                    completions.add(action);
                }
            }
        }

        return completions;
    }

    @Override
    public String getDetailedHelp() {
        StringBuilder help = new StringBuilder();
        
        help.append(header("Command: ")).append(command("resource")).append("\n");
        help.append("Manage application resources downloaded from GitHub.\n\n");
        
        help.append(header("Subcommands:")).append("\n");
        help.append("  ").append(cyan("reload")).append("   - Re-download all resources from GitHub\n");
        help.append("  ").append(cyan("status")).append("   - Show resource manager status\n");
        help.append("  ").append(cyan("list")).append("     - List available resource types\n\n");
        
        help.append(header("Examples:")).append("\n");
        help.append("  ").append(dim("resource reload")).append("   - Force refresh all resources\n");
        help.append("  ").append(dim("resource status")).append("   - Check initialization status\n\n");
        
        help.append(header("Aliases:")).append("\n");
        help.append("  resources, res\n");
        
        return help.toString();
    }
}
