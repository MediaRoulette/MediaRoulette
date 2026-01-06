package me.hash.mediaroulette.utils.terminal;

import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.hash.mediaroulette.utils.terminal.commands.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import static me.hash.mediaroulette.utils.terminal.TerminalColors.*;

/**
 * Interactive terminal interface using JLine3 for advanced features:
 * - Command history
 * - Tab completion
 * - Signal handling (Ctrl+C, Ctrl+D)
 * - Proper ANSI color support
 * - Log message integration without prompt disruption
 */
public class TerminalInterface {
    private static final Logger logger = LoggerFactory.getLogger(TerminalInterface.class);
    
    private static volatile TerminalInterface instance;
    
    private final CommandSystem commandSystem;
    private Terminal terminal;
    private LineReader lineReader;
    private volatile boolean running = true;
    private String currentBuffer = "";
    
    public TerminalInterface() {
        this.commandSystem = new CommandSystem();
        instance = this;
        registerCommands();
    }
    
    /**
     * Get the singleton instance of the terminal interface.
     */
    public static TerminalInterface getInstance() {
        return instance;
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
        commandSystem.registerCommand(new ResourceCommand());
    }

    public void start() {
        try {
            // Build JLine terminal with proper settings
            terminal = TerminalBuilder.builder()
                    .name("MediaRoulette")
                    .system(true)
                    .dumb(false)
                    .jansi(true)
                    .build();
            
            // Create completer for tab completion
            Completer completer = new Completer() {
                @Override
                public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
                    String buffer = line.line();
                    List<String> completions = commandSystem.getCompletions(buffer);
                    for (String completion : completions) {
                        candidates.add(new Candidate(completion));
                    }
                }
            };
            
            // Build line reader with features
            lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(completer)
                    .parser(new DefaultParser())
                    .option(LineReader.Option.CASE_INSENSITIVE, true)
                    .option(LineReader.Option.AUTO_FRESH_LINE, true)
                    .variable(LineReader.HISTORY_FILE, ".mediaroulette_history")
                    .build();
            
            // Print welcome message
            PrintWriter writer = terminal.writer();
            writer.println();
            writer.println(cyan("═".repeat(50)));
            writer.println(bold(cyan("  Media Roulette Terminal")));
            writer.println(cyan("═".repeat(50)));
            writer.println(dim("  Type 'help' for available commands"));
            writer.println(dim("  Use Tab for auto-completion"));
            writer.println(dim("  Ctrl+C to cancel, Ctrl+D to exit"));
            writer.println(cyan("═".repeat(50)));
            writer.println();
            writer.flush();
            
            logger.info("Terminal interface started");
            
            // Main input loop
            while (running) {
                try {
                    String prompt = green("mediaroulette") + dim("> ");
                    String input = lineReader.readLine(prompt);
                    
                    if (input == null) {
                        // EOF (Ctrl+D)
                        break;
                    }
                    
                    input = input.trim();
                    if (input.isEmpty()) {
                        continue;
                    }
                    
                    // Execute command
                    CommandResult result = commandSystem.executeCommand(input);
                    
                    if (result.isSuccess()) {
                        if (!result.getMessage().isEmpty()) {
                            writer.println(result.getMessage());
                        }
                    } else {
                        writer.println(error(result.getMessage()));
                    }
                    writer.flush();
                    
                } catch (UserInterruptException e) {
                    // Ctrl+C pressed - just print a new line and continue
                    terminal.writer().println(dim("^C"));
                    terminal.writer().flush();
                } catch (EndOfFileException e) {
                    // Ctrl+D pressed - exit gracefully
                    break;
                }
            }
            
        } catch (IOException e) {
            logger.error("Failed to initialize terminal: {}", e.getMessage());
            // Fall back to simple mode
            startSimpleMode();
        } finally {
            cleanup();
        }
    }
    
    /**
     * Fallback mode using simple System.in if JLine fails
     */
    private void startSimpleMode() {
        logger.info("Running in simple terminal mode");
        
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(System.in))) {
            
            System.out.println("\n=== Media Roulette Terminal (Simple Mode) ===");
            System.out.println("Type 'help' for available commands");
            
            while (running) {
                System.out.print("mediaroulette> ");
                String input = reader.readLine();
                
                if (input == null || !running) {
                    break;
                }
                
                input = input.trim();
                if (input.isEmpty()) {
                    continue;
                }
                
                CommandResult result = commandSystem.executeCommand(input);
                
                if (result.isSuccess()) {
                    if (!result.getMessage().isEmpty()) {
                        System.out.println(result.getMessage());
                    }
                } else {
                    System.out.println("Error: " + result.getMessage());
                }
            }
        } catch (IOException e) {
            logger.error("Error in simple terminal mode: {}", e.getMessage());
        }
    }
    
    /**
     * Print a message above the current input line.
     * Used by TerminalAppender to print log messages without disrupting user input.
     */
    public void printAboveLine(String message) {
        if (lineReader != null && terminal != null && running) {
            try {
                // Use JLine's printAbove to properly handle the prompt
                lineReader.printAbove(message.trim());
            } catch (Exception e) {
                // Fallback: just print directly
                terminal.writer().print(message);
                terminal.writer().flush();
            }
        } else {
            // Terminal not ready, print directly to stdout
            System.out.print(message);
        }
    }
    
    /**
     * Check if the terminal is running.
     */
    public boolean isRunning() {
        return running && terminal != null;
    }
    
    /**
     * Get the JLine terminal instance.
     */
    public Terminal getTerminal() {
        return terminal;
    }
    
    /**
     * Get the command system.
     */
    public CommandSystem getCommandSystem() {
        return commandSystem;
    }

    public void stop() {
        running = false;
        
        if (lineReader != null) {
            try {
                // Try to interrupt the readline
                terminal.writer().println();
                terminal.writer().flush();
            } catch (Exception e) {
                // Ignore
            }
        }
        
        cleanup();
    }
    
    private void cleanup() {
        if (terminal != null) {
            try {
                terminal.close();
            } catch (IOException e) {
                // Ignore close errors
            }
            terminal = null;
        }
        lineReader = null;
    }
}