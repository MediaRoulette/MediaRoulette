package me.hash.mediaroulette.utils.terminal;

/**
 * ANSI color utility for terminal output.
 * Provides consistent color formatting across the terminal interface.
 */
public final class TerminalColors {
    
    // Reset
    public static final String RESET = "\u001B[0m";
    
    // Regular Colors
    public static final String BLACK = "\u001B[30m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";
    
    // Bold/Bright Colors
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";
    public static final String ITALIC = "\u001B[3m";
    public static final String UNDERLINE = "\u001B[4m";
    
    // Bright Colors
    public static final String BRIGHT_BLACK = "\u001B[90m";
    public static final String BRIGHT_RED = "\u001B[91m";
    public static final String BRIGHT_GREEN = "\u001B[92m";
    public static final String BRIGHT_YELLOW = "\u001B[93m";
    public static final String BRIGHT_BLUE = "\u001B[94m";
    public static final String BRIGHT_PURPLE = "\u001B[95m";
    public static final String BRIGHT_CYAN = "\u001B[96m";
    public static final String BRIGHT_WHITE = "\u001B[97m";
    
    // Background Colors
    public static final String BG_BLACK = "\u001B[40m";
    public static final String BG_RED = "\u001B[41m";
    public static final String BG_GREEN = "\u001B[42m";
    public static final String BG_YELLOW = "\u001B[43m";
    public static final String BG_BLUE = "\u001B[44m";
    public static final String BG_PURPLE = "\u001B[45m";
    public static final String BG_CYAN = "\u001B[46m";
    public static final String BG_WHITE = "\u001B[47m";
    
    private TerminalColors() {
        // Utility class
    }
    
    /**
     * Wraps text with a color and auto-resets.
     */
    public static String color(String text, String color) {
        return color + text + RESET;
    }
    
    /**
     * Makes text bold.
     */
    public static String bold(String text) {
        return BOLD + text + RESET;
    }
    
    /**
     * Makes text dim.
     */
    public static String dim(String text) {
        return DIM + text + RESET;
    }
    
    /**
     * Colors text red (for errors).
     */
    public static String red(String text) {
        return RED + text + RESET;
    }
    
    /**
     * Colors text green (for success).
     */
    public static String green(String text) {
        return GREEN + text + RESET;
    }
    
    /**
     * Colors text yellow (for warnings).
     */
    public static String yellow(String text) {
        return YELLOW + text + RESET;
    }
    
    /**
     * Colors text cyan (for info/highlights).
     */
    public static String cyan(String text) {
        return CYAN + text + RESET;
    }
    
    /**
     * Colors text blue.
     */
    public static String blue(String text) {
        return BLUE + text + RESET;
    }
    
    /**
     * Colors text purple.
     */
    public static String purple(String text) {
        return PURPLE + text + RESET;
    }
    
    /**
     * Formats a command name for display.
     */
    public static String command(String text) {
        return BRIGHT_CYAN + BOLD + text + RESET;
    }
    
    /**
     * Formats a parameter/argument for display.
     */
    public static String param(String text) {
        return YELLOW + text + RESET;
    }
    
    /**
     * Formats a section header.
     */
    public static String header(String text) {
        return BOLD + BRIGHT_WHITE + text + RESET;
    }
    
    /**
     * Formats a success message.
     */
    public static String success(String text) {
        return BRIGHT_GREEN + "✓ " + text + RESET;
    }
    
    /**
     * Formats an error message.
     */
    public static String error(String text) {
        return BRIGHT_RED + "✗ " + text + RESET;
    }
    
    /**
     * Formats a warning message.
     */
    public static String warning(String text) {
        return BRIGHT_YELLOW + "⚠ " + text + RESET;
    }
    
    /**
     * Formats an info message.
     */
    public static String info(String text) {
        return BRIGHT_BLUE + "ℹ " + text + RESET;
    }
}
