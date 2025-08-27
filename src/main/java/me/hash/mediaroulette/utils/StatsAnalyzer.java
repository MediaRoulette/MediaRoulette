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

/**
 * Utility class for analyzing CSV stats data and generating reports
 */
public class StatsAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(StatsAnalyzer.class);

    private static final String STATS_DIR = "stats";
    private static final String REPORTS_DIR = "reports";
    
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
     * Generate monthly report for a specific month
     */
    public static void generateMonthlyReport(String yearMonth) throws IOException, CsvValidationException {
        // Create reports directory if it doesn't exist
        Files.createDirectories(Paths.get(REPORTS_DIR));
        
        // Generate different types of reports
        generateGeneralMonthlyReport(yearMonth);
        generateSourceUsageReport(yearMonth);
        generateCommandUsageReport(yearMonth);
        generatePeakHoursReport(yearMonth);
        generateUserGrowthReport(yearMonth);

        logger.info("Generated monthly report for: {}", yearMonth);
    }
    
    /**
     * Generate general monthly statistics report
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
        
        double avgImagesPerHour = (double) totalImages / hourlyData.size();
        double avgCommandsPerHour = (double) totalCommands / hourlyData.size();
        double avgActiveUsersPerHour = hourlyData.stream().mapToLong(h -> h.activeUsers).average().orElse(0);
        double avgUniqueUsersPerHour = hourlyData.stream().mapToLong(h -> h.uniqueUsers).average().orElse(0);
        
        // Find peak hours
        HourlyStats peakImagesHour = hourlyData.stream().max(Comparator.comparing(h -> h.imagesGenerated)).orElse(null);
        HourlyStats peakUsersHour = hourlyData.stream().max(Comparator.comparing(h -> h.uniqueUsers)).orElse(null);
        
        // User growth
        HourlyStats firstHour = hourlyData.get(0);
        HourlyStats lastHour = hourlyData.get(hourlyData.size() - 1);
        long userGrowth = lastHour.totalUsersInDb - firstHour.totalUsersInDb;
        
        // Write report
        Path reportFile = Paths.get(REPORTS_DIR, "monthly_report_" + yearMonth + ".csv");
        try (CSVWriter writer = new CSVWriter(new FileWriter(reportFile.toFile()))) {
            // Write summary statistics
            writer.writeNext(new String[]{"Metric", "Value"});
            writer.writeNext(new String[]{"Month", yearMonth});
            writer.writeNext(new String[]{"Total Hours Tracked", String.valueOf(hourlyData.size())});
            writer.writeNext(new String[]{"Total Images Generated", String.valueOf(totalImages)});
            writer.writeNext(new String[]{"Total Commands Used", String.valueOf(totalCommands)});
            writer.writeNext(new String[]{"Total New Users", String.valueOf(totalNewUsers)});
            writer.writeNext(new String[]{"Total Coins Earned", String.valueOf(totalCoinsEarned)});
            writer.writeNext(new String[]{"Total Coins Spent", String.valueOf(totalCoinsSpent)});
            writer.writeNext(new String[]{"Total Quests Completed", String.valueOf(totalQuests)});
            writer.writeNext(new String[]{"Total NSFW Requests", String.valueOf(totalNsfwRequests)});
            writer.writeNext(new String[]{"Total SFW Requests", String.valueOf(totalSfwRequests)});
            writer.writeNext(new String[]{"User Growth", String.valueOf(userGrowth)});
            writer.writeNext(new String[]{"Avg Images per Hour", String.format("%.2f", avgImagesPerHour)});
            writer.writeNext(new String[]{"Avg Commands per Hour", String.format("%.2f", avgCommandsPerHour)});
            writer.writeNext(new String[]{"Avg Active Users per Hour", String.format("%.2f", avgActiveUsersPerHour)});
            writer.writeNext(new String[]{"Avg Unique Users per Hour", String.format("%.2f", avgUniqueUsersPerHour)});
            
            if (peakImagesHour != null) {
                writer.writeNext(new String[]{"Peak Images Hour", peakImagesHour.hour + " (" + peakImagesHour.imagesGenerated + " images)"});
            }
            if (peakUsersHour != null) {
                writer.writeNext(new String[]{"Peak Users Hour", peakUsersHour.hour + " (" + peakUsersHour.uniqueUsers + " users)"});
            }
            
            // NSFW vs SFW ratio
            long totalRequests = totalNsfwRequests + totalSfwRequests;
            if (totalRequests > 0) {
                double nsfwPercentage = (double) totalNsfwRequests / totalRequests * 100;
                writer.writeNext(new String[]{"NSFW Percentage", String.format("%.2f%%", nsfwPercentage)});
            }
        }
    }
    
    /**
     * Generate source usage report
     */
    private static void generateSourceUsageReport(String yearMonth) throws IOException, CsvValidationException {
        Path inputFile = Paths.get(STATS_DIR, "source_usage_" + yearMonth + ".csv");
        if (!Files.exists(inputFile)) {
            logger.warn("No source usage file found for: {}", yearMonth);
            return;
        }
        
        Map<String, Long> sourceUsageTotals = new HashMap<>();
        
        try (CSVReader reader = new CSVReader(new FileReader(inputFile.toFile()))) {
            String[] header = reader.readNext(); // Skip header
            String[] row;
            
            while ((row = reader.readNext()) != null) {
                SourceUsageStats stats = new SourceUsageStats(row);
                sourceUsageTotals.merge(stats.source, stats.usageCount, Long::sum);
            }
        }
        
        // Sort by usage count (descending)
        List<Map.Entry<String, Long>> sortedSources = sourceUsageTotals.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toList());
        
        // Write report
        Path reportFile = Paths.get(REPORTS_DIR, "source_usage_report_" + yearMonth + ".csv");
        try (CSVWriter writer = new CSVWriter(new FileWriter(reportFile.toFile()))) {
            writer.writeNext(new String[]{"Source", "Total Usage", "Percentage"});
            
            long totalUsage = sortedSources.stream().mapToLong(Map.Entry::getValue).sum();
            
            for (Map.Entry<String, Long> entry : sortedSources) {
                double percentage = totalUsage > 0 ? (double) entry.getValue() / totalUsage * 100 : 0;
                writer.writeNext(new String[]{
                    entry.getKey(),
                    String.valueOf(entry.getValue()),
                    String.format("%.2f%%", percentage)
                });
            }
        }
    }
    
    /**
     * Generate command usage report
     */
    private static void generateCommandUsageReport(String yearMonth) throws IOException, CsvValidationException {
        Path inputFile = Paths.get(STATS_DIR, "command_usage_" + yearMonth + ".csv");
        if (!Files.exists(inputFile)) {
            logger.warn("No command usage file found for: {}", yearMonth);
            return;
        }
        
        Map<String, Long> commandUsageTotals = new HashMap<>();
        
        try (CSVReader reader = new CSVReader(new FileReader(inputFile.toFile()))) {
            String[] header = reader.readNext(); // Skip header
            String[] row;
            
            while ((row = reader.readNext()) != null) {
                CommandUsageStats stats = new CommandUsageStats(row);
                commandUsageTotals.merge(stats.command, stats.usageCount, Long::sum);
            }
        }
        
        // Sort by usage count (descending)
        List<Map.Entry<String, Long>> sortedCommands = commandUsageTotals.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toList());
        
        // Write report
        Path reportFile = Paths.get(REPORTS_DIR, "command_usage_report_" + yearMonth + ".csv");
        try (CSVWriter writer = new CSVWriter(new FileWriter(reportFile.toFile()))) {
            writer.writeNext(new String[]{"Command", "Total Usage", "Percentage"});
            
            long totalUsage = sortedCommands.stream().mapToLong(Map.Entry::getValue).sum();
            
            for (Map.Entry<String, Long> entry : sortedCommands) {
                double percentage = totalUsage > 0 ? (double) entry.getValue() / totalUsage * 100 : 0;
                writer.writeNext(new String[]{
                    entry.getKey(),
                    String.valueOf(entry.getValue()),
                    String.format("%.2f%%", percentage)
                });
            }
        }
    }
    
    /**
     * Generate peak hours analysis report
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

        // Calculate averages for each hour of day
        Path reportFile = Paths.get(REPORTS_DIR, "peak_hours_report_" + yearMonth + ".csv");
        try (CSVWriter writer = new CSVWriter(new FileWriter(reportFile.toFile()))) {
            writer.writeNext(new String[]{
                "Hour_of_Day", "Avg_Images", "Avg_Commands", "Avg_Active_Users", 
                "Avg_Unique_Users", "Avg_NSFW_Requests", "Avg_SFW_Requests"
            });
            
            for (int hour = 0; hour < 24; hour++) {
                List<HourlyStats> hourData = hourlyGroups.getOrDefault(hour, new ArrayList<>());
                
                if (!hourData.isEmpty()) {
                    double avgImages = hourData.stream().mapToLong(h -> h.imagesGenerated).average().orElse(0);
                    double avgCommands = hourData.stream().mapToLong(h -> h.commandsUsed).average().orElse(0);
                    double avgActiveUsers = hourData.stream().mapToLong(h -> h.activeUsers).average().orElse(0);
                    double avgUniqueUsers = hourData.stream().mapToLong(h -> h.uniqueUsers).average().orElse(0);
                    double avgNsfw = hourData.stream().mapToLong(h -> h.nsfwRequests).average().orElse(0);
                    double avgSfw = hourData.stream().mapToLong(h -> h.sfwRequests).average().orElse(0);
                    
                    writer.writeNext(new String[]{
                        String.format("%02d:00", hour),
                        String.format("%.2f", avgImages),
                        String.format("%.2f", avgCommands),
                        String.format("%.2f", avgActiveUsers),
                        String.format("%.2f", avgUniqueUsers),
                        String.format("%.2f", avgNsfw),
                        String.format("%.2f", avgSfw)
                    });
                }
            }
        }
    }
    
    /**
     * Generate user growth report
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
        
        // Group by day and calculate daily growth
        Map<String, List<HourlyStats>> dailyGroups = hourlyData.stream()
                .collect(Collectors.groupingBy(h -> h.hour.substring(0, 10))); // Extract date part
        
        Path reportFile = Paths.get(REPORTS_DIR, "user_growth_report_" + yearMonth + ".csv");
        try (CSVWriter writer = new CSVWriter(new FileWriter(reportFile.toFile()))) {
            writer.writeNext(new String[]{
                "Date", "New_Users", "Total_Users_End_of_Day", "Daily_Growth_Rate", 
                "Total_Images_End_of_Day", "Avg_Images_Per_User"
            });
            
            for (Map.Entry<String, List<HourlyStats>> entry : dailyGroups.entrySet()) {
                String date = entry.getKey();
                List<HourlyStats> dayData = entry.getValue();
                
                // Sort by hour to get end of day stats
                dayData.sort(Comparator.comparing(h -> h.hour));
                
                long dailyNewUsers = dayData.stream().mapToLong(h -> h.newUsers).sum();
                HourlyStats endOfDay = dayData.get(dayData.size() - 1);
                
                double growthRate = endOfDay.totalUsersInDb > 0 ? 
                    (double) dailyNewUsers / endOfDay.totalUsersInDb * 100 : 0;
                
                double avgImagesPerUser = endOfDay.totalUsersInDb > 0 ? 
                    (double) endOfDay.totalImagesInDb / endOfDay.totalUsersInDb : 0;
                
                writer.writeNext(new String[]{
                    date,
                    String.valueOf(dailyNewUsers),
                    String.valueOf(endOfDay.totalUsersInDb),
                    String.format("%.4f%%", growthRate),
                    String.valueOf(endOfDay.totalImagesInDb),
                    String.format("%.2f", avgImagesPerUser)
                });
            }
        }
    }
    
    /**
     * Generate report for current month
     */
    public static void generateCurrentMonthReport() throws IOException, CsvValidationException {
        String currentMonth = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        generateMonthlyReport(currentMonth);
    }
    
    /**
     * Generate report for previous month
     */
    public static void generatePreviousMonthReport() throws IOException, CsvValidationException {
        String previousMonth = LocalDateTime.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        generateMonthlyReport(previousMonth);
    }
}