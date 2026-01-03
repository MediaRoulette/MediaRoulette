package me.hash.mediaroulette.utils.terminal;

import static me.hash.mediaroulette.utils.terminal.TerminalColors.*;

/**
 * Fancy reusable progress bar with rich colors and animations.
 * Integrates with JLine terminal and uses TerminalColors for consistent styling.
 * 
 * Features:
 * - Gradient color progress (purple → cyan → green)
 * - Animated spinner for indeterminate tasks
 * - Speed display (MB/s, KB/s)
 * - ETA calculation
 * - Smooth progress characters
 * 
 * Usage:
 *   try (ProgressBar bar = ProgressBar.create("Downloading resources")
 *           .withTotal(fileSize)
 *           .withStyle(Style.DOWNLOAD)
 *           .start()) {
 *       bar.stepTo(bytesRead);
 *   }
 */
public class ProgressBar implements AutoCloseable {
    
    // Progress bar characters
    private static final String BLOCK_FULL = "█";
    private static final String BLOCK_EMPTY = "░";
    private static final String[] GRADIENT_BLOCKS = {"▏", "▎", "▍", "▌", "▋", "▊", "▉", "█"};
    
    // Animated spinners
    private static final String[] SPINNER_DOTS = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private static final String[] SPINNER_BOUNCE = {"⣾", "⣽", "⣻", "⢿", "⡿", "⣟", "⣯", "⣷"};
    
    // Box drawing
    private static final String BOX_LEFT = "▐";
    private static final String BOX_RIGHT = "▌";
    
    public enum Style {
        SIMPLE,    // Basic [████░░░░]
        FANCY,     // Gradient with smooth edges
        MINIMAL,   // Just percentage and spinner
        DOWNLOAD   // Optimized for file downloads with speed
    }
    
    private final String taskName;
    private final long total;
    private final int barWidth;
    private final Style style;
    private final boolean indeterminate;
    
    private long current;
    private long lastUpdate;
    private long lastBytes;
    private double speed;
    private volatile boolean running;
    private long startTime;
    private String extraInfo;
    
    private ProgressBar(Builder builder) {
        this.taskName = builder.taskName;
        this.total = builder.total;
        this.barWidth = builder.barWidth;
        this.style = builder.style;
        this.indeterminate = builder.total <= 0;
    }
    
    public static Builder create(String taskName) {
        return new Builder(taskName);
    }
    
    public ProgressBar start() {
        this.running = true;
        this.startTime = System.currentTimeMillis();
        this.lastUpdate = startTime;
        this.lastBytes = 0;
        render();
        return this;
    }
    
    public void stepTo(long value) {
        long now = System.currentTimeMillis();
        long deltaTime = now - lastUpdate;
        
        // Calculate speed every 200ms for smooth updates
        if (deltaTime >= 200) {
            long deltaBytes = value - lastBytes;
            speed = (deltaBytes * 1000.0) / deltaTime;
            lastBytes = value;
            lastUpdate = now;
        }
        
        this.current = Math.min(value, total);
        render();
    }
    
    public void stepBy(long delta) {
        stepTo(current + delta);
    }
    
    public void setExtraInfo(String info) {
        this.extraInfo = info;
        render();
    }
    
    private void render() {
        if (!running) return;
        
        StringBuilder sb = new StringBuilder();
        sb.append("\r");
        
        switch (style) {
            case FANCY -> renderFancy(sb);
            case DOWNLOAD -> renderDownload(sb);
            case MINIMAL -> renderMinimal(sb);
            default -> renderSimple(sb);
        }
        
        sb.append("\u001B[K"); // Clear rest of line
        print(sb.toString());
    }
    
