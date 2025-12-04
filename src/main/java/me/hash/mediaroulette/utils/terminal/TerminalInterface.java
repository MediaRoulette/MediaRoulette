package me.hash.mediaroulette.utils.terminal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.hash.mediaroulette.utils.terminal.commands.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public class TerminalInterface {
    private static final Logger logger = LoggerFactory.getLogger(TerminalInterface.class);
    private final CommandSystem commandSystem;
    private final BufferedReader reader;
    private volatile boolean running = true;

    public TerminalInterface() {
        this.commandSystem = new CommandSystem();
        this.reader = new BufferedReader(new InputStreamReader(System.in));
        registerCommands();
    }

    private void registerCommands() {
        commandSystem.registerCommand(new HelpCommand(commandSystem));
        commandSystem.registerCommand(new UserCommand());
        commandSystem.registerCommand(new ExitCommand());
        commandSystem.registerCommand(new StatusCommand());
        commandSystem.registerCommand(new StatsCommand());
        commandSystem.registerCommand(new RateLimitCommand());
        commandSystem.registerCommand(new ClearCommand());
        commandSystem.registerCommand(new PluginCommand());
        commandSystem.registerCommand(new AnalyticsCommand());
        commandSystem.registerCommand(new VaultCommand());
    }

    public void start() {
        logger.info("=== Media Roulette Terminal ===");
        logger.info("Type 'help' for available commands or 'exit' to quit.");

        while (running) {
            try {
                System.out.print("mediaroulette> "); // Keep this as console prompt
                String input = reader.readLine();

                if (input == null || !running) {
                    break; // EOF or shutdown requested
                }

                input = input.trim();
                if (input.isEmpty()) {
                    continue;
                }

                // Handle tab completion simulation (basic)
                if (input.equals("tab")) {
                    showCompletions("");
                    continue;
                }

                CommandResult result = commandSystem.executeCommand(input);

                if (result.isSuccess()) {
                    if (!result.getMessage().isEmpty()) {
                        logger.info(result.getMessage());
                    }
                } else {
                    logger.error("Error: {}", result.getMessage());
                }

            } catch (IOException e) {
                if (running) {
                    logger.error("Error reading input: {}", e.getMessage());
                }
                break;
            } catch (Exception e) {
                logger.error("Unexpected error: {}", e.getMessage(), e);
            }
        }
        
        // Close the reader when exiting the loop
        try {
            reader.close();
        } catch (IOException e) {
            // Ignore close errors
        }
    }

    private void showCompletions(String input) {
        List<String> completions = commandSystem.getCompletions(input);
        if (!completions.isEmpty()) {
            logger.info("Available completions:");
            for (String completion : completions) {
                logger.info("  {}", completion);
            }
        } else {
            logger.info("No completions available.");
        }
    }

    public void stop() {
        running = false;
        // Close the reader to interrupt the readLine() call
        try {
            reader.close();
        } catch (IOException e) {
            // Ignore close errors during shutdown
        }
    }
}