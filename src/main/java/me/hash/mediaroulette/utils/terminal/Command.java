package me.hash.mediaroulette.utils.terminal;

import java.util.List;

import static me.hash.mediaroulette.utils.terminal.TerminalColors.*;

public abstract class Command {
    protected final String name;
    protected final String description;
    protected final String usage;
    protected final List<String> aliases;

    public Command(String name, String description, String usage, List<String> aliases) {
        this.name = name;
        this.description = description;
        this.usage = usage;
        this.aliases = aliases != null ? aliases : List.of();
    }

    public abstract CommandResult execute(String[] args);

    public List<String> getCompletions(String[] args) {
        return List.of(); // Default: no completions
    }

    /**
     * Returns detailed help text for this command.
     * Override this method in subclasses to provide comprehensive help.
     */
    public String getDetailedHelp() {
        StringBuilder help = new StringBuilder();
        
        // Command name and description
        help.append(header("Command: ")).append(command(name)).append("\n");
        help.append(description).append("\n\n");
        
        // Usage
        help.append(header("Usage:")).append("\n");
        help.append("  ").append(cyan(usage)).append("\n\n");
        
        // Aliases
        if (!aliases.isEmpty()) {
            help.append(header("Aliases:")).append("\n");
            help.append("  ").append(String.join(", ", aliases)).append("\n");
        }
        
        return help.toString();
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getUsage() { return usage; }
    public List<String> getAliases() { return aliases; }
}
