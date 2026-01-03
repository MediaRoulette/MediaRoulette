package me.hash.mediaroulette.utils.startup;

import me.hash.mediaroulette.utils.terminal.ProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static me.hash.mediaroulette.utils.terminal.TerminalColors.*;

/**
 * Orchestrates application startup with clean, consolidated logging.
 * Replaces verbose individual logger.info() calls with progress-based display.
 */
public class StartupManager {
    private static final Logger logger = LoggerFactory.getLogger(StartupManager.class);
    
    private final List<StartupTask> tasks = new ArrayList<>();
    private final Map<String, StartupResult> results = new LinkedHashMap<>();
    private long startTime;
    
    public StartupManager() {
    }
    
    /**
     * Add a startup task.
     */
    public StartupManager addTask(String name, Runnable action) {
        tasks.add(new StartupTask(name, () -> {
            action.run();
            return null;
        }));
        return this;
    }
    
    /**
     * Add a startup task that returns a result message.
     */
    public StartupManager addTask(String name, Supplier<String> action) {
        tasks.add(new StartupTask(name, action));
        return this;
    }
    
    /**
     * Execute all startup tasks with progress display.
     */
    public void execute() {
        printBanner();
        startTime = System.currentTimeMillis();
        
        for (StartupTask task : tasks) {
            executeTask(task);
        }
        
        printSummary();
    }
    
    private void executeTask(StartupTask task) {
        try (ProgressBar progress = ProgressBar.create(task.name())
                .withStyle(ProgressBar.Style.MINIMAL)
                .start()) {
            
            String resultMessage = task.action().get();
            results.put(task.name(), StartupResult.ok(resultMessage));
            progress.complete(resultMessage);
            
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg == null || errorMsg.isBlank()) {
                errorMsg = e.getClass().getSimpleName();
            }
            results.put(task.name(), StartupResult.fail(errorMsg));
            logger.error("{} failed: {}", task.name(), errorMsg, e);
            
            // Print failure line
            System.out.println("\r" + BRIGHT_RED + "✗ " + RESET + BOLD + task.name() + 
                    RESET + RED + " — " + errorMsg + RESET + "\u001B[K");
        }
    }
    
    private void printBanner() {
        System.out.println();
        System.out.println(BRIGHT_PURPLE + "  ╔═══════════════════════════════════════════╗" + RESET);
        System.out.println(BRIGHT_PURPLE + "  ║" + RESET + "       " + BOLD + BRIGHT_CYAN + "🎲 MediaRoulette" + RESET + 
                DIM + " v1.0.1" + RESET + "         " + BRIGHT_PURPLE + "║" + RESET);
        System.out.println(BRIGHT_PURPLE + "  ╚═══════════════════════════════════════════╝" + RESET);
        System.out.println();
    }
    
    private void printSummary() {
        long elapsed = System.currentTimeMillis() - startTime;
        
        System.out.println();
        System.out.println(BRIGHT_BLACK + "  ┌─────────────────────────────────────────┐" + RESET);
        System.out.println(BRIGHT_BLACK + "  │" + RESET + "           " + BOLD + "Startup Summary" + RESET + 
                "              " + BRIGHT_BLACK + "│" + RESET);
        System.out.println(BRIGHT_BLACK + "  ├─────────────────────────────────────────┤" + RESET);
        
        for (var entry : results.entrySet()) {
            String name = entry.getKey();
            StartupResult result = entry.getValue();
            
            String status = result.isSuccess() ? BRIGHT_GREEN + "✓" + RESET : BRIGHT_RED + "✗" + RESET;
            String color = result.isSuccess() ? "" : RED;
            String message = result.message() != null ? DIM + " " + result.message() + RESET : "";
            
            // Pad name to align
            String paddedName = String.format("%-16s", name);
            
            System.out.printf(BRIGHT_BLACK + "  │" + RESET + "  %s %s%s%s%s" + 
                    BRIGHT_BLACK + " │" + RESET + "%n", 
                    status, color, paddedName, RESET, truncate(message, 18));
        }
        
        System.out.println(BRIGHT_BLACK + "  ├─────────────────────────────────────────┤" + RESET);
        System.out.printf(BRIGHT_BLACK + "  │" + RESET + "  " + DIM + "Started in " + RESET + 
                BRIGHT_GREEN + "%.2fs" + RESET + "                       " + 
                BRIGHT_BLACK + "│" + RESET + "%n", elapsed / 1000.0);
        System.out.println(BRIGHT_BLACK + "  └─────────────────────────────────────────┘" + RESET);
        System.out.println();
    }
    
    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        // Strip ANSI codes for length calculation
        String stripped = str.replaceAll("\u001B\\[[;\\d]*m", "");
        if (stripped.length() <= maxLen) return str;
        return str.substring(0, maxLen - 3) + "...";
    }
    
    /**
     * Check if all tasks succeeded.
     */
    public boolean allSucceeded() {
        return results.values().stream().allMatch(r -> r.isSuccess());
    }
    
    /**
     * Get the result for a specific task.
     */
    public StartupResult getResult(String taskName) {
        return results.get(taskName);
    }
    
    // Inner classes
    
    public record StartupTask(String name, Supplier<String> action) {
    }
    
    public record StartupResult(boolean isSuccess, String message) {
        public static StartupResult ok() {
            return new StartupResult(true, null);
        }
        
        public static StartupResult ok(String message) {
            return new StartupResult(true, message);
        }
        
        public static StartupResult fail(String message) {
            return new StartupResult(false, message);
        }
    }
}
