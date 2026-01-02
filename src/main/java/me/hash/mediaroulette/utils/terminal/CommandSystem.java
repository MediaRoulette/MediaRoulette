package me.hash.mediaroulette.utils.terminal;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static me.hash.mediaroulette.utils.terminal.TerminalColors.*;

public class CommandSystem {
    private final Map<String, Command> commands = new ConcurrentHashMap<>();
    private final Map<String, String> aliases = new ConcurrentHashMap<>();

    public void registerCommand(Command command) {
        commands.put(command.getName().toLowerCase(), command);
        for (String alias : command.getAliases()) {
            aliases.put(alias.toLowerCase(), command.getName().toLowerCase());
        }
    }

    public CommandResult executeCommand(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new CommandResult(false, "Empty command");
        }

        String[] parts = input.trim().split("\\s+");
        String commandName = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        // Check aliases first
        if (aliases.containsKey(commandName)) {
            commandName = aliases.get(commandName);
        }

        Command command = commands.get(commandName);
        if (command == null) {
            return new CommandResult(false, "Unknown command: " + parts[0] + ". Type 'help' for available commands.");
        }

        try {
            return command.execute(args);
        } catch (Exception e) {
            return new CommandResult(false, "Error executing command: " + e.getMessage());
        }
    }

    /**
     * Get a command by name or alias.
     */
    public Command getCommand(String name) {
        if (name == null) return null;
        String lowerName = name.toLowerCase();
        
        // Check if it's an alias first
        if (aliases.containsKey(lowerName)) {
            lowerName = aliases.get(lowerName);
        }
        
        return commands.get(lowerName);
    }

    /**
     * Get all registered commands.
     */
    public Collection<Command> getCommands() {
        return commands.values();
    }

    public List<String> getCompletions(String input) {
        List<String> completions = new ArrayList<>();

        if (input == null || input.trim().isEmpty()) {
            // Return all command names
            completions.addAll(commands.keySet());
            completions.addAll(aliases.keySet());
            return completions;
        }

        String[] parts = input.trim().split("\\s+");

        if (parts.length == 1) {
            // Complete command name
            String partial = parts[0].toLowerCase();
            for (String cmdName : commands.keySet()) {
                if (cmdName.startsWith(partial)) {
                    completions.add(cmdName);
                }
            }
            for (String alias : aliases.keySet()) {
                if (alias.startsWith(partial)) {
                    completions.add(alias);
                }
            }
        } else {
            // Complete command arguments
            String commandName = parts[0].toLowerCase();
            if (aliases.containsKey(commandName)) {
                commandName = aliases.get(commandName);
            }

            Command command = commands.get(commandName);
            if (command != null) {
                String[] args = Arrays.copyOfRange(parts, 1, parts.length);
                completions.addAll(command.getCompletions(args));
            }
        }

        Collections.sort(completions);
        return completions;
    }

    public String getHelp() {
        StringBuilder help = new StringBuilder();
        help.append(header("Available Commands")).append("\n");
        help.append(dim("─".repeat(50))).append("\n\n");

        // Sort commands by name
        List<Command> sortedCommands = new ArrayList<>(commands.values());
        sortedCommands.sort(Comparator.comparing(Command::getName));

        for (Command command : sortedCommands) {
            help.append("  ").append(command(command.getName()));
            
            // Pad command name for alignment
            int padding = 15 - command.getName().length();
            help.append(" ".repeat(Math.max(1, padding)));
            
            help.append(dim(command.getDescription())).append("\n");
        }
        
        help.append("\n").append(dim("Type 'help <command>' for detailed information about a specific command."));

        return help.toString();
    }

    public Set<String> getCommandNames() {
        return new HashSet<>(commands.keySet());
    }
}
