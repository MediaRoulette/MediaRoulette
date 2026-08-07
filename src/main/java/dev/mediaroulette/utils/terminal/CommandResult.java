package dev.mediaroulette.utils.terminal;

public class CommandResult {
    private final boolean success;
    private final String message;

    public CommandResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }

    public static CommandResult success(String message) {
        return new CommandResult(true, message);
    }

    public static CommandResult error(String message) {
        return new CommandResult(false, message);
    }
}