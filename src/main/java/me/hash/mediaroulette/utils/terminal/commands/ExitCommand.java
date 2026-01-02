package me.hash.mediaroulette.utils.terminal.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.hash.mediaroulette.utils.terminal.Command;
import me.hash.mediaroulette.utils.terminal.CommandResult;

import java.util.List;

import static me.hash.mediaroulette.utils.terminal.TerminalColors.*;

public class ExitCommand extends Command {
    private static final Logger logger = LoggerFactory.getLogger(ExitCommand.class);

    public ExitCommand() {
        super("exit", "Exit the application", "exit", List.of("quit", "q"));
    }

    @Override
    public CommandResult execute(String[] args) {
        logger.info("Shutting down...");
        // Use the proper shutdown method instead of System.exit(0)
        me.hash.mediaroulette.Main.shutdown();
        return CommandResult.success(yellow("Goodbye!"));
    }
    
    @Override
    public String getDetailedHelp() {
        StringBuilder help = new StringBuilder();
        help.append(header("Command: ")).append(command("exit")).append("\n");
        help.append("Gracefully shutdown the application.\n\n");
        
        help.append(header("Behavior:")).append("\n");
        help.append("  This command initiates a graceful shutdown which:\n");
        help.append("  • Stops the terminal interface\n");
        help.append("  • Disconnects the Discord bot\n");
        help.append("  • Closes database connections\n");
        help.append("  • Cleans up media processing resources\n\n");
        
        help.append(header("Usage:")).append("\n");
        help.append("  ").append(cyan("exit")).append("\n\n");
        
        help.append(header("Aliases:")).append("\n");
        help.append("  quit, q\n\n");
        
        help.append(header("Tip:")).append("\n");
        help.append("  You can also press ").append(bold("Ctrl+D")).append(" to exit.\n");
        
        return help.toString();
    }
}