    private void renderFancy(StringBuilder sb) {
        sb.append(BOLD).append(BRIGHT_CYAN).append(taskName).append(RESET).append(" ");
        
        if (indeterminate) {
            renderSpinner(sb, SPINNER_BOUNCE);
            if (extraInfo != null) {
                sb.append(DIM).append(extraInfo).append(RESET);
            }
            return;
        }
        
        double progress = (double) current / total;
        
        sb.append(BRIGHT_BLACK).append(BOX_LEFT).append(RESET);
        
        double fillExact = barWidth * progress;
        int fullBlocks = (int) fillExact;
        double remainder = fillExact - fullBlocks;
        
        for (int i = 0; i < barWidth; i++) {
            String blockColor = getGradientColor(i, barWidth, progress);
            
            if (i < fullBlocks) {
                sb.append(blockColor).append(BLOCK_FULL);
            } else if (i == fullBlocks && remainder > 0) {
                int gradientIdx = (int) (remainder * (GRADIENT_BLOCKS.length - 1));
                sb.append(blockColor).append(GRADIENT_BLOCKS[gradientIdx]);
            } else {
                sb.append(BRIGHT_BLACK).append(BLOCK_EMPTY);
            }
        }
        sb.append(RESET);
        
        sb.append(BRIGHT_BLACK).append(BOX_RIGHT).append(RESET).append(" ");
        
        String pctColor = progress < 0.3 ? YELLOW : progress < 0.7 ? CYAN : GREEN;
        sb.append(pctColor).append(BOLD);
        sb.append(String.format("%5.1f%%", progress * 100));
        sb.append(RESET);
        
        appendSpeedAndEta(sb, progress);
    }
    
    private void renderDownload(StringBuilder sb) {
        sb.append(BRIGHT_CYAN).append("↓ ").append(BOLD).append(taskName).append(RESET).append(" ");
        
        if (indeterminate) {
            renderSpinner(sb, SPINNER_DOTS);
            return;
        }
        
        double progress = (double) current / total;
        
        sb.append(BRIGHT_BLACK).append("[").append(RESET);
        
        int filled = (int) (barWidth * progress);
        for (int i = 0; i < barWidth; i++) {
            if (i < filled) {
                if (i < barWidth * 0.33) {
                    sb.append(PURPLE);
                } else if (i < barWidth * 0.66) {
                    sb.append(CYAN);
                } else {
                    sb.append(GREEN);
                }
                sb.append(BLOCK_FULL);
            } else if (i == filled) {
                sb.append(BRIGHT_CYAN).append("▸");
            } else {
                sb.append(BRIGHT_BLACK).append("─");
            }
        }
        sb.append(RESET).append(BRIGHT_BLACK).append("]").append(RESET).append(" ");
        
        sb.append(DIM).append(formatBytes(current)).append(RESET);
        sb.append(BRIGHT_BLACK).append("/").append(RESET);
        sb.append(formatBytes(total)).append(" ");
        
        if (speed > 0) {
            sb.append(BRIGHT_YELLOW).append(formatSpeed(speed)).append(RESET).append(" ");
        }
        
        if (progress > 0 && progress < 1.0) {
            long elapsed = System.currentTimeMillis() - startTime;
            long eta = (long) (elapsed / progress - elapsed);
            sb.append(DIM).append("ETA ").append(formatDuration(eta)).append(RESET);
        }
    }
    
    private void renderSimple(StringBuilder sb) {
        sb.append(CYAN).append(taskName).append(RESET).append(" ");
        
        if (indeterminate) {
            renderSpinner(sb, SPINNER_DOTS);
            return;
        }
        
        double progress = (double) current / total;
        int filled = (int) (barWidth * progress);
        
        sb.append("[");
        sb.append(GREEN);
        for (int i = 0; i < barWidth; i++) {
            sb.append(i < filled ? BLOCK_FULL : BLOCK_EMPTY);
        }
        sb.append(RESET).append("] ");
        sb.append(String.format("%5.1f%%", progress * 100));
    }
    
    private void renderMinimal(StringBuilder sb) {
        if (indeterminate) {
            renderSpinner(sb, SPINNER_DOTS);
            sb.append(" ").append(taskName);
            return;
        }
        
        double progress = (double) current / total;
        String color = progress < 0.5 ? YELLOW : GREEN;
        sb.append(color).append(String.format("%3.0f%%", progress * 100)).append(RESET);
        sb.append(" ").append(taskName);
    }
    
