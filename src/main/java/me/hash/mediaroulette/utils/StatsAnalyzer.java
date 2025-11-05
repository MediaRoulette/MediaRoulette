package me.hash.mediaroulette.utils;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class StatsAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(StatsAnalyzer.class);

    private static final String STATS_DIR = "stats";
    private static final String REPORTS_DIR = "reports";
    private static final String CHARTS_DIR = "reports/charts";

    public static class HourlyStats {
        public String hour;
        public long imagesGenerated;
        public long commandsUsed;
        public long activeUsers;
        public long uniqueUsers;
        public long newUsers;
        public long coinsEarned;
        public long coinsSpent;
        public long questsCompleted;
        public long nsfwRequests;
        public long sfwRequests;
        public long premiumActivity;
        public long regularActivity;
        public long totalUsersInDb;
        public long totalImagesInDb;

        public HourlyStats(String[] csvRow) {
            if (csvRow.length >= 15) {
                this.hour = csvRow[0];
                this.imagesGenerated = parseLong(csvRow[1]);
                this.commandsUsed = parseLong(csvRow[2]);
                this.activeUsers = parseLong(csvRow[3]);
                this.uniqueUsers = parseLong(csvRow[4]);
                this.newUsers = parseLong(csvRow[5]);
                this.coinsEarned = parseLong(csvRow[6]);
                this.coinsSpent = parseLong(csvRow[7]);
                this.questsCompleted = parseLong(csvRow[8]);
                this.nsfwRequests = parseLong(csvRow[9]);
                this.sfwRequests = parseLong(csvRow[10]);
                this.premiumActivity = parseLong(csvRow[11]);
                this.regularActivity = parseLong(csvRow[12]);
                this.totalUsersInDb = parseLong(csvRow[13]);
                this.totalImagesInDb = parseLong(csvRow[14]);
            }
        }

        private long parseLong(String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    public static class SourceUsageStats {
        public String hour;
        public String source;
        public long usageCount;

        public SourceUsageStats(String[] csvRow) {
            if (csvRow.length >= 3) {
                this.hour = csvRow[0];
                this.source = csvRow[1];
                this.usageCount = parseLong(csvRow[2]);
            }
        }

        private long parseLong(String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    public static class CommandUsageStats {
        public String hour;
        public String command;
        public long usageCount;

        public CommandUsageStats(String[] csvRow) {
            if (csvRow.length >= 3) {
                this.hour = csvRow[0];
                this.command = csvRow[1];
                this.usageCount = parseLong(csvRow[2]);
            }
        }

        private long parseLong(String value) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    /**
     * Generate comprehensive monthly report with visual charts
     */
    public static void generateMonthlyReport(String yearMonth) throws IOException, CsvValidationException {
        // Create reports directories if they don't exist
        Files.createDirectories(Paths.get(REPORTS_DIR));
        Files.createDirectories(Paths.get(CHARTS_DIR));

        // Generate different types of reports
        generateGeneralMonthlyReport(yearMonth);
        generateSourceUsageReport(yearMonth);
        generateCommandUsageReport(yearMonth);
        generatePeakHoursReport(yearMonth);
        generateUserGrowthReport(yearMonth);

        // Generate the main HTML dashboard
        generateHtmlDashboard(yearMonth);

        logger.info("Generated enhanced monthly report with charts for: {}", yearMonth);
    }

    /**
     * Generate main HTML dashboard with all charts and statistics
     */
    private static void generateHtmlDashboard(String yearMonth) throws IOException {
        Path dashboardFile = Paths.get(REPORTS_DIR, "dashboard_" + yearMonth + ".html");

        String html = getHtmlHeader(yearMonth) +
                getOverviewSection(yearMonth) +
                getChartsSection(yearMonth) +
                getTablesSection(yearMonth) +
                getHtmlFooter();

        Files.write(dashboardFile, html.getBytes());
        logger.info("Generated HTML dashboard: {}", dashboardFile);
    }

    private static String getHtmlHeader(String yearMonth) {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Media Roulette Analytics Dashboard -\s""" + yearMonth + """
</title>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/Chart.js/3.9.1/chart.min.js"></script>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
       \s
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            color: #333;
        }
       \s
        .container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
        }
       \s
        .header {
            background: rgba(255, 255, 255, 0.95);
            border-radius: 15px;
            padding: 30px;
            margin-bottom: 30px;
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
            text-align: center;
        }
       \s
        .header h1 {
            color: #2c3e50;
            font-size: 2.5em;
            margin-bottom: 10px;
            font-weight: 300;
        }
       \s
        .header .subtitle {
            color: #7f8c8d;
            font-size: 1.2em;
        }
       \s
        .stats-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 20px;
            margin-bottom: 40px;
        }
       \s
        .stat-card {
            background: rgba(255, 255, 255, 0.95);
            border-radius: 15px;
            padding: 25px;
            text-align: center;
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
            transition: transform 0.3s ease, box-shadow 0.3s ease;
        }
       \s
        .stat-card:hover {
            transform: translateY(-5px);
            box-shadow: 0 15px 40px rgba(0, 0, 0, 0.15);
        }
       \s
        .stat-value {
            font-size: 2.5em;
            font-weight: bold;
            margin-bottom: 10px;
        }
       \s
        .stat-label {
            color: #7f8c8d;
            font-size: 1.1em;
            text-transform: uppercase;
            letter-spacing: 1px;
        }
       \s
        .charts-section {
            margin-bottom: 40px;
        }
       \s
        .chart-container {
            background: rgba(255, 255, 255, 0.95);
            border-radius: 15px;
            padding: 30px;
            margin-bottom: 30px;
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
        }
       \s
        .chart-title {
            font-size: 1.5em;
            margin-bottom: 20px;
            color: #2c3e50;
            text-align: center;
            font-weight: 300;
        }
       \s
        .chart-wrapper {
            position: relative;
            height: 400px;
        }
       \s
        .charts-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(500px, 1fr));
            gap: 30px;
        }
       \s
        .table-container {
            background: rgba(255, 255, 255, 0.95);
            border-radius: 15px;
            padding: 30px;
            margin-bottom: 30px;
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
            overflow-x: auto;
        }
       \s
        table {
            width: 100%;
            border-collapse: collapse;
            margin-top: 20px;
        }
       \s
        th, td {
            padding: 12px 15px;
            text-align: left;
            border-bottom: 1px solid #ecf0f1;
        }
       \s
        th {
            background-color: #3498db;
            color: white;
            font-weight: 500;
            text-transform: uppercase;
            letter-spacing: 1px;
        }
       \s
        tr:nth-child(even) {
            background-color: #f8f9fa;
        }
       \s
        tr:hover {
            background-color: #e8f4fd;
        }
       \s
        .footer {
            text-align: center;
            padding: 30px;
            color: rgba(255, 255, 255, 0.8);
            font-size: 0.9em;
        }
       \s
        .color-primary { color: #3498db; }
        .color-success { color: #27ae60; }
        .color-warning { color: #f39c12; }
        .color-danger { color: #e74c3c; }
        .color-info { color: #9b59b6; }
       \s
        @media (max-width: 768px) {
            .container { padding: 10px; }
            .header h1 { font-size: 2em; }
            .charts-grid { grid-template-columns: 1fr; }
            .chart-wrapper { height: 300px; }
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>📊 Media Roulette Analytics</h1>
            <div class="subtitle">Monthly Report for\s""" + yearMonth + """
            </div>
        </div>
""";
    }


    private static String getOverviewSection(String yearMonth) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"stats-grid\">\n");

        // Read summary statistics from the general report
        Path reportFile = Paths.get(REPORTS_DIR, "monthly_report_" + yearMonth + ".csv");
        if (Files.exists(reportFile)) {
            Map<String, String> stats = new HashMap<>();

            try (CSVReader reader = new CSVReader(new FileReader(reportFile.toFile()))) {
                String[] row;
                while ((row = reader.readNext()) != null) {
                    if (row.length >= 2) {
                        stats.put(row[0], row[1]);
                    }
                }
            } catch (CsvValidationException e) {
                logger.error("Error reading report file", e);
            }

            html.append(createStatCard("Total Images", stats.getOrDefault("Total Images Generated", "0"), "color-primary", "🖼️"));
            html.append(createStatCard("Total Commands", stats.getOrDefault("Total Commands Used", "0"), "color-success", "⚡"));
            html.append(createStatCard("New Users", stats.getOrDefault("Total New Users", "0"), "color-warning", "👥"));
            html.append(createStatCard("User Growth", stats.getOrDefault("User Growth", "0"), "color-info", "📈"));
            html.append(createStatCard("Coins Earned", stats.getOrDefault("Total Coins Earned", "0"), "color-success", "💰"));
            html.append(createStatCard("Quests Completed", stats.getOrDefault("Total Quests Completed", "0"), "color-danger", "🎯"));
        }

        html.append("</div>\n");
        return html.toString();
    }

    private static String createStatCard(String label, String value, String colorClass, String icon) {
        return """
        <div class="stat-card">
            <div class="stat-value %s">%s %s</div>
            <div class="stat-label">%s</div>
        </div>
        """.formatted(colorClass, icon, value, label);
    }

    private static String getChartsSection(String yearMonth) throws IOException {

        return "<div class=\"charts-section\">\n" +
                "<div class=\"charts-grid\">\n" +

                // Peak Hours Chart
                createPeakHoursChart(yearMonth) +

                // User Growth Chart
                createUserGrowthChart(yearMonth) +

                // Source Usage Chart
                createSourceUsageChart(yearMonth) +

                // Command Usage Chart
                createCommandUsageChart(yearMonth) +

                // NSFW vs SFW Chart
                createContentTypeChart(yearMonth) +
                "</div>\n" +
                "</div>\n";
    }

    private static String createPeakHoursChart(String yearMonth) throws IOException {
        StringBuilder chartData = new StringBuilder();
        Path reportFile = Paths.get(REPORTS_DIR, "peak_hours_report_" + yearMonth + ".csv");

        if (Files.exists(reportFile)) {
            List<String> hours = new ArrayList<>();
            List<String> images = new ArrayList<>();
            List<String> users = new ArrayList<>();

            try (CSVReader reader = new CSVReader(new FileReader(reportFile.toFile()))) {
                reader.readNext(); // Skip header
                String[] row;
                while ((row = reader.readNext()) != null) {
                    if (row.length >= 4) {
                        hours.add("'" + row[0] + "'");
                        images.add(row[1]);
                        users.add(row[3]);
                    }
                }
            } catch (CsvValidationException e) {
                logger.error("Error reading peak hours file", e);
            }

            chartData.append("labels: [").append(String.join(",", hours)).append("],\n");
            chartData.append("datasets: [{\n");
            chartData.append("  label: 'Avg Images Generated',\n");
            chartData.append("  data: [").append(String.join(",", images)).append("],\n");
            chartData.append("  borderColor: '#3498db',\n");
            chartData.append("  backgroundColor: 'rgba(52, 152, 219, 0.1)',\n");
            chartData.append("  tension: 0.4\n");
            chartData.append("}, {\n");
            chartData.append("  label: 'Avg Unique Users',\n");
            chartData.append("  data: [").append(String.join(",", users)).append("],\n");
            chartData.append("  borderColor: '#e74c3c',\n");
            chartData.append("  backgroundColor: 'rgba(231, 76, 60, 0.1)',\n");
            chartData.append("  tension: 0.4\n");
            chartData.append("}]");
        }

        return """
            <div class="chart-container">
                <h3 class="chart-title">📊 Peak Hours Activity</h3>
                <div class="chart-wrapper">
                    <canvas id="peakHoursChart"></canvas>
                </div>
            </div>
            <script>
                new Chart(document.getElementById('peakHoursChart'), {
                    type: 'line',
                    data: {
                       \s""" + chartData.toString() + """
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        plugins: {
                            legend: { position: 'top' }
                        },
                        scales: {
                            y: { beginAtZero: true }
                        }
                    }
                });
            </script>
        """;
    }

    private static String createUserGrowthChart(String yearMonth) throws IOException {
        StringBuilder chartData = new StringBuilder();
        Path reportFile = Paths.get(REPORTS_DIR, "user_growth_report_" + yearMonth + ".csv");

        if (Files.exists(reportFile)) {
            List<String> dates = new ArrayList<>();
            List<String> totalUsers = new ArrayList<>();
            List<String> newUsers = new ArrayList<>();

            try (CSVReader reader = new CSVReader(new FileReader(reportFile.toFile()))) {
                reader.readNext(); // Skip header
                String[] row;
                while ((row = reader.readNext()) != null) {
                    if (row.length >= 3) {
                        dates.add("'" + row[0] + "'");
                        newUsers.add(row[1]);
                        totalUsers.add(row[2]);
                    }
                }
            } catch (CsvValidationException e) {
                logger.error("Error reading user growth file", e);
            }

            chartData.append("labels: [").append(String.join(",", dates)).append("],\n");
            chartData.append("datasets: [{\n");
            chartData.append("  label: 'Total Users',\n");
            chartData.append("  data: [").append(String.join(",", totalUsers)).append("],\n");
            chartData.append("  borderColor: '#27ae60',\n");
            chartData.append("  backgroundColor: 'rgba(39, 174, 96, 0.1)',\n");
            chartData.append("  tension: 0.4,\n");
            chartData.append("  yAxisID: 'y'\n");
            chartData.append("}, {\n");
            chartData.append("  label: 'Daily New Users',\n");
            chartData.append("  data: [").append(String.join(",", newUsers)).append("],\n");
            chartData.append("  type: 'bar',\n");
            chartData.append("  backgroundColor: 'rgba(241, 196, 15, 0.7)',\n");
            chartData.append("  yAxisID: 'y1'\n");
            chartData.append("}]");
        }

        return """
            <div class="chart-container">
                <h3 class="chart-title">📈 User Growth Trend</h3>
                <div class="chart-wrapper">
                    <canvas id="userGrowthChart"></canvas>
                </div>
            </div>
            <script>
                new Chart(document.getElementById('userGrowthChart'), {
                    type: 'line',
                    data: {
                       \s""" + chartData.toString() + """
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        plugins: {
                            legend: { position: 'top' }
                        },
                        scales: {
                            y: {
                                type: 'linear',
                                display: true,
                                position: 'left',
                                beginAtZero: true
                            },
                            y1: {
                                type: 'linear',
                                display: true,
                                position: 'right',
                                beginAtZero: true,
                                grid: { drawOnChartArea: false }
                            }
                        }
                    }
                });
            </script>
        """;
    }

    private static String createSourceUsageChart(String yearMonth) throws IOException {
        StringBuilder chartData = new StringBuilder();
        Path reportFile = Paths.get(REPORTS_DIR, "source_usage_report_" + yearMonth + ".csv");

        if (Files.exists(reportFile)) {
            List<String> sources = new ArrayList<>();
            List<String> usage = new ArrayList<>();
            List<String> colors = Arrays.asList(
                    "'#3498db'", "'#e74c3c'", "'#2ecc71'", "'#f39c12'", "'#9b59b6'",
                    "'#1abc9c'", "'#34495e'", "'#e67e22'", "'#95a5a6'", "'#c0392b'"
            );

            try (CSVReader reader = new CSVReader(new FileReader(reportFile.toFile()))) {
                reader.readNext(); // Skip header
                String[] row;
                int colorIndex = 0;
                while ((row = reader.readNext()) != null && colorIndex < 10) { // Top 10 sources
                    if (row.length >= 2) {
                        sources.add("'" + row[0] + "'");
                        usage.add(row[1]);
                        colorIndex++;
                    }
                }
            } catch (CsvValidationException e) {
                logger.error("Error reading source usage file", e);
            }

            chartData.append("labels: [").append(String.join(",", sources)).append("],\n");
            chartData.append("datasets: [{\n");
            chartData.append("  data: [").append(String.join(",", usage)).append("],\n");
            chartData.append("  backgroundColor: [").append(String.join(",", colors.subList(0, Math.min(sources.size(), colors.size())))).append("]\n");
            chartData.append("}]");
        }

        return """
            <div class="chart-container">
                <h3 class="chart-title">🎯 Source Usage Distribution</h3>
                <div class="chart-wrapper">
                    <canvas id="sourceUsageChart"></canvas>
                </div>
            </div>
            <script>
                new Chart(document.getElementById('sourceUsageChart'), {
                    type: 'doughnut',
                    data: {
                       \s""" + chartData + """
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        plugins: {
                            legend: { position: 'right' }
                        }
                    }
                });
            </script>
        """;
    }

    private static String createCommandUsageChart(String yearMonth) throws IOException {
        StringBuilder chartData = new StringBuilder();
        Path reportFile = Paths.get(REPORTS_DIR, "command_usage_report_" + yearMonth + ".csv");

        if (Files.exists(reportFile)) {
            List<String> commands = new ArrayList<>();
            List<String> usage = new ArrayList<>();

            try (CSVReader reader = new CSVReader(new FileReader(reportFile.toFile()))) {
                reader.readNext(); // Skip header
                String[] row;
                int count = 0;
                while ((row = reader.readNext()) != null && count < 10) { // Top 10 commands
                    if (row.length >= 2) {
                        commands.add("'" + row[0] + "'");
                        usage.add(row[1]);
                        count++;
                    }
                }
            } catch (CsvValidationException e) {
                logger.error("Error reading command usage file", e);
            }

            chartData.append("labels: [").append(String.join(",", commands)).append("],\n");
            chartData.append("datasets: [{\n");
            chartData.append("  label: 'Usage Count',\n");
            chartData.append("  data: [").append(String.join(",", usage)).append("],\n");
            chartData.append("  backgroundColor: 'rgba(155, 89, 182, 0.7)',\n");
            chartData.append("  borderColor: '#9b59b6',\n");
            chartData.append("  borderWidth: 2\n");
            chartData.append("}]");
        }

        return """
            <div class="chart-container">
                <h3 class="chart-title">⚡ Top Commands Usage</h3>
                <div class="chart-wrapper">
                    <canvas id="commandUsageChart"></canvas>
                </div>
            </div>
            <script>
                new Chart(document.getElementById('commandUsageChart'), {
                    type: 'bar',
                    data: {
                       \s""" + chartData + """
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        plugins: {
                            legend: { display: false }
                        },
                        scales: {
                            y: { beginAtZero: true }
                        }
                    }
                });
            </script>
        """;
    }

    private static String createContentTypeChart(String yearMonth) throws IOException {
        StringBuilder html = new StringBuilder();
        Path reportFile = Paths.get(REPORTS_DIR, "monthly_report_" + yearMonth + ".csv");

        if (Files.exists(reportFile)) {
            String nsfwTotal = "0";
            String sfwTotal = "0";

            try (CSVReader reader = new CSVReader(new FileReader(reportFile.toFile()))) {
                String[] row;
                while ((row = reader.readNext()) != null) {
                    if (row.length >= 2) {
                        if ("Total NSFW Requests".equals(row[0])) {
                            nsfwTotal = row[1];
                        } else if ("Total SFW Requests".equals(row[0])) {
                            sfwTotal = row[1];
                        }
                    }
                }
            } catch (CsvValidationException e) {
                logger.error("Error reading monthly report", e);
            }

            html.append("""
            <div class="chart-container">
                <h3 class="chart-title">🔞 Content Type Distribution</h3>
                <div class="chart-wrapper">
                    <canvas id="contentTypeChart"></canvas>
                </div>
            </div>
            <script>
                new Chart(document.getElementById('contentTypeChart'), {
                    type: 'pie',
                    data: {
                        labels: ['SFW Content', 'NSFW Content'],
                        datasets: [{
                            data: [%s, %s],
                            backgroundColor: ['#2ecc71', '#e74c3c']
                        }]
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        plugins: {
                            legend: { position: 'bottom' }
                        }
                    }
                });
            </script>
            """.formatted(sfwTotal, nsfwTotal));
        }

        return html.toString();
    }

    private static String getTablesSection(String yearMonth) {
        return """
        <div class="table-container">
            <h3 class="chart-title">📋 Detailed Statistics Tables</h3>
            <p>Detailed CSV reports have been generated in the reports directory:</p>
            <ul>
                <li><strong>monthly_report_%s.csv</strong> - General monthly statistics</li>
                <li><strong>source_usage_report_%s.csv</strong> - Source usage breakdown</li>
                <li><strong>command_usage_report_%s.csv</strong> - Command usage statistics</li>
                <li><strong>peak_hours_report_%s.csv</strong> - Peak hours analysis</li>
                <li><strong>user_growth_report_%s.csv</strong> - User growth tracking</li>
            </ul>
        </div>
        """.formatted(yearMonth, yearMonth, yearMonth, yearMonth, yearMonth);
    }


    private static String getHtmlFooter() {
        return """
        <div class="footer">
            <p>🤖 Generated by Media Roulette Stats Analyzer | %s</p>
        </div>
    </div>
</body>
</html>
        """.formatted(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    /**
     * Generate general monthly statistics report (enhanced)
     */
    private static void generateGeneralMonthlyReport(String yearMonth) throws IOException {
        Path inputFile = Paths.get(STATS_DIR, "general_stats_" + yearMonth + ".csv");
        if (!Files.exists(inputFile)) {
            logger.warn("No general stats file found for: {}", yearMonth);
            return;
        }

        List<HourlyStats> hourlyData = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new FileReader(inputFile.toFile()))) {
            String[] header = reader.readNext(); // Skip header
            String[] row;

            while ((row = reader.readNext()) != null) {
                hourlyData.add(new HourlyStats(row));
            }
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }

        if (hourlyData.isEmpty()) return;

        // Calculate monthly totals and averages
        long totalImages = hourlyData.stream().mapToLong(h -> h.imagesGenerated).sum();
        long totalCommands = hourlyData.stream().mapToLong(h -> h.commandsUsed).sum();
        long totalNewUsers = hourlyData.stream().mapToLong(h -> h.newUsers).sum();
        long totalCoinsEarned = hourlyData.stream().mapToLong(h -> h.coinsEarned).sum();
        long totalCoinsSpent = hourlyData.stream().mapToLong(h -> h.coinsSpent).sum();
        long totalQuests = hourlyData.stream().mapToLong(h -> h.questsCompleted).sum();
        long totalNsfwRequests = hourlyData.stream().mapToLong(h -> h.nsfwRequests).sum();
        long totalSfwRequests = hourlyData.stream().mapToLong(h -> h.sfwRequests).sum();
        long totalPremiumActivity = hourlyData.stream().mapToLong(h -> h.premiumActivity).sum();
        long totalRegularActivity = hourlyData.stream().mapToLong(h -> h.regularActivity).sum();

        double avgImagesPerHour = (double) totalImages / hourlyData.size();
        double avgCommandsPerHour = (double) totalCommands / hourlyData.size();
        double avgActiveUsersPerHour = hourlyData.stream().mapToLong(h -> h.activeUsers).average().orElse(0);
        double avgUniqueUsersPerHour = hourlyData.stream().mapToLong(h -> h.uniqueUsers).average().orElse(0);

        // Find peak hours and activity patterns
        HourlyStats peakImagesHour = hourlyData.stream().max(Comparator.comparing(h -> h.imagesGenerated)).orElse(null);
        HourlyStats peakUsersHour = hourlyData.stream().max(Comparator.comparing(h -> h.uniqueUsers)).orElse(null);
        HourlyStats peakCommandsHour = hourlyData.stream().max(Comparator.comparing(h -> h.commandsUsed)).orElse(null);

        // User growth and engagement metrics
        HourlyStats firstHour = hourlyData.getFirst();
        HourlyStats lastHour = hourlyData.getLast();
        long userGrowth = lastHour.totalUsersInDb - firstHour.totalUsersInDb;
        long imageGrowth = lastHour.totalImagesInDb - firstHour.totalImagesInDb;

        // Calculate retention and engagement rates
        double avgRetentionRate = totalCommands > 0 ? (double) totalImages / totalCommands * 100 : 0;
        double premiumUserPercentage = (totalPremiumActivity + totalRegularActivity) > 0 ?
                (double) totalPremiumActivity / (totalPremiumActivity + totalRegularActivity) * 100 : 0;

        // Economic metrics
        long netCoinFlow = totalCoinsEarned - totalCoinsSpent;
        double avgCoinsPerUser = totalNewUsers > 0 ? (double) totalCoinsEarned / totalNewUsers : 0;

        // Write enhanced report
        Path reportFile = Paths.get(REPORTS_DIR, "monthly_report_" + yearMonth + ".csv");
        try (CSVWriter writer = new CSVWriter(new FileWriter(reportFile.toFile()))) {
            // Write summary statistics
            writer.writeNext(new String[]{"Metric", "Value", "Category"});
            writer.writeNext(new String[]{"Month", yearMonth, "General"});
            writer.writeNext(new String[]{"Reporting Period", firstHour.hour + " to " + lastHour.hour, "General"});
            writer.writeNext(new String[]{"Total Hours Tracked", String.valueOf(hourlyData.size()), "General"});

            // Activity Metrics
            writer.writeNext(new String[]{"Total Images Generated", String.valueOf(totalImages), "Activity"});
            writer.writeNext(new String[]{"Total Commands Used", String.valueOf(totalCommands), "Activity"});
            writer.writeNext(new String[]{"Total Quests Completed", String.valueOf(totalQuests), "Activity"});
            writer.writeNext(new String[]{"Total NSFW Requests", String.valueOf(totalNsfwRequests), "Activity"});
            writer.writeNext(new String[]{"Total SFW Requests", String.valueOf(totalSfwRequests), "Activity"});
            writer.writeNext(new String[]{"Total Premium Activity", String.valueOf(totalPremiumActivity), "Activity"});
            writer.writeNext(new String[]{"Total Regular Activity", String.valueOf(totalRegularActivity), "Activity"});

            // User Metrics
            writer.writeNext(new String[]{"Total New Users", String.valueOf(totalNewUsers), "Users"});
            writer.writeNext(new String[]{"User Growth", String.valueOf(userGrowth), "Users"});
            writer.writeNext(new String[]{"Final User Count", String.valueOf(lastHour.totalUsersInDb), "Users"});
            writer.writeNext(new String[]{"Final Image Count", String.valueOf(lastHour.totalImagesInDb), "Users"});
            writer.writeNext(new String[]{"Image Database Growth", String.valueOf(imageGrowth), "Users"});

            // Economic Metrics
            writer.writeNext(new String[]{"Total Coins Earned", String.valueOf(totalCoinsEarned), "Economics"});
            writer.writeNext(new String[]{"Total Coins Spent", String.valueOf(totalCoinsSpent), "Economics"});
            writer.writeNext(new String[]{"Net Coin Flow", String.valueOf(netCoinFlow), "Economics"});
            writer.writeNext(new String[]{"Avg Coins per New User", String.format("%.2f", avgCoinsPerUser), "Economics"});

            // Performance Averages
            writer.writeNext(new String[]{"Avg Images per Hour", String.format("%.2f", avgImagesPerHour), "Performance"});
            writer.writeNext(new String[]{"Avg Commands per Hour", String.format("%.2f", avgCommandsPerHour), "Performance"});
            writer.writeNext(new String[]{"Avg Active Users per Hour", String.format("%.2f", avgActiveUsersPerHour), "Performance"});
            writer.writeNext(new String[]{"Avg Unique Users per Hour", String.format("%.2f", avgUniqueUsersPerHour), "Performance"});
            writer.writeNext(new String[]{"Retention Rate", String.format("%.2f%%", avgRetentionRate), "Performance"});
            writer.writeNext(new String[]{"Premium User Percentage", String.format("%.2f%%", premiumUserPercentage), "Performance"});

            // Peak Activity
            writer.writeNext(new String[]{"Peak Images Hour", peakImagesHour.hour + " (" + peakImagesHour.imagesGenerated + " images)", "Peaks"});
            writer.writeNext(new String[]{"Peak Users Hour", peakUsersHour.hour + " (" + peakUsersHour.uniqueUsers + " users)", "Peaks"});
            writer.writeNext(new String[]{"Peak Commands Hour", peakCommandsHour.hour + " (" + peakCommandsHour.commandsUsed + " commands)", "Peaks"});

            // Content Analysis
            long totalRequests = totalNsfwRequests + totalSfwRequests;
            if (totalRequests > 0) {
                double nsfwPercentage = (double) totalNsfwRequests / totalRequests * 100;
                double sfwPercentage = (double) totalSfwRequests / totalRequests * 100;
                writer.writeNext(new String[]{"NSFW Percentage", String.format("%.2f%%", nsfwPercentage), "Content"});
                writer.writeNext(new String[]{"SFW Percentage", String.format("%.2f%%", sfwPercentage), "Content"});
            }

            // Engagement Metrics
            double imagesPerUser = lastHour.totalUsersInDb > 0 ? (double) lastHour.totalImagesInDb / lastHour.totalUsersInDb : 0;
            double commandsPerUser = lastHour.totalUsersInDb > 0 ? (double) totalCommands / lastHour.totalUsersInDb : 0;
            writer.writeNext(new String[]{"Images per User (Total)", String.format("%.2f", imagesPerUser), "Engagement"});
            writer.writeNext(new String[]{"Commands per User (Monthly)", String.format("%.2f", commandsPerUser), "Engagement"});
        }
    }

    /**
     * Generate enhanced source usage report with better categorization
     */
    private static void generateSourceUsageReport(String yearMonth) throws IOException, CsvValidationException {
        Path inputFile = Paths.get(STATS_DIR, "source_usage_" + yearMonth + ".csv");
        if (!Files.exists(inputFile)) {
            logger.warn("No source usage file found for: {}", yearMonth);
            return;
        }

        Map<String, Long> sourceUsageTotals = new HashMap<>();
        Map<String, Map<String, Long>> hourlySourceUsage = new HashMap<>();

        try (CSVReader reader = new CSVReader(new FileReader(inputFile.toFile()))) {
            String[] header = reader.readNext(); // Skip header
            String[] row;

            while ((row = reader.readNext()) != null) {
                SourceUsageStats stats = new SourceUsageStats(row);
                sourceUsageTotals.merge(stats.source, stats.usageCount, Long::sum);

                // Track hourly usage for trend analysis
                hourlySourceUsage.computeIfAbsent(stats.hour, k -> new HashMap<>())
                        .merge(stats.source, stats.usageCount, Long::sum);
            }
        }

        // Sort by usage count (descending)
        List<Map.Entry<String, Long>> sortedSources = sourceUsageTotals.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toList());

        // Write enhanced report
        Path reportFile = Paths.get(REPORTS_DIR, "source_usage_report_" + yearMonth + ".csv");

        long totalUsage;

        try (CSVWriter writer = new CSVWriter(new FileWriter(reportFile.toFile()))) {
            writer.writeNext(new String[]{"Rank", "Source", "Total Usage", "Percentage", "Category", "Growth Trend"});

            totalUsage = sortedSources.stream().mapToLong(Map.Entry::getValue).sum();

            for (int i = 0; i < sortedSources.size(); i++) {
                Map.Entry<String, Long> entry = sortedSources.get(i);
                double percentage = totalUsage > 0 ? (double) entry.getValue() / totalUsage * 100 : 0;

                // Categorize sources
                String category = categorizeSource(entry.getKey());

                // Calculate trend (simple growth indicator)
                String trend = calculateSourceTrend(entry.getKey(), hourlySourceUsage);

                writer.writeNext(new String[]{
                        String.valueOf(i + 1),
                        entry.getKey(),
                        String.valueOf(entry.getValue()),
                        String.format("%.2f%%", percentage),
                        category,
                        trend
                });
            }
        }

        // Generate source category summary
        generateSourceCategorySummary(sortedSources, yearMonth, totalUsage);
    }

    /**
     * Categorize sources for better analysis
     */
    private static String categorizeSource(String source) {
        String lowerSource = source.toLowerCase();

        if (lowerSource.contains("anime") || lowerSource.contains("manga")) return "Anime/Manga";
        if (lowerSource.contains("game") || lowerSource.contains("gaming")) return "Gaming";
        if (lowerSource.contains("art") || lowerSource.contains("drawing")) return "Art";
        if (lowerSource.contains("photo") || lowerSource.contains("real")) return "Photography";
        if (lowerSource.contains("meme") || lowerSource.contains("funny")) return "Memes";
        if (lowerSource.contains("nsfw") || lowerSource.contains("adult")) return "NSFW";
        if (lowerSource.contains("random") || lowerSource.contains("misc")) return "Random";

        return "Other";
    }

    /**
     * Calculate simple trend indicator for source usage
     */
    private static String calculateSourceTrend(String source, Map<String, Map<String, Long>> hourlyData) {
        if (hourlyData.size() < 2) return "Stable";

        List<String> hours = new ArrayList<>(hourlyData.keySet());
        hours.sort(String::compareTo);

        if (hours.size() < 2) return "Stable";

        // Compare first and last quarter of the month
        int quarterSize = Math.max(1, hours.size() / 4);

        long firstQuarterUsage = 0;
        long lastQuarterUsage = 0;

        // First quarter
        for (int i = 0; i < quarterSize; i++) {
            Map<String, Long> hourData = hourlyData.get(hours.get(i));
            firstQuarterUsage += hourData.getOrDefault(source, 0L);
        }

        // Last quarter
        for (int i = hours.size() - quarterSize; i < hours.size(); i++) {
            Map<String, Long> hourData = hourlyData.get(hours.get(i));
            lastQuarterUsage += hourData.getOrDefault(source, 0L);
        }

        if (firstQuarterUsage == 0) return lastQuarterUsage > 0 ? "Growing" : "New";

        double growthRate = ((double) lastQuarterUsage - firstQuarterUsage) / firstQuarterUsage;

        if (growthRate > 0.2) return "Growing";
        if (growthRate < -0.2) return "Declining";
        return "Stable";
    }

    /**
     * Generate source category summary
     */
    private static void generateSourceCategorySummary(List<Map.Entry<String, Long>> sortedSources,
                                                      String yearMonth, long totalUsage) throws IOException {
        Map<String, Long> categoryTotals = new HashMap<>();

        for (Map.Entry<String, Long> entry : sortedSources) {
            String category = categorizeSource(entry.getKey());
            categoryTotals.merge(category, entry.getValue(), Long::sum);
        }

        Path categoryFile = Paths.get(REPORTS_DIR, "source_categories_" + yearMonth + ".csv");
        try (CSVWriter writer = new CSVWriter(new FileWriter(categoryFile.toFile()))) {
            writer.writeNext(new String[]{"Category", "Total Usage", "Percentage", "Source Count"});

            categoryTotals.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(entry -> {
                        long sourceCount = sortedSources.stream()
                                .mapToLong(s -> categorizeSource(s.getKey()).equals(entry.getKey()) ? 1 : 0)
                                .sum();

                        double percentage = totalUsage > 0 ? (double) entry.getValue() / totalUsage * 100 : 0;

                        writer.writeNext(new String[]{
                                entry.getKey(),
                                String.valueOf(entry.getValue()),
                                String.format("%.2f%%", percentage),
                                String.valueOf(sourceCount)
                        });
                    });
        }
    }

    /**
     * Generate enhanced command usage report
     */
    private static void generateCommandUsageReport(String yearMonth) throws IOException, CsvValidationException {
        Path inputFile = Paths.get(STATS_DIR, "command_usage_" + yearMonth + ".csv");
        if (!Files.exists(inputFile)) {
            logger.warn("No command usage file found for: {}", yearMonth);
            return;
        }

        Map<String, Long> commandUsageTotals = new HashMap<>();
        Map<String, Map<String, Long>> hourlyCommandUsage = new HashMap<>();

        try (CSVReader reader = new CSVReader(new FileReader(inputFile.toFile()))) {
            String[] header = reader.readNext(); // Skip header
            String[] row;

            while ((row = reader.readNext()) != null) {
                CommandUsageStats stats = new CommandUsageStats(row);
                commandUsageTotals.merge(stats.command, stats.usageCount, Long::sum);

                // Track hourly usage for trend analysis
                hourlyCommandUsage.computeIfAbsent(stats.hour, k -> new HashMap<>())
                        .merge(stats.command, stats.usageCount, Long::sum);
            }
        }

        // Sort by usage count (descending)
        List<Map.Entry<String, Long>> sortedCommands = commandUsageTotals.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .toList();

        // Write enhanced report
        Path reportFile = Paths.get(REPORTS_DIR, "command_usage_report_" + yearMonth + ".csv");
        try (CSVWriter writer = new CSVWriter(new FileWriter(reportFile.toFile()))) {
            writer.writeNext(new String[]{"Rank", "Command", "Total Usage", "Percentage", "Avg Usage Per Day", "Trend"});

            long totalUsage = sortedCommands.stream().mapToLong(Map.Entry::getValue).sum();
            int daysInMonth = hourlyCommandUsage.size() / 24; // Approximate

            for (int i = 0; i < sortedCommands.size(); i++) {
                Map.Entry<String, Long> entry = sortedCommands.get(i);
                double percentage = totalUsage > 0 ? (double) entry.getValue() / totalUsage * 100 : 0;
                double avgPerDay = daysInMonth > 0 ? (double) entry.getValue() / daysInMonth : entry.getValue();

                String trend = calculateSourceTrend(entry.getKey(), hourlyCommandUsage);

                writer.writeNext(new String[]{
                        String.valueOf(i + 1),
                        entry.getKey(),
                        String.valueOf(entry.getValue()),
                        String.format("%.2f%%", percentage),
                        String.format("%.1f", avgPerDay),
                        trend
                });
            }
        }
    }

    /**
     * Generate enhanced peak hours analysis report
     */
    private static void generatePeakHoursReport(String yearMonth) throws IOException {
        Path inputFile = Paths.get(STATS_DIR, "general_stats_" + yearMonth + ".csv");
        if (!Files.exists(inputFile)) return;

        Map<Integer, List<HourlyStats>> hourlyGroups = new HashMap<>();

        try (CSVReader reader = new CSVReader(new FileReader(inputFile.toFile()))) {
            String[] header = reader.readNext(); // Skip header
            String[] row;

            while ((row = reader.readNext()) != null) {
                HourlyStats stats = new HourlyStats(row);
                // Extract hour of day from timestamp (format: yyyy-MM-dd-HH)
                String[] parts = stats.hour.split("-");
                if (parts.length >= 4) {
                    int hourOfDay = Integer.parseInt(parts[3]);
                    hourlyGroups.computeIfAbsent(hourOfDay, k -> new ArrayList<>()).add(stats);
                }
            }
        } catch (CsvValidationException e) {
            throw new RuntimeException(e);
        }

        // Calculate averages and peak indicators for each hour of day
        Path reportFile = Paths.get(REPORTS_DIR, "peak_hours_report_" + yearMonth + ".csv");
        try (CSVWriter writer = new CSVWriter(new FileWriter(reportFile.toFile()))) {
            writer.writeNext(new String[]{
                    "Hour_of_Day", "Avg_Images", "Avg_Commands", "Avg_Active_Users",
                    "Avg_Unique_Users", "Avg_NSFW_Requests", "Avg_SFW_Requests",
                    "Peak_Images", "Peak_Users", "Activity_Level", "Time_Period"
            });

            // Find overall peaks for comparison
            double maxAvgImages = 0;
            double maxAvgUsers = 0;

            // First pass to find maximums
            for (int hour = 0; hour < 24; hour++) {
                List<HourlyStats> hourData = hourlyGroups.getOrDefault(hour, new ArrayList<>());
                if (!hourData.isEmpty()) {
                    double avgImages = hourData.stream().mapToLong(h -> h.imagesGenerated).average().orElse(0);
                    double avgUsers = hourData.stream().mapToLong(h -> h.uniqueUsers).average().orElse(0);
                    maxAvgImages = Math.max(maxAvgImages, avgImages);
                    maxAvgUsers = Math.max(maxAvgUsers, avgUsers);
                }
            }

            // Second pass to write data with classifications
            for (int hour = 0; hour < 24; hour++) {
                List<HourlyStats> hourData = hourlyGroups.getOrDefault(hour, new ArrayList<>());

                if (!hourData.isEmpty()) {
                    double avgImages = hourData.stream().mapToLong(h -> h.imagesGenerated).average().orElse(0);
                    double avgCommands = hourData.stream().mapToLong(h -> h.commandsUsed).average().orElse(0);
                    double avgActiveUsers = hourData.stream().mapToLong(h -> h.activeUsers).average().orElse(0);
                    double avgUniqueUsers = hourData.stream().mapToLong(h -> h.uniqueUsers).average().orElse(0);
                    double avgNsfw = hourData.stream().mapToLong(h -> h.nsfwRequests).average().orElse(0);
                    double avgSfw = hourData.stream().mapToLong(h -> h.sfwRequests).average().orElse(0);

                    long peakImages = hourData.stream().mapToLong(h -> h.imagesGenerated).max().orElse(0);
                    long peakUsers = hourData.stream().mapToLong(h -> h.uniqueUsers).max().orElse(0);

                    // Classify activity level
                    String activityLevel = "Low";
                    if (avgImages > maxAvgImages * 0.7 || avgUniqueUsers > maxAvgUsers * 0.7) {
                        activityLevel = "High";
                    } else if (avgImages > maxAvgImages * 0.4 || avgUniqueUsers > maxAvgUsers * 0.4) {
                        activityLevel = "Medium";
                    }

                    // Classify time period
                    String timePeriod = getTimePeriod(hour);

                    writer.writeNext(new String[]{
                            String.format("%02d:00", hour),
                            String.format("%.2f", avgImages),
                            String.format("%.2f", avgCommands),
                            String.format("%.2f", avgActiveUsers),
                            String.format("%.2f", avgUniqueUsers),
                            String.format("%.2f", avgNsfw),
                            String.format("%.2f", avgSfw),
                            String.valueOf(peakImages),
                            String.valueOf(peakUsers),
                            activityLevel,
                            timePeriod
                    });
                }
            }
        }

        // Generate time period summary
        generateTimePeriodSummary(hourlyGroups, yearMonth);
    }

    /**
     * Get time period classification
     */
    private static String getTimePeriod(int hour) {
        if (hour >= 6 && hour < 12) return "Morning";
        if (hour >= 12 && hour < 18) return "Afternoon";
        if (hour >= 18 && hour < 24) return "Evening";
        return "Night";
    }

    /**
     * Generate time period summary report
     */
    private static void generateTimePeriodSummary(Map<Integer, List<HourlyStats>> hourlyGroups, String yearMonth) throws IOException {
        Map<String, List<Double>> periodImages = new HashMap<>();
        Map<String, List<Double>> periodUsers = new HashMap<>();

        for (int hour = 0; hour < 24; hour++) {
            List<HourlyStats> hourData = hourlyGroups.getOrDefault(hour, new ArrayList<>());
            if (!hourData.isEmpty()) {
                String period = getTimePeriod(hour);
                double avgImages = hourData.stream().mapToLong(h -> h.imagesGenerated).average().orElse(0);
                double avgUsers = hourData.stream().mapToLong(h -> h.uniqueUsers).average().orElse(0);

                periodImages.computeIfAbsent(period, k -> new ArrayList<>()).add(avgImages);
                periodUsers.computeIfAbsent(period, k -> new ArrayList<>()).add(avgUsers);
            }
        }

        Path summaryFile = Paths.get(REPORTS_DIR, "time_period_summary_" + yearMonth + ".csv");
        try (CSVWriter writer = new CSVWriter(new FileWriter(summaryFile.toFile()))) {
            writer.writeNext(new String[]{"Time_Period", "Avg_Images", "Avg_Users", "Peak_Performance", "Hours_Included"});

            for (String period : Arrays.asList("Morning", "Afternoon", "Evening", "Night")) {
                List<Double> images = periodImages.getOrDefault(period, new ArrayList<>());
                List<Double> users = periodUsers.getOrDefault(period, new ArrayList<>());

                if (!images.isEmpty()) {
                    double avgImages = images.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    double avgUsers = users.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                    double maxImages = images.stream().mapToDouble(Double::doubleValue).max().orElse(0);

                    String performance = maxImages > avgImages * 1.5 ? "High Variance" : "Consistent";

                    writer.writeNext(new String[]{
                            period,
                            String.format("%.2f", avgImages),
                            String.format("%.2f", avgUsers),
                            performance,
                            String.valueOf(images.size())
                    });
                }
            }
        }
    }

    /**
     * Generate enhanced user growth report
     */
    private static void generateUserGrowthReport(String yearMonth) throws IOException, CsvValidationException {
        Path inputFile = Paths.get(STATS_DIR, "general_stats_" + yearMonth + ".csv");
        if (!Files.exists(inputFile)) return;

        List<HourlyStats> hourlyData = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new FileReader(inputFile.toFile()))) {
            String[] header = reader.readNext(); // Skip header
            String[] row;

            while ((row = reader.readNext()) != null) {
                hourlyData.add(new HourlyStats(row));
            }
        }

        // Group by day and calculate daily growth metrics
        Map<String, List<HourlyStats>> dailyGroups = hourlyData.stream()
                .collect(Collectors.groupingBy(h -> h.hour.substring(0, 10))); // Extract date part

        Path reportFile = Paths.get(REPORTS_DIR, "user_growth_report_" + yearMonth + ".csv");
        List<String> sortedDates;
        try (CSVWriter writer = new CSVWriter(new FileWriter(reportFile.toFile()))) {
            writer.writeNext(new String[]{
                    "Date", "New_Users", "Total_Users_End_of_Day", "Daily_Growth_Rate",
                    "Total_Images_End_of_Day", "Avg_Images_Per_User", "User_Retention_Indicator",
                    "Daily_Activity_Score", "Weekend_Indicator"
            });

            sortedDates = dailyGroups.keySet().stream().sorted().collect(Collectors.toList());

            for (String date : sortedDates) {
                List<HourlyStats> dayData = dailyGroups.get(date);

                // Sort by hour to get end of day stats
                dayData.sort(Comparator.comparing(h -> h.hour));

                long dailyNewUsers = dayData.stream().mapToLong(h -> h.newUsers).sum();
                long dailyCommands = dayData.stream().mapToLong(h -> h.commandsUsed).sum();
                long dailyImages = dayData.stream().mapToLong(h -> h.imagesGenerated).sum();
                HourlyStats endOfDay = dayData.getLast();

                double growthRate = endOfDay.totalUsersInDb > 0 ?
                        (double) dailyNewUsers / endOfDay.totalUsersInDb * 100 : 0;

                double avgImagesPerUser = endOfDay.totalUsersInDb > 0 ?
                        (double) endOfDay.totalImagesInDb / endOfDay.totalUsersInDb : 0;

                // Calculate user retention indicator (commands per user)
                double retentionIndicator = dailyNewUsers > 0 ? (double) dailyCommands / dailyNewUsers : 0;

                // Calculate daily activity score (normalized combination of metrics)
                double activityScore = (dailyImages * 0.4 + dailyCommands * 0.4 + dailyNewUsers * 0.2) / 100.0;

                // Determine if it's weekend
                String weekendIndicator = isWeekend(date) ? "Weekend" : "Weekday";

                writer.writeNext(new String[]{
                        date,
                        String.valueOf(dailyNewUsers),
                        String.valueOf(endOfDay.totalUsersInDb),
                        String.format("%.4f%%", growthRate),
                        String.valueOf(endOfDay.totalImagesInDb),
                        String.format("%.2f", avgImagesPerUser),
                        String.format("%.2f", retentionIndicator),
                        String.format("%.2f", activityScore),
                        weekendIndicator
                });
            }
        }

        // Generate growth insights report
        generateGrowthInsights(sortedDates, dailyGroups, yearMonth);
    }

    /**
     * Check if a date is weekend
     */
    private static boolean isWeekend(String date) {
        try {
            java.time.LocalDate localDate = java.time.LocalDate.parse(date);
            java.time.DayOfWeek dayOfWeek = localDate.getDayOfWeek();
            return dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Generate growth insights and trends analysis
     */
    private static void generateGrowthInsights(List<String> sortedDates, Map<String, List<HourlyStats>> dailyGroups, String yearMonth) throws IOException {
        Path insightsFile = Paths.get(REPORTS_DIR, "growth_insights_" + yearMonth + ".csv");

        try (CSVWriter writer = new CSVWriter(new FileWriter(insightsFile.toFile()))) {
            writer.writeNext(new String[]{"Insight_Type", "Metric", "Value", "Description"});

            // Calculate week-over-week growth
            if (sortedDates.size() >= 14) {
                List<String> firstWeek = sortedDates.subList(0, 7);
                List<String> lastWeek = sortedDates.subList(sortedDates.size() - 7, sortedDates.size());

                long firstWeekUsers = firstWeek.stream()
                        .mapToLong(date -> dailyGroups.get(date).stream().mapToLong(h -> h.newUsers).sum())
                        .sum();

                long lastWeekUsers = lastWeek.stream()
                        .mapToLong(date -> dailyGroups.get(date).stream().mapToLong(h -> h.newUsers).sum())
                        .sum();

                double weekOverWeekGrowth = firstWeekUsers > 0 ?
                        ((double) lastWeekUsers - firstWeekUsers) / firstWeekUsers * 100 : 0;

                writer.writeNext(new String[]{
                        "Growth_Trend", "Week_Over_Week_Growth", String.format("%.2f%%", weekOverWeekGrowth),
                        "Growth rate comparing first and last week of the month"
                });
            }

            // Find best performing day
            String bestDay = "";
            long maxNewUsers = 0;

            for (String date : sortedDates) {
                long dailyNewUsers = dailyGroups.get(date).stream().mapToLong(h -> h.newUsers).sum();
                if (dailyNewUsers > maxNewUsers) {
                    maxNewUsers = dailyNewUsers;
                    bestDay = date;
                }
            }

            writer.writeNext(new String[]{
                    "Performance", "Best_Day", bestDay + " (" + maxNewUsers + " new users)",
                    "Day with highest new user acquisition"
            });

            // Calculate weekend vs weekday performance
            long weekendUsers = 0;
            long weekdayUsers = 0;
            int weekendDays = 0;
            int weekdayDays = 0;

            for (String date : sortedDates) {
                long dailyNewUsers = dailyGroups.get(date).stream().mapToLong(h -> h.newUsers).sum();
                if (isWeekend(date)) {
                    weekendUsers += dailyNewUsers;
                    weekendDays++;
                } else {
                    weekdayUsers += dailyNewUsers;
                    weekdayDays++;
                }
            }

            double avgWeekendUsers = weekendDays > 0 ? (double) weekendUsers / weekendDays : 0;
            double avgWeekdayUsers = weekdayDays > 0 ? (double) weekdayUsers / weekdayDays : 0;

            writer.writeNext(new String[]{
                    "Patterns", "Weekend_vs_Weekday",
                    String.format("Weekend: %.1f, Weekday: %.1f", avgWeekendUsers, avgWeekdayUsers),
                    "Average new users per day comparison"
            });

            // Growth consistency analysis
            List<Long> dailyNewUsersList = sortedDates.stream()
                    .map(date -> dailyGroups.get(date).stream().mapToLong(h -> h.newUsers).sum())
                    .toList();

            double avgDailyUsers = dailyNewUsersList.stream().mapToLong(Long::longValue).average().orElse(0);
            double variance = dailyNewUsersList.stream()
                    .mapToDouble(users -> Math.pow(users - avgDailyUsers, 2))
                    .average().orElse(0);
            double standardDeviation = Math.sqrt(variance);

            String consistency = standardDeviation < avgDailyUsers * 0.5 ? "High" :
                    standardDeviation < avgDailyUsers ? "Medium" : "Low";

            writer.writeNext(new String[]{
                    "Consistency", "Growth_Stability", consistency + " (σ=" + String.format("%.2f", standardDeviation) + ")",
                    "Consistency of daily new user acquisition"
            });
        }
    }

    /**
     * Generate comprehensive analytics index page
     */
    public static void generateAnalyticsIndex() throws IOException {
        Path indexFile = Paths.get(REPORTS_DIR, "index.html");

        StringBuilder html = new StringBuilder();
        html.append("""
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Media Roulette Analytics Hub</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%);
            min-height: 100vh;
            color: #333;
        }
        
        .container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 20px;
        }
        
        .header {
            background: rgba(255, 255, 255, 0.95);
            border-radius: 20px;
            padding: 40px;
            margin-bottom: 40px;
            box-shadow: 0 15px 35px rgba(0, 0, 0, 0.1);
            text-align: center;
        }
        
        .header h1 {
            color: #2c3e50;
            font-size: 3em;
            margin-bottom: 15px;
            font-weight: 300;
        }
        
        .reports-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(350px, 1fr));
            gap: 25px;
            margin-bottom: 40px;
        }
        
        .report-card {
            background: rgba(255, 255, 255, 0.95);
            border-radius: 15px;
            padding: 30px;
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
            transition: all 0.3s ease;
            border-left: 5px solid #3498db;
        }
        
        .report-card:hover {
            transform: translateY(-5px);
            box-shadow: 0 20px 40px rgba(0, 0, 0, 0.15);
        }
        
        .report-card h3 {
            color: #2c3e50;
            margin-bottom: 15px;
            font-size: 1.5em;
        }
        
        .report-card p {
            color: #7f8c8d;
            line-height: 1.6;
            margin-bottom: 20px;
        }
        
        .report-link {
            display: inline-block;
            background: #3498db;
            color: white;
            padding: 10px 20px;
            border-radius: 8px;
            text-decoration: none;
            transition: background 0.3s ease;
        }
        
        .report-link:hover {
            background: #2980b9;
        }
        
        .quick-stats {
            background: rgba(255, 255, 255, 0.95);
            border-radius: 15px;
            padding: 30px;
            margin-bottom: 30px;
            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.1);
        }
        
        .stats-row {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 20px;
            margin-top: 20px;
        }
        
        .stat-item {
            text-align: center;
            padding: 15px;
            background: #f8f9fa;
            border-radius: 10px;
        }
        
        .stat-value {
            font-size: 1.8em;
            font-weight: bold;
            color: #3498db;
        }
        
        .stat-label {
            color: #7f8c8d;
            font-size: 0.9em;
            text-transform: uppercase;
            letter-spacing: 1px;
        }
        
        .footer {
            text-align: center;
            padding: 30px;
            color: rgba(255, 255, 255, 0.8);
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>📊 Media Roulette Analytics Hub</h1>
            <p>Comprehensive insights and reporting dashboard</p>
        </div>
        
        <div class="quick-stats">
            <h2>🚀 Quick Statistics</h2>
            <div class="stats-row">
                <div class="stat-item">
                    <div class="stat-value" id="totalReports">-</div>
                    <div class="stat-label">Total Reports</div>
                </div>
                <div class="stat-item">
                    <div class="stat-value" id="lastGenerated">-</div>
                    <div class="stat-label">Last Generated</div>
                </div>
                <div class="stat-item">
                    <div class="stat-value" id="dataPoints">-</div>
                    <div class="stat-label">Data Points</div>
                </div>
                <div class="stat-item">
                    <div class="stat-value" id="chartTypes">12+</div>
                    <div class="stat-label">Chart Types</div>
                </div>
            </div>
        </div>
        
        <div class="reports-grid">
""");

        // List available reports
        try {
            Files.list(Paths.get(REPORTS_DIR))
                    .filter(path -> path.toString().endsWith("dashboard_"))
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        String monthYear = fileName.replace("dashboard_", "").replace(".html", "");

                        html.append(String.format("""
                        <div class="report-card">
                            <h3>📈 %s Report</h3>
                            <p>Comprehensive analytics dashboard with interactive charts, user growth analysis, and performance metrics.</p>
                            <a href="%s" class="report-link">View Dashboard</a>
                        </div>
                    """, monthYear, fileName));
                    });
        } catch (IOException e) {
            logger.warn("Could not list report files", e);
        }

        html.append("""
        </div>
        
        <div class="footer">
            <p>🤖 Powered by Enhanced Media Roulette Stats Analyzer | Auto-refreshed analytics</p>
        </div>
    </div>
    
    <script>
        // Update quick stats
        document.getElementById('totalReports').textContent = document.querySelectorAll('.report-card').length;
        document.getElementById('lastGenerated').textContent = new Date().toLocaleDateString();
        document.getElementById('dataPoints').textContent = '1M+';
    </script>
</body>
</html>
""");

        Files.write(indexFile, html.toString().getBytes());
        logger.info("Generated analytics index page: {}", indexFile);
    }

    /**
     * Generate report for current month
     */
    public static void generateCurrentMonthReport() throws IOException, CsvValidationException {
        String currentMonth = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        generateMonthlyReport(currentMonth);
        generateAnalyticsIndex();
    }

    /**
     * Generate report for previous month
     */
    public static void generatePreviousMonthReport() throws IOException, CsvValidationException {
        String previousMonth = LocalDateTime.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        generateMonthlyReport(previousMonth);
        generateAnalyticsIndex();
    }

    /**
     * Generate reports for multiple months
     */
    public static void generateMultiMonthReport(String... months) throws IOException, CsvValidationException {
        for (String month : months) {
            logger.info("Generating report for month: {}", month);
            generateMonthlyReport(month);
        }
        generateAnalyticsIndex();
        logger.info("Generated reports for {} months", months.length);
    }

    /**
     * Clean old reports (keep last 6 months)
     */
    public static void cleanOldReports() throws IOException {
        LocalDateTime sixMonthsAgo = LocalDateTime.now().minusMonths(6);
        String cutoffMonth = sixMonthsAgo.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        try {
            Files.list(Paths.get(REPORTS_DIR))
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        if (fileName.matches(".*_\\d{4}-\\d{2}\\..*")) {
                            String fileMonth = fileName.replaceAll(".*_(\\d{4}-\\d{2})\\..*", "$1");
                            return fileMonth.compareTo(cutoffMonth) < 0;
                        }
                        return false;
                    })
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                            logger.info("Deleted old report: {}", path.getFileName());
                        } catch (IOException e) {
                            logger.warn("Could not delete old report: {}", path.getFileName(), e);
                        }
                    });
        } catch (IOException e) {
            logger.warn("Could not clean old reports", e);
        }
    }
}