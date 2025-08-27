package me.hash.mediaroulette.utils.terminal.commands;

import me.hash.mediaroulette.utils.terminal.Command;
import me.hash.mediaroulette.utils.terminal.CommandResult;
import me.hash.mediaroulette.Main;
import java.util.List;

public class PluginCommand extends Command {
    public PluginCommand() {
        super("plugin", "Manage plugins", "plugin <list|reload|info> [name]", List.of("plugins"));
    }

    @Override
    public CommandResult execute(String[] args) {
        if (args.length == 0) {
            return new CommandResult(false, "Usage: " + getUsage());
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "list":
                return listPlugins();
            case "reload":
                return reloadPlugins();
            case "info":
                if (args.length < 2) {
                    return new CommandResult(false, "Usage: plugin info <name>");
                }
                return pluginInfo(args[1]);
            default:
                return new CommandResult(false, "Unknown action: " + action);
        }
    }

    private CommandResult listPlugins() {
        try {
            if (Main.getPluginManager() == null) {
                return new CommandResult(true, "Plugin manager not initialized");
            }
            return new CommandResult(true, "Plugins: " + Main.getPluginManager().getLoadedPlugins().size() + " loaded");
        } catch (Exception e) {
            return new CommandResult(false, "Error listing plugins: " + e.getMessage());
        }
    }

    private CommandResult reloadPlugins() {
        try {
            if (Main.getPluginManager() == null) {
                return new CommandResult(false, "Plugin manager not initialized");
            }
            Main.getPluginManager().reloadPlugins();
            return new CommandResult(true, "Plugins reloaded");
        } catch (Exception e) {
            return new CommandResult(false, "Error reloading plugins: " + e.getMessage());
        }
    }

    private CommandResult pluginInfo(String name) {
        try {
            if (Main.getPluginManager() == null) {
                return new CommandResult(false, "Plugin manager not initialized");
            }
            return new CommandResult(true, "Plugin info for: " + name);
        } catch (Exception e) {
            return new CommandResult(false, "Error getting plugin info: " + e.getMessage());
        }
    }

    @Override
    public List<String> getCompletions(String[] args) {
        if (args.length == 1) {
            return List.of("list", "reload", "info");
        }
        return List.of();
    }
}