    private void renderSpinner(StringBuilder sb, String[] spinnerFrames) {
        int idx = (int) ((System.currentTimeMillis() / 80) % spinnerFrames.length);
        sb.append(BRIGHT_CYAN).append(spinnerFrames[idx]).append(RESET).append(" ");
    }
    
    private String getGradientColor(int position, int total, double progress) {
        double posRatio = (double) position / total;
        if (posRatio > progress) return BRIGHT_BLACK;
        
        if (posRatio < 0.25) return PURPLE;
        if (posRatio < 0.5) return BLUE;
        if (posRatio < 0.75) return CYAN;
        return GREEN;
    }
    
    private void appendSpeedAndEta(StringBuilder sb, double progress) {
        if (speed > 0) {
            sb.append(" ").append(DIM).append(formatSpeed(speed)).append(RESET);
        }
        
        if (progress > 0 && progress < 1.0) {
            long elapsed = System.currentTimeMillis() - startTime;
            long eta = (long) (elapsed / progress - elapsed);
            sb.append(" ").append(BRIGHT_BLACK).append("ETA ").append(formatDuration(eta)).append(RESET);
        }
    }
    
    public void complete(String message) {
        this.running = false;
        StringBuilder sb = new StringBuilder();
        sb.append("\r");
        sb.append(BRIGHT_GREEN).append("✓ ").append(RESET);
        sb.append(BOLD).append(taskName).append(RESET);
        if (message != null) {
            sb.append(DIM).append(" — ").append(message).append(RESET);
        }
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed > 1000) {
            sb.append(BRIGHT_BLACK).append(" (").append(formatDuration(elapsed)).append(")").append(RESET);
        }
        sb.append("\u001B[K\n");
        print(sb.toString());
    }
    
    public void fail(String message) {
        this.running = false;
        StringBuilder sb = new StringBuilder();
        sb.append("\r");
        sb.append(BRIGHT_RED).append("✗ ").append(RESET);
        sb.append(BOLD).append(taskName).append(RESET);
        if (message != null) {
            sb.append(RED).append(" — ").append(message).append(RESET);
        }
        sb.append("\u001B[K\n");
        print(sb.toString());
    }
    
    public void skip(String reason) {
        this.running = false;
        StringBuilder sb = new StringBuilder();
        sb.append("\r");
        sb.append(YELLOW).append("○ ").append(RESET);
        sb.append(DIM).append(taskName);
        if (reason != null) {
            sb.append(" — ").append(reason);
        }
        sb.append(RESET).append("\u001B[K\n");
        print(sb.toString());
    }
    
    private void print(String text) {
        TerminalInterface terminal = TerminalInterface.getInstance();
        if (terminal != null && terminal.getTerminal() != null) {
            terminal.getTerminal().writer().print(text);
            terminal.getTerminal().writer().flush();
        } else {
            System.out.print(text);
            System.out.flush();
        }
    }
    
    @Override
    public void close() {
        if (running) {
            complete(null);
        }
    }
    
    // Formatting helpers
    
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    public static String formatSpeed(double bytesPerSecond) {
        if (bytesPerSecond < 1024) return String.format("%.0f B/s", bytesPerSecond);
        if (bytesPerSecond < 1024 * 1024) return String.format("%.1f KB/s", bytesPerSecond / 1024);
        return String.format("%.1f MB/s", bytesPerSecond / (1024 * 1024));
    }
    
    public static String formatDuration(long millis) {
        if (millis < 1000) return "<1s";
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes < 60) return minutes + "m " + seconds + "s";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }
    
    // Builder
    
    public static class Builder {
        private final String taskName;
        private long total = -1;
        private int barWidth = 25;
        private Style style = Style.FANCY;
        
        private Builder(String taskName) {
            this.taskName = taskName;
        }
        
        public Builder withTotal(long total) {
            this.total = total;
            return this;
        }
        
        public Builder withBarWidth(int width) {
            this.barWidth = width;
            return this;
        }
        
        public Builder withStyle(Style style) {
            this.style = style;
            return this;
        }
        
        public ProgressBar start() {
            return new ProgressBar(this).start();
        }
    }
}
