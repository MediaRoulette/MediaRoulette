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

public class PluginCommand extends Command {
    private static final String USAGE = "plugin <list|info|enable|disable|reload|add|update> [args...]";
    public PluginCommand() {
        super("plugin", "Manage plugins", USAGE, List.of("plugins"));
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
        switch (action) {
            case "list":
                return listPlugins();
            case "info":
                if (args.length < 2) return CommandResult.error("Usage: plugin info <name>");
                return pluginInfo(args[1]);
            case "enable":
                if (args.length < 2) return CommandResult.error("Usage: plugin enable <name>");
                return enablePlugin(args[1]);
            case "disable":
                if (args.length < 2) return CommandResult.error("Usage: plugin disable <name>");
                return disablePlugin(args[1]);
            case "reload":
                boolean preserve = args.length > 1 && (args[1].equalsIgnoreCase("keep-disabled") || args[1].equalsIgnoreCase("preserve"));
                Main.getPluginManager().reloadPlugins(preserve);
                return CommandResult.success("Plugins reloaded" + (preserve ? " (kept disabled state)" : ""));
            case "add":
                if (args.length < 2) return CommandResult.error("Usage: plugin add <path-to-jar> [--enable|--disable]");
                return addOrUpdateJar(args[1], hasFlag(args, "--enable", true));
            case "update":
                if (args.length < 2) return CommandResult.error("Usage: plugin update <path-to-jar> [--enable|--disable]");
                return addOrUpdateJar(args[1], hasFlag(args, "--enable", true));
            default:
                return CommandResult.error("Unknown action: " + action);
        }
    }

    private CommandResult listPlugins() {
        try {
            List<Plugin> list = new ArrayList<>(Main.getPluginManager().getLoadedPlugins());
            list.sort(Comparator.comparing(Plugin::getName, String.CASE_INSENSITIVE_ORDER));
            if (list.isEmpty()) return CommandResult.success("Plugins (0)");

            String summary = list.stream()
                    .map(p -> p.getName() + " v" + safe(p.getVersion()) + (p.isEnabled() ? " [enabled]" : " [disabled]"))
                    .collect(Collectors.joining(", "));
            return CommandResult.success("Plugins (" + list.size() + "): " + summary);
        } catch (Exception e) {
            return CommandResult.error("Error listing plugins: " + e.getMessage());
        }
    }

    private CommandResult pluginInfo(String name) {
        Plugin p = Main.getPluginManager().findPlugin(name);
        if (p == null) {
            // Try case-insensitive contains to help users
            List<String> suggestions = Main.getPluginManager().getPluginNames().stream()
                    .filter(n -> n.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT)))
                    .limit(5)
                    .toList();
            String hint = suggestions.isEmpty() ? "" : " Did you mean: " + String.join(", ", suggestions) + "?";
            return CommandResult.error("Plugin not found: " + name + "." + hint);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(p.getName()).append(" v").append(safe(p.getVersion())).append(p.isEnabled() ? " [enabled]" : " [disabled]");
        if (p.getDescription() != null) {
            if (p.getDescription().getDescription() != null && !p.getDescription().getDescription().isBlank()) {
                sb.append(" - ").append(p.getDescription().getDescription());
            }
            String author = p.getDescription().getAuthor();
            List<String> authors = p.getDescription().getAuthors();
            if (author != null || (authors != null && !authors.isEmpty())) {
                sb.append(" | Author(s): ");
                if (authors != null && !authors.isEmpty()) {
                    sb.append(String.join(", ", authors));
                    if (author != null && !authors.contains(author)) sb.append(", ").append(author);
                } else {
                    sb.append(author);
                }
            }
            List<String> depend = p.getDescription().getDepend();
            List<String> soft = p.getDescription().getSoftDepend();
            if ((depend != null && !depend.isEmpty()) || (soft != null && !soft.isEmpty())) {
                sb.append(" | Depends: ");
                if (depend != null && !depend.isEmpty()) sb.append(String.join(", ", depend));
                if (soft != null && !soft.isEmpty()) sb.append(" (soft: ").append(String.join(", ", soft)).append(")");
            }
        }
        return CommandResult.success(sb.toString());
    }

    private CommandResult enablePlugin(String name) {
        boolean ok = Main.getPluginManager().enablePlugin(name);
        if (!ok) return CommandResult.error("Unable to enable: " + name);
        return CommandResult.success("Enabled plugin: " + name);
    }

    private CommandResult disablePlugin(String name) {
        boolean ok = Main.getPluginManager().disablePlugin(name);
        if (!ok) return CommandResult.error("Unable to disable: " + name);
        return CommandResult.success("Disabled plugin: " + name);
    }

    private CommandResult addOrUpdateJar(String path, boolean defaultEnableFlag) {
        try {
            File jar = new File(path);
            if (!jar.exists() || !jar.isFile()) {
                return CommandResult.error("Jar not found: " + path);
            }
            Main.getPluginManager().addOrUpdatePluginJar(jar, defaultEnableFlag);
            return CommandResult.success("Loaded jar: " + jar.getName());
        } catch (Exception e) {
            return CommandResult.error("Failed to load jar: " + e.getMessage());
        }
    }

    @Override
    public List<String> getCompletions(String[] args) {
        if (args.length == 1) {
            return List.of("list", "info", "enable", "disable", "reload", "add", "update");
        }
        if (args.length == 2) {
            String action = args[0].toLowerCase(Locale.ROOT);
            if (action.equals("info") || action.equals("enable") || action.equals("disable")) {
                return Main.getPluginManager().getPluginNames().stream()
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
            }
        }
        return List.of();
    }

    private static String safe(String s) { return s == null ? "?" : s; }

    private static boolean hasFlag(String[] args, String flag, boolean defaultValue) {
        for (String a : args) {
            if (a.equalsIgnoreCase(flag)) return true;
            if (a.equalsIgnoreCase("--disable") && flag.equalsIgnoreCase("--enable")) return false;
        }
        return defaultValue;
    }
}
