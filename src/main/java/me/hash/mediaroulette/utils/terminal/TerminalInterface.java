package me.hash.mediaroulette.utils.terminal;

import me.hash.mediaroulette.utils.terminal.commands.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public class TerminalInterface {
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
    }

    public void start() {
        System.out.println("=== Media Roulette Terminal ===");
        System.out.println("Type 'help' for available commands or 'exit' to quit.");
        System.out.println();

        while (running) {
            try {
                System.out.print("mediaroulette> ");
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
                        System.out.println(result.getMessage());
                    }
                } else {
                    System.err.println("Error: " + result.getMessage());
                }

                System.out.println();

            } catch (IOException e) {
                if (running) {
                    System.err.println("Error reading input: " + e.getMessage());
                }
                break;
            } catch (Exception e) {
                System.err.println("Unexpected error: " + e.getMessage());
                e.printStackTrace();
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
            System.out.println("Available completions:");
            for (String completion : completions) {
                System.out.println("  " + completion);
            }
        } else {
            System.out.println("No completions available.");
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