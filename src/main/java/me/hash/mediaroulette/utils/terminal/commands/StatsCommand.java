package me.hash.mediaroulette.utils.terminal.commands;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.utils.terminal.Command;
import me.hash.mediaroulette.utils.terminal.CommandResult;

import java.util.ArrayList;
import java.util.List;

public class StatsCommand extends Command {

    public StatsCommand() {
        super("stats", "Show database statistics", "stats", List.of("statistics", "db"));
    }

    @Override
    public CommandResult execute(String[] args) {
        try {
            long totalUsers = Main.getUserService().getTotalUsers();
            long totalImages = Main.getUserService().getTotalImagesGenerated();
            
            StringBuilder stats = new StringBuilder();
            stats.append("=== DATABASE STATISTICS ===\n");
            stats.append("Total Users: ").append(String.format("%,d", totalUsers)).append("\n");
            stats.append("Total Images Generated: ").append(String.format("%,d", totalImages)).append("\n");
            
            if (totalUsers > 0) {
                double avgImagesPerUser = (double) totalImages / totalUsers;
                stats.append("Average Images per User: ").append(String.format("%.2f", avgImagesPerUser)).append("\n");
            }
            
            stats.append("===========================");
            
            return CommandResult.success(stats.toString());
        } catch (Exception e) {
            return CommandResult.error("Failed to get database statistics: " + e.getMessage());
        }
    }

    @Override
    public List<String> getCompletions(String[] args) {
        return new ArrayList<>(); // No completions needed for this command
    }
}