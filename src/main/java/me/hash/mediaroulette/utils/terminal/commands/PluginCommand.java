package me.hash.mediaroulette.utils.terminal.commands;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.plugins.Plugin;
import me.hash.mediaroulette.utils.terminal.Command;
import me.hash.mediaroulette.utils.terminal.CommandResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static me.hash.mediaroulette.utils.terminal.TerminalColors.*;

public class PluginCommand extends Command {
    private static final String USAGE = "plugin <list|info|enable|disable|reload|unload|add|update> [args...]";
    
    public PluginCommand() {
        super("plugin", "Manage plugins", USAGE, List.of("plugins", "pl"));
    }

    @Override
    public CommandResult execute(String[] args) {
        if (args.length == 0) {
            return CommandResult.error("Usage: " + getUsage());
        }

        if (Main.getPluginManager() == null) {
            return CommandResult.error("Plugin manager not initialized");
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "list", "ls" -> listPlugins();
            case "info", "i" -> {
                if (args.length < 2) yield CommandResult.error("Usage: plugin info <name>");
                yield pluginInfo(args[1]);
            }
            case "enable", "e" -> {
                if (args.length < 2) yield CommandResult.error("Usage: plugin enable <name>");
                yield enablePlugin(args[1]);
            }
            case "disable", "d" -> {
                if (args.length < 2) yield CommandResult.error("Usage: plugin disable <name>");
                yield disablePlugin(args[1]);
            }
            case "reload", "r" -> {
                if (args.length < 2) {
                    // Reload all plugins
                    boolean preserve = hasFlag(args, "--keep-disabled", false);
                    Main.getPluginManager().reloadPlugins(preserve);
                    yield CommandResult.success(green("✓") + " All plugins reloaded" + 
                            (preserve ? " (kept disabled state)" : ""));
                } else {
                    // Reload single plugin
                    yield reloadPlugin(args[1]);
                }
            }
            case "unload", "u" -> {
                if (args.length < 2) yield CommandResult.error("Usage: plugin unload <name>");
                yield unloadPlugin(args[1]);
            }
            case "add" -> {
                if (args.length < 2) yield CommandResult.error("Usage: plugin add <path-to-jar> [--enable|--disable]");
                yield addOrUpdateJar(args[1], hasFlag(args, "--enable", true));
            }
            case "update" -> {
                if (args.length < 2) yield CommandResult.error("Usage: plugin update <path-to-jar> [--enable|--disable]");
                yield addOrUpdateJar(args[1], hasFlag(args, "--enable", true));
            }
            default -> CommandResult.error("Unknown action: " + action + 
                    "\nUse 'help plugin' for available commands.");
        };
    }

    private CommandResult listPlugins() {
        try {
            List<Plugin> list = new ArrayList<>(Main.getPluginManager().getLoadedPlugins());
            list.sort(Comparator.comparing(Plugin::getName, String.CASE_INSENSITIVE_ORDER));
            
            if (list.isEmpty()) {
                return CommandResult.success(dim("No plugins loaded."));
            }

            StringBuilder result = new StringBuilder();
            result.append(header("Loaded Plugins")).append(" ").append(dim("(" + list.size() + ")")).append("\n");
            result.append(dim("─".repeat(50))).append("\n\n");

            for (Plugin p : list) {
                String status = p.isEnabled() ? green("●") : red("○");
                String statusText = p.isEnabled() ? green("enabled") : dim("disabled");
                
                result.append("  ").append(status).append(" ");
                result.append(bold(p.getName()));
                result.append(dim(" v" + safe(p.getVersion())));
                result.append(" [").append(statusText).append("]\n");
            }

            return CommandResult.success(result.toString());
        } catch (Exception e) {
            return CommandResult.error("Error listing plugins: " + e.getMessage());
        }
    }

