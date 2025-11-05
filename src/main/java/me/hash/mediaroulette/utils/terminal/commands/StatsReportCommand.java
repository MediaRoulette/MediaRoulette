package me.hash.mediaroulette.utils.terminal.commands;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.utils.StatsAnalyzer;
import me.hash.mediaroulette.utils.terminal.Command;
import me.hash.mediaroulette.utils.terminal.CommandResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StatsReportCommand extends Command {

    public StatsReportCommand() {
        super("statsreport", "Generate and view stats reports", 
              "statsreport [current|hourly|monthly|generate] [YYYY-MM]", 
              List.of("report", "analytics"));
    }

    @Override
    public CommandResult execute(String[] args) {
        if (args.length == 0) {
            return showUsage();
        }

        String subCommand = args[0].toLowerCase();
        
        try {
            switch (subCommand) {
                case "current":
                    return showCurrentHourlyStats();
                    
                case "hourly":
                    return showHourlyStatsInfo();
                    
                case "monthly":
                    String month = args.length > 1 ? args[1] : getCurrentMonth();
                    return showMonthlyReportInfo(month);
                    
                case "generate":
                    String targetMonth = args.length > 1 ? args[1] : getCurrentMonth();
                    return generateMonthlyReport(targetMonth);
                    
                default:
                    return showUsage();
            }
        } catch (Exception e) {
            return CommandResult.error("Error executing stats report command: " + e.getMessage());
        }
    }

    private CommandResult showUsage() {
        StringBuilder usage = new StringBuilder();
        usage.append("=== STATS REPORT COMMAND USAGE ===\n");
        usage.append("statsreport current          - Show current hour statistics\n");
        usage.append("statsreport hourly           - Show info about hourly tracking\n");
        usage.append("statsreport monthly [YYYY-MM] - Show monthly report info\n");
        usage.append("statsreport generate [YYYY-MM] - Generate monthly reports\n");
        usage.append("\nExamples:\n");
        usage.append("  statsreport current\n");
        usage.append("  statsreport generate 2024-01\n");
        usage.append("  statsreport monthly 2024-01\n");
        usage.append("================================");
        
        return CommandResult.success(usage.toString());
    }

    private CommandResult showCurrentHourlyStats() {
        if (Main.getStatsService() == null) {
            return CommandResult.error("Stats service not initialized");
        }

        Map<String, Object> currentStats = Main.getStatsService().getCurrentHourlyStats();
        
        StringBuilder stats = new StringBuilder();
        stats.append("=== CURRENT HOUR STATISTICS ===\n");
        stats.append("Hour: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00"))).append("\n");
        stats.append("Images Generated: ").append(currentStats.get("images_generated")).append("\n");
        stats.append("Commands Used: ").append(currentStats.get("commands_used")).append("\n");
        stats.append("Active Users: ").append(currentStats.get("active_users")).append("\n");
        stats.append("Unique Users: ").append(currentStats.get("unique_users")).append("\n");
        stats.append("New Users: ").append(currentStats.get("new_users")).append("\n");
        stats.append("Coins Earned: ").append(currentStats.get("coins_earned")).append("\n");
        stats.append("Coins Spent: ").append(currentStats.get("coins_spent")).append("\n");
        stats.append("Quests Completed: ").append(currentStats.get("quests_completed")).append("\n");
        stats.append("NSFW Requests: ").append(currentStats.get("nsfw_requests")).append("\n");
        stats.append("SFW Requests: ").append(currentStats.get("sfw_requests")).append("\n");
        stats.append("Premium Activity: ").append(currentStats.get("premium_activity")).append("\n");
        stats.append("Regular Activity: ").append(currentStats.get("regular_activity")).append("\n");
        
        // Show top sources
        @SuppressWarnings("unchecked")
        Map<String, Object> sourceUsage = (Map<String, Object>) currentStats.get("source_usage");
        if (sourceUsage != null && !sourceUsage.isEmpty()) {
            stats.append("\nTop Sources This Hour:\n");
            sourceUsage.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(
                    ((Number) e2.getValue()).longValue(), 
                    ((Number) e1.getValue()).longValue()))
                .limit(5)
                .forEach(entry -> stats.append("  ")
                    .append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue())
                    .append("\n"));
        }
        
        // Show top commands
        @SuppressWarnings("unchecked")
        Map<String, Object> commandUsage = (Map<String, Object>) currentStats.get("command_usage");
        if (commandUsage != null && !commandUsage.isEmpty()) {
            stats.append("\nTop Commands This Hour:\n");
            commandUsage.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(
                    ((Number) e2.getValue()).longValue(), 
                    ((Number) e1.getValue()).longValue()))
                .limit(5)
                .forEach(entry -> stats.append("  ")
                    .append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue())
                    .append("\n"));
        }
        
        stats.append("===============================");
        
        return CommandResult.success(stats.toString());
    }

    private CommandResult showHourlyStatsInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== HOURLY STATS TRACKING INFO ===\n");
        info.append("Stats are automatically logged every hour to CSV files in the 'stats' directory.\n\n");
        info.append("Generated Files:\n");
        info.append("• general_stats_YYYY-MM.csv     - Main statistics (images, users, commands, etc.)\n");
        info.append("• source_usage_YYYY-MM.csv      - Usage count per media source (reddit, imgur, etc.)\n");
        info.append("• command_usage_YYYY-MM.csv     - Usage count per bot command\n");
        info.append("• theme_usage_YYYY-MM.csv       - Usage count per theme\n\n");
        info.append("Data tracked per hour:\n");
        info.append("• Images generated, commands used, active users\n");
        info.append("• New user registrations, coins earned/spent\n");
        info.append("• NSFW vs SFW requests, premium vs regular activity\n");
        info.append("• Source popularity, command popularity, theme usage\n\n");
        info.append("Use 'statsreport current' to see current hour statistics.\n");
        info.append("Use 'statsreport generate YYYY-MM' to create monthly reports.\n");
        info.append("==================================");
        
        return CommandResult.success(info.toString());
    }

    private CommandResult showMonthlyReportInfo(String month) {
        StringBuilder info = new StringBuilder();
        info.append("=== MONTHLY REPORT INFO ===\n");
        info.append("Month: ").append(month).append("\n\n");
        info.append("Monthly reports are generated from hourly CSV data and saved to the 'reports' directory.\n\n");
        info.append("Generated Report Files:\n");
        info.append("• monthly_report_").append(month).append(".csv          - Overall monthly summary\n");
        info.append("• source_usage_report_").append(month).append(".csv     - Source popularity ranking\n");
        info.append("• command_usage_report_").append(month).append(".csv    - Command popularity ranking\n");
        info.append("• peak_hours_report_").append(month).append(".csv      - Activity patterns by hour of day\n");
        info.append("• user_growth_report_").append(month).append(".csv     - Daily user growth statistics\n\n");
        info.append("To generate reports for this month, use:\n");
        info.append("  statsreport generate ").append(month).append("\n");
        info.append("===========================");
        
        return CommandResult.success(info.toString());
    }

    private CommandResult generateMonthlyReport(String month) {
        try {
            StatsAnalyzer.generateMonthlyReport(month);
            
            StringBuilder result = new StringBuilder();
            result.append("=== MONTHLY REPORT GENERATION ===\n");
            result.append("Successfully generated monthly reports for: ").append(month).append("\n\n");
            result.append("Reports saved to 'reports' directory:\n");
            result.append("• monthly_report_").append(month).append(".csv\n");
            result.append("• source_usage_report_").append(month).append(".csv\n");
            result.append("• command_usage_report_").append(month).append(".csv\n");
            result.append("• peak_hours_report_").append(month).append(".csv\n");
            result.append("• user_growth_report_").append(month).append(".csv\n\n");
            result.append("You can now analyze these CSV files with spreadsheet software\n");
            result.append("or data analysis tools for detailed insights.\n");
            result.append("=================================");
            
            return CommandResult.success(result.toString());
            
        } catch (Exception e) {
            return CommandResult.error("Failed to generate monthly report: " + e.getMessage());
        }
    }

    private String getCurrentMonth() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    @Override
    public List<String> getCompletions(String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length <= 1) {
            completions.addAll(List.of("current", "hourly", "monthly", "generate"));
        } else if (args.length == 2 && (args[0].equals("monthly") || args[0].equals("generate"))) {
            // Suggest current and previous months
            String currentMonth = getCurrentMonth();
            String previousMonth = LocalDateTime.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
            completions.add(currentMonth);
            completions.add(previousMonth);
        }
        
        return completions;
    }
}