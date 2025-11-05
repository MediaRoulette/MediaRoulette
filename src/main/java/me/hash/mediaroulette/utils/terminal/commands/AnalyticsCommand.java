package me.hash.mediaroulette.utils.terminal.commands;

import me.hash.mediaroulette.utils.StatsAnalyzer;
import me.hash.mediaroulette.utils.terminal.Command;
import me.hash.mediaroulette.utils.terminal.CommandResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AnalyticsCommand extends Command {
    private static final Logger logger = LoggerFactory.getLogger(AnalyticsCommand.class);
    private static final String USAGE = "analytics <generate|clean|list|open|status|check> [args...]";

    public AnalyticsCommand() {
        super("analytics", "Generate and manage analytics reports", USAGE, List.of("stats", "reports"));
    }

    @Override
    public CommandResult execute(String[] args) {
        if (args.length == 0) {
            return CommandResult.error("Usage: " + getUsage() + "\n" + getHelpText());
        }

        String action = args[0].toLowerCase(Locale.ROOT);

        try {
            switch (action) {
                case "generate":
                    return handleGenerate(args);
                case "clean":
                    return handleClean(args);
                case "list":
                    return handleList(args);
                case "open":
                    return handleOpen(args);
                case "status":
                    return handleStatus();
                case "check":
                    return handleCheck(args);
                case "help":
                    return CommandResult.success(getHelpText());
                default:
                    return CommandResult.error("Unknown action: " + action + "\nUse 'analytics help' for available commands");
            }
        } catch (Exception e) {
            logger.error("Error executing analytics command", e);
            return CommandResult.error("Command failed: " + e.getMessage());
        }
    }

    private CommandResult handleGenerate(String[] args) {
        if (args.length < 2) {
            return CommandResult.error("Usage: analytics generate <current|previous|month|multi> [options]\n" +
                    "Examples:\n" +
                    "  analytics generate current\n" +
                    "  analytics generate previous\n" +
                    "  analytics generate month 2024-01\n" +
                    "  analytics generate multi 2024-01 2024-02 2024-03");
        }

        String subAction = args[1].toLowerCase(Locale.ROOT);

        try {
            switch (subAction) {
                case "current":
                    StatsAnalyzer.generateCurrentMonthReport();
                    return CommandResult.success("Generated current month report and analytics index");

                case "previous":
                    StatsAnalyzer.generatePreviousMonthReport();
                    return CommandResult.success("Generated previous month report and analytics index");

                case "month":
                    if (args.length < 3) {
                        return CommandResult.error("Usage: analytics generate month <YYYY-MM>");
                    }
                    String month = validateMonth(args[2]);
                    if (month == null) {
                        return CommandResult.error("Invalid month format. Use YYYY-MM (e.g., 2024-01)");
                    }
                    StatsAnalyzer.generateMonthlyReport(month);
                    StatsAnalyzer.generateAnalyticsIndex();
                    return CommandResult.success("Generated report for " + month + " and updated analytics index");

                case "multi":
                    if (args.length < 3) {
                        return CommandResult.error("Usage: analytics generate multi <YYYY-MM> [YYYY-MM] ...");
                    }
                    List<String> months = new ArrayList<>();
                    for (int i = 2; i < args.length; i++) {
                        String validMonth = validateMonth(args[i]);
                        if (validMonth == null) {
                            return CommandResult.error("Invalid month format: " + args[i] + ". Use YYYY-MM");
                        }
                        months.add(validMonth);
                    }
                    StatsAnalyzer.generateMultiMonthReport(months.toArray(new String[0]));
                    return CommandResult.success("Generated reports for " + months.size() + " months: " + String.join(", ", months));

                default:
                    return CommandResult.error("Unknown generate option: " + subAction);
            }
        } catch (Exception e) {
            logger.error("Error generating analytics report", e);
            return CommandResult.error("Report generation failed: " + e.getMessage());
        }
    }

    private CommandResult handleClean(String[] args) {
        try {
            if (args.length > 1 && args[1].equalsIgnoreCase("--confirm")) {
                StatsAnalyzer.cleanOldReports();
                return CommandResult.success("Cleaned old reports (kept last 6 months)");
            } else {
                return CommandResult.success("This will delete reports older than 6 months.\n" +
                        "Use 'analytics clean --confirm' to proceed.");
            }
        } catch (Exception e) {
            logger.error("Error cleaning old reports", e);
            return CommandResult.error("Failed to clean reports: " + e.getMessage());
        }
    }

    private CommandResult handleList(String[] args) {
        try {
            Path reportsDir = Paths.get("reports");
            if (!Files.exists(reportsDir)) {
                return CommandResult.success("No reports directory found. Generate reports first.");
            }

            boolean detailed = args.length > 1 && args[1].equalsIgnoreCase("--detailed");

            List<Path> dashboards = Files.list(reportsDir)
                    .filter(path -> path.getFileName().toString().startsWith("dashboard_"))
                    .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                    .collect(Collectors.toList());

            if (dashboards.isEmpty()) {
                return CommandResult.success("No dashboard reports found. Use 'analytics generate' to create reports.");
            }

            StringBuilder result = new StringBuilder();
            result.append("Available Analytics Reports (").append(dashboards.size()).append("):\n");

            for (Path dashboard : dashboards) {
                String fileName = dashboard.getFileName().toString();
                String month = fileName.replace("dashboard_", "").replace(".html", "");

                if (detailed) {
                    try {
                        long fileSize = Files.size(dashboard);
                        String lastModified = Files.getLastModifiedTime(dashboard)
                                .toString().substring(0, 19).replace("T", " ");
                        result.append(String.format("  %s - %s KB (modified: %s)\n",
                                month, fileSize / 1024, lastModified));
                    } catch (IOException e) {
                        result.append("  ").append(month).append(" - (error reading file info)\n");
                    }
                } else {
                    result.append("  ").append(month).append("\n");
                }
            }

            if (!detailed) {
                result.append("\nUse 'analytics list --detailed' for more information");
            }
            result.append("\nUse 'analytics open <month>' to view a specific report");

            return CommandResult.success(result.toString().trim());

        } catch (Exception e) {
            logger.error("Error listing reports", e);
            return CommandResult.error("Failed to list reports: " + e.getMessage());
        }
    }

    private CommandResult handleOpen(String[] args) {
        if (args.length < 2) {
            return CommandResult.error("Usage: analytics open <month|index>\n" +
                    "Examples:\n" +
                    "  analytics open 2024-01\n" +
                    "  analytics open index");
        }

        String target = args[1].toLowerCase();
        Path reportsDir = Paths.get("reports");

        try {
            if (target.equals("index")) {
                Path indexFile = reportsDir.resolve("index.html");
                if (!Files.exists(indexFile)) {
                    StatsAnalyzer.generateAnalyticsIndex();
                }
                return CommandResult.success("Analytics index location: " + indexFile.toAbsolutePath());
            } else {
                String month = validateMonth(target);
                if (month == null) {
                    return CommandResult.error("Invalid month format. Use YYYY-MM or 'index'");
                }

                Path dashboardFile = reportsDir.resolve("dashboard_" + month + ".html");
                if (!Files.exists(dashboardFile)) {
                    return CommandResult.error("Report not found for " + month + ". Use 'analytics generate month " + month + "' first.");
                }

                return CommandResult.success("Report location: " + dashboardFile.toAbsolutePath());
            }
        } catch (Exception e) {
            logger.error("Error opening report", e);
            return CommandResult.error("Failed to open report: " + e.getMessage());
        }
    }

    private CommandResult handleStatus() {
        try {
            StringBuilder status = new StringBuilder();
            status.append("Analytics System Status:\n");

            // Check directories
            Path statsDir = Paths.get("stats");
            Path reportsDir = Paths.get("reports");
            Path chartsDir = Paths.get("reports/charts");

            status.append("  Stats Directory: ").append(Files.exists(statsDir) ? "EXISTS" : "MISSING").append("\n");
            status.append("  Reports Directory: ").append(Files.exists(reportsDir) ? "EXISTS" : "MISSING").append("\n");
            status.append("  Charts Directory: ").append(Files.exists(chartsDir) ? "EXISTS" : "MISSING").append("\n");

            // Count available data files
            if (Files.exists(statsDir)) {
                long generalStats = Files.list(statsDir)
                        .filter(path -> path.getFileName().toString().startsWith("general_stats_"))
                        .count();
                long sourceStats = Files.list(statsDir)
                        .filter(path -> path.getFileName().toString().startsWith("source_usage_"))
                        .count();
                long commandStats = Files.list(statsDir)
                        .filter(path -> path.getFileName().toString().startsWith("command_usage_"))
                        .count();

                status.append("  Available Data Files:\n");
                status.append("    General Stats: ").append(generalStats).append(" months\n");
                status.append("    Source Usage: ").append(sourceStats).append(" months\n");
                status.append("    Command Usage: ").append(commandStats).append(" months\n");
            }

            // Count generated reports
            if (Files.exists(reportsDir)) {
                long dashboards = Files.list(reportsDir)
                        .filter(path -> path.getFileName().toString().startsWith("dashboard_"))
                        .count();
                boolean hasIndex = Files.exists(reportsDir.resolve("index.html"));

                status.append("  Generated Reports:\n");
                status.append("    Dashboards: ").append(dashboards).append("\n");
                status.append("    Index Page: ").append(hasIndex ? "EXISTS" : "MISSING").append("\n");
            }

            // Current time info
            String currentMonth = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            String previousMonth = LocalDateTime.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));

            status.append("  Time Info:\n");
            status.append("    Current Month: ").append(currentMonth).append("\n");
            status.append("    Previous Month: ").append(previousMonth).append("\n");

            return CommandResult.success(status.toString().trim());

        } catch (Exception e) {
            logger.error("Error checking analytics status", e);
            return CommandResult.error("Failed to get status: " + e.getMessage());
        }
    }

    private CommandResult handleCheck(String[] args) {
        if (args.length < 2) {
            return CommandResult.error("Usage: analytics check <month>\n" +
                    "Example: analytics check 2024-01");
        }

        String month = validateMonth(args[1]);
        if (month == null) {
            return CommandResult.error("Invalid month format. Use YYYY-MM");
        }

        try {
            StringBuilder result = new StringBuilder();
            result.append("Data availability check for ").append(month).append(":\n");

            Path statsDir = Paths.get("stats");
            boolean hasGeneral = Files.exists(statsDir.resolve("general_stats_" + month + ".csv"));
            boolean hasSource = Files.exists(statsDir.resolve("source_usage_" + month + ".csv"));
            boolean hasCommand = Files.exists(statsDir.resolve("command_usage_" + month + ".csv"));

            result.append("  General Stats: ").append(hasGeneral ? "AVAILABLE" : "MISSING").append("\n");
            result.append("  Source Usage: ").append(hasSource ? "AVAILABLE" : "MISSING").append("\n");
            result.append("  Command Usage: ").append(hasCommand ? "AVAILABLE" : "MISSING").append("\n");

            Path reportsDir = Paths.get("reports");
            boolean hasDashboard = Files.exists(reportsDir.resolve("dashboard_" + month + ".html"));
            boolean hasMonthlyReport = Files.exists(reportsDir.resolve("monthly_report_" + month + ".csv"));

            result.append("  Generated Reports:\n");
            result.append("    Dashboard: ").append(hasDashboard ? "EXISTS" : "NOT GENERATED").append("\n");
            result.append("    Monthly Report: ").append(hasMonthlyReport ? "EXISTS" : "NOT GENERATED").append("\n");

            boolean canGenerate = hasGeneral || hasSource || hasCommand;
            result.append("  Can Generate Report: ").append(canGenerate ? "YES" : "NO").append("\n");

            if (canGenerate && !hasDashboard) {
                result.append("\nUse 'analytics generate month ").append(month).append("' to create the report.");
            } else if (!canGenerate) {
                result.append("\nNo data files found for this month. Ensure data collection is running.");
            }

            return CommandResult.success(result.toString().trim());

        } catch (Exception e) {
            logger.error("Error checking month data", e);
            return CommandResult.error("Failed to check data: " + e.getMessage());
        }
    }

    private String validateMonth(String monthStr) {
        try {
            // Try to parse as YYYY-MM
            YearMonth.parse(monthStr, DateTimeFormatter.ofPattern("yyyy-MM"));
            return monthStr;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private String getHelpText() {
        return """
                Analytics Command Usage:
                
                GENERATION:
                  analytics generate current               - Generate report for current month
                  analytics generate previous             - Generate report for previous month
                  analytics generate month <YYYY-MM>      - Generate report for specific month
                  analytics generate multi <months...>    - Generate reports for multiple months
                
                MANAGEMENT:
                  analytics list [--detailed]            - List all available reports
                  analytics clean [--confirm]            - Clean old reports (6+ months)
                  analytics open <month|index>           - Get file path for report or index
                  analytics status                       - Show analytics system status
                  analytics check <YYYY-MM>              - Check data availability for month
                
                EXAMPLES:
                  analytics generate current
                  analytics generate month 2024-01
                  analytics generate multi 2024-01 2024-02 2024-03
                  analytics list --detailed
                  analytics open 2024-01
                  analytics open index
                  analytics check 2024-01
                  analytics clean --confirm
                """;
    }

    @Override
    public List<String> getCompletions(String[] args) {
        if (args.length == 1) {
            return Arrays.asList("generate", "clean", "list", "open", "status", "check", "help");
        }

        if (args.length == 2) {
            String action = args[0].toLowerCase(Locale.ROOT);
            switch (action) {
                case "generate":
                    return Arrays.asList("current", "previous", "month", "multi");
                case "list":
                    return Arrays.asList("--detailed");
                case "clean":
                    return Arrays.asList("--confirm");
                case "open":
                    List<String> completions = new ArrayList<>();
                    completions.add("index");
                    completions.addAll(getAvailableMonths());
                    return completions;
                case "check":
                    return getAvailableMonths();
            }
        }

        if (args.length >= 3) {
            String action = args[0].toLowerCase(Locale.ROOT);
            if ("generate".equals(action)) {
                String subAction = args[1].toLowerCase(Locale.ROOT);
                if ("month".equals(subAction) || "multi".equals(subAction)) {
                    return getMonthCompletions();
                }
            }
        }

        return List.of();
    }

    private List<String> getAvailableMonths() {
        try {
            Path statsDir = Paths.get("stats");
            if (!Files.exists(statsDir)) return List.of();

            return Files.list(statsDir)
                    .filter(path -> path.getFileName().toString().startsWith("general_stats_"))
                    .map(path -> path.getFileName().toString()
                            .replace("general_stats_", "")
                            .replace(".csv", ""))
                    .filter(month -> validateMonth(month) != null)
                    .sorted((a, b) -> b.compareTo(a)) // Most recent first
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> getMonthCompletions() {
        LocalDateTime now = LocalDateTime.now();
        return IntStream.rangeClosed(-12, 1) // Last 12 months + next month
                .mapToObj(i -> now.plusMonths(i).format(DateTimeFormatter.ofPattern("yyyy-MM")))
                .collect(Collectors.toList());
    }
}