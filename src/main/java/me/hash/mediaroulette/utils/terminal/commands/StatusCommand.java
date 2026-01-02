package me.hash.mediaroulette.utils.terminal.commands;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.utils.terminal.Command;
import me.hash.mediaroulette.utils.terminal.CommandResult;

import java.util.List;

import static me.hash.mediaroulette.utils.terminal.TerminalColors.*;

public class StatusCommand extends Command {

    public StatusCommand() {
        super("status", "Show application status", "status", List.of("stat"));
    }

    @Override
    public CommandResult execute(String[] args) {
        boolean botRunning = Main.getBot() != null;
        boolean dbConnected = Main.getDatabase() != null;
        
        StringBuilder status = new StringBuilder();
        status.append(header("Application Status")).append("\n");
        status.append(dim("─".repeat(40))).append("\n\n");
        
        status.append("  ").append(bold("Uptime:")).append("      ").append(cyan(getUptime())).append("\n");
        status.append("  ").append(bold("Bot:")).append("         ").append(botRunning ? green("Running") : red("Not Running")).append("\n");
        status.append("  ").append(bold("Database:")).append("    ").append(dbConnected ? green("Connected") : red("Disconnected")).append("\n");

        return CommandResult.success(status.toString());
    }

    private String getUptime() {
        long uptime = System.currentTimeMillis() - Main.START_TIME;
        long seconds = uptime / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format("%dd %02dh %02dm %02ds", days, hours % 24, minutes % 60, seconds % 60);
        }
        return String.format("%02d:%02d:%02d", hours % 24, minutes % 60, seconds % 60);
    }
    
    @Override
    public String getDetailedHelp() {
        StringBuilder help = new StringBuilder();
        help.append(header("Command: ")).append(command("status")).append("\n");
        help.append("Display the current status of the application.\n\n");
        
        help.append(header("Information Shown:")).append("\n");
        help.append("  • ").append(bold("Uptime")).append(" - How long the application has been running\n");
        help.append("  • ").append(bold("Bot")).append(" - Discord bot connection status\n");
        help.append("  • ").append(bold("Database")).append(" - MongoDB connection status\n\n");
        
        help.append(header("Usage:")).append("\n");
        help.append("  ").append(cyan("status")).append("\n\n");
        
        help.append(header("Aliases:")).append("\n");
        help.append("  stat\n");
        
        return help.toString();
    }
}
