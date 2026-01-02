package me.hash.mediaroulette.utils.terminal.commands;

import me.hash.mediaroulette.utils.terminal.Command;
import me.hash.mediaroulette.utils.terminal.CommandResult;
import me.hash.mediaroulette.utils.terminal.CommandSystem;

import java.util.List;

import static me.hash.mediaroulette.utils.terminal.TerminalColors.*;

public class HelpCommand extends Command {
    private final CommandSystem commandSystem;

    public HelpCommand(CommandSystem commandSystem) {
        super("help", "Show help information", "help [command]", List.of("h", "?"));
        this.commandSystem = commandSystem;
    }

    @Override
    public CommandResult execute(String[] args) {
        if (args.length == 0) {
            return CommandResult.success(commandSystem.getHelp());
        } else {
            String commandName = args[0].toLowerCase();
            Command command = commandSystem.getCommand(commandName);
            
            if (command == null) {
                return CommandResult.error("Unknown command: " + red(commandName) + 
                    ". Type 'help' to see available commands.");
            }
            
            return CommandResult.success(command.getDetailedHelp());
        }
    }

    @Override
    public List<String> getCompletions(String[] args) {
        if (args.length == 1) {
            return commandSystem.getCommandNames().stream()
                    .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                    .sorted()
                    .toList();
        }
        return List.of();
    }
    
    @Override
    public String getDetailedHelp() {
        StringBuilder help = new StringBuilder();
        help.append(header("Command: ")).append(command("help")).append("\n");
        help.append("Display help information about available commands.\n\n");
        
        help.append(header("Usage:")).append("\n");
        help.append("  ").append(cyan("help")).append("           - Show list of all commands\n");
        help.append("  ").append(cyan("help <command>")).append("  - Show detailed help for a specific command\n\n");
        
        help.append(header("Examples:")).append("\n");
        help.append("  ").append(dim("help")).append("          - List all commands\n");
        help.append("  ").append(dim("help status")).append("   - Show help for the status command\n");
        help.append("  ").append(dim("help user")).append("     - Show help for the user command\n\n");
        
        help.append(header("Aliases:")).append("\n");
        help.append("  h, ?\n");
        
        return help.toString();
    }
}
