package me.hash.mediaroulette.utils.terminal.commands;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.utils.terminal.Command;
import me.hash.mediaroulette.utils.terminal.CommandResult;

import java.util.List;

import static me.hash.mediaroulette.utils.terminal.TerminalColors.*;

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
            stats.append(header("Database Statistics")).append("\n");
            stats.append(dim("─".repeat(40))).append("\n\n");
            
            stats.append("  ").append(bold("Total Users:")).append("         ");
            stats.append(cyan(String.format("%,d", totalUsers))).append("\n");
            
            stats.append("  ").append(bold("Images Generated:")).append("    ");
            stats.append(cyan(String.format("%,d", totalImages))).append("\n");
            
            if (totalUsers > 0) {
                double avgImagesPerUser = (double) totalImages / totalUsers;
                stats.append("  ").append(bold("Avg per User:")).append("        ");
                stats.append(cyan(String.format("%.2f", avgImagesPerUser))).append("\n");
            }
            
            return CommandResult.success(stats.toString());
        } catch (Exception e) {
            return CommandResult.error("Failed to get database statistics: " + e.getMessage());
        }
    }

    @Override
    public List<String> getCompletions(String[] args) {
        return List.of(); // No completions needed for this command
    }

    @Override
    public String getDetailedHelp() {
        StringBuilder help = new StringBuilder();
        
        help.append(header("Command: ")).append(command("stats")).append("\n");
        help.append("Display database statistics and usage metrics.\n\n");
        
        help.append(header("Information Shown:")).append("\n");
        help.append("  ").append(cyan("•")).append(" ").append(bold("Total Users")).append(" - Number of registered users\n");
        help.append("  ").append(cyan("•")).append(" ").append(bold("Images Generated")).append(" - Total images created\n");
        help.append("  ").append(cyan("•")).append(" ").append(bold("Avg per User")).append(" - Average images per user\n\n");
        
        help.append(header("Usage:")).append("\n");
        help.append("  ").append(cyan("stats")).append("\n\n");
        
        help.append(header("Aliases:")).append("\n");
        help.append("  statistics, db\n");
        
        return help.toString();
    }
}