    private CommandResult pluginInfo(String name) {
        Plugin p = Main.getPluginManager().findPlugin(name);
        if (p == null) {
            List<String> suggestions = Main.getPluginManager().getPluginNames().stream()
                    .filter(n -> n.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)))
                    .limit(5)
                    .toList();
            String hint = suggestions.isEmpty() ? "" : "\n\nDid you mean: " + String.join(", ", suggestions) + "?";
            return CommandResult.error("Plugin not found: " + name + hint);
        }
        
        StringBuilder info = new StringBuilder();
        info.append(header("Plugin: ")).append(command(p.getName())).append("\n");
        info.append(dim("─".repeat(40))).append("\n\n");
        
        info.append("  ").append(bold("Version:")).append("     ").append(cyan(safe(p.getVersion()))).append("\n");
        info.append("  ").append(bold("Status:")).append("      ");
        info.append(p.isEnabled() ? green("Enabled") : red("Disabled")).append("\n");
        
        if (p.getDescription() != null) {
            if (p.getDescription().getDescription() != null && !p.getDescription().getDescription().isBlank()) {
                info.append("  ").append(bold("Description:")).append(" ").append(p.getDescription().getDescription()).append("\n");
            }
            
            String author = p.getDescription().getAuthor();
            List<String> authors = p.getDescription().getAuthors();
            if (author != null || (authors != null && !authors.isEmpty())) {
                info.append("  ").append(bold("Author(s):")).append("   ");
                if (authors != null && !authors.isEmpty()) {
                    info.append(String.join(", ", authors));
                } else {
                    info.append(author);
                }
                info.append("\n");
            }
            
            List<String> depend = p.getDescription().getDepend();
            if (depend != null && !depend.isEmpty()) {
                info.append("  ").append(bold("Dependencies:")).append(" ").append(String.join(", ", depend)).append("\n");
            }
        }
        
        return CommandResult.success(info.toString());
    }

    private CommandResult enablePlugin(String name) {
        boolean ok = Main.getPluginManager().enablePlugin(name);
        if (!ok) return CommandResult.error(red("✗") + " Unable to enable: " + name);
        return CommandResult.success(green("✓") + " Enabled plugin: " + bold(name));
    }

    private CommandResult disablePlugin(String name) {
        boolean ok = Main.getPluginManager().disablePlugin(name);
        if (!ok) return CommandResult.error(red("✗") + " Unable to disable: " + name);
        return CommandResult.success(green("✓") + " Disabled plugin: " + bold(name));
    }

    private CommandResult reloadPlugin(String name) {
        boolean ok = Main.getPluginManager().reloadPlugin(name);
        if (!ok) return CommandResult.error(red("✗") + " Unable to reload: " + name);
        return CommandResult.success(green("✓") + " Reloaded plugin: " + bold(name));
    }

    private CommandResult unloadPlugin(String name) {
        boolean ok = Main.getPluginManager().unloadPlugin(name);
        if (!ok) return CommandResult.error(red("✗") + " Unable to unload: " + name);
        return CommandResult.success(green("✓") + " Unloaded plugin: " + bold(name));
    }

    private CommandResult addOrUpdateJar(String path, boolean enableAfterLoad) {
        try {
            File jar = new File(path);
            if (!jar.exists() || !jar.isFile()) {
                return CommandResult.error("JAR not found: " + path);
            }
            Main.getPluginManager().addOrUpdatePluginJar(jar, enableAfterLoad);
            return CommandResult.success(green("✓") + " Loaded JAR: " + bold(jar.getName()));
        } catch (Exception e) {
            return CommandResult.error("Failed to load JAR: " + e.getMessage());
        }
    }

    @Override
    public List<String> getCompletions(String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return List.of("list", "info", "enable", "disable", "reload", "unload", "add", "update")
                    .stream()
                    .filter(a -> a.startsWith(partial))
                    .collect(Collectors.toList());
        }
        
        if (args.length == 2) {
            String action = args[0].toLowerCase(Locale.ROOT);
            String partial = args[1].toLowerCase(Locale.ROOT);
            
            // Commands that accept plugin names
            if (List.of("info", "i", "enable", "e", "disable", "d", "reload", "r", "unload", "u").contains(action)) {
                return Main.getPluginManager().getPluginNames().stream()
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(partial))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
            }
        }
        
        return List.of();
    }

    @Override
    public String getDetailedHelp() {
        StringBuilder help = new StringBuilder();
        
        help.append(header("Command: ")).append(command("plugin")).append("\n");
        help.append("Manage runtime plugins - load, unload, enable, and hot-reload.\n\n");
        
        help.append(header("Subcommands:")).append("\n");
        help.append("  ").append(cyan("list")).append("              - List all loaded plugins\n");
        help.append("  ").append(cyan("info <name>")).append("       - Show detailed plugin information\n");
        help.append("  ").append(cyan("enable <name>")).append("     - Enable a disabled plugin\n");
        help.append("  ").append(cyan("disable <name>")).append("    - Disable an enabled plugin\n");
        help.append("  ").append(cyan("reload [name]")).append("     - Reload a single plugin or all plugins\n");
        help.append("  ").append(cyan("unload <name>")).append("     - Unload a plugin from memory\n");
        help.append("  ").append(cyan("add <jar>")).append("         - Add a new plugin from JAR file\n");
        help.append("  ").append(cyan("update <jar>")).append("      - Update/replace an existing plugin\n\n");
        
        help.append(header("Hot-Reload:")).append("\n");
        help.append("  Plugins are loaded into memory, so you can safely replace\n");
        help.append("  JAR files on disk and use ").append(cyan("reload <name>")).append(" to apply changes.\n\n");
        
        help.append(header("Examples:")).append("\n");
        help.append("  ").append(dim("plugin list")).append("                     - Show all plugins\n");
        help.append("  ").append(dim("plugin info RedditPlugin")).append("        - Show plugin details\n");
        help.append("  ").append(dim("plugin reload EconomyPlugin")).append("     - Hot-reload a single plugin\n");
        help.append("  ").append(dim("plugin reload")).append("                   - Reload all plugins\n");
        help.append("  ").append(dim("plugin add ./new-plugin.jar")).append("     - Load a new plugin\n\n");
        
        help.append(header("Aliases:")).append("\n");
        help.append("  plugins, pl\n");
        
        return help.toString();
    }

    private static String safe(String s) { return s == null ? "unknown" : s; }

    private static boolean hasFlag(String[] args, String flag, boolean defaultValue) {
        for (String a : args) {
            if (a.equalsIgnoreCase(flag)) return true;
            if (a.equalsIgnoreCase("--disable") && flag.equalsIgnoreCase("--enable")) return false;
        }
        return defaultValue;
    }
}
