package me.hash.mediaroulette.utils.terminal.commands;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.utils.terminal.Command;
import me.hash.mediaroulette.utils.terminal.CommandResult;

import java.util.List;

public class StatusCommand extends Command {

    public StatusCommand() {
        super("status", "Show application status", "status", List.of("stat"));
    }

    @Override
    public CommandResult execute(String[] args) {
        StringBuilder status = new StringBuilder();
        status.append("Application Status:\n");
        status.append("  Uptime: ").append(getUptime()).append("\n");
        status.append("  Bot Status: ").append(Main.getBot() != null ? "Running" : "Not Running").append("\n");
        status.append("  Database: ").append(Main.getDatabase() != null ? "Connected" : "Disconnected").append("\n");

        return CommandResult.success(status.toString());
    }

    private String getUptime() {
        long uptime = System.currentTimeMillis() - Main.START_TIME;
        long seconds = uptime / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        return String.format("%02d:%02d:%02d", hours % 24, minutes % 60, seconds % 60);
    }
}