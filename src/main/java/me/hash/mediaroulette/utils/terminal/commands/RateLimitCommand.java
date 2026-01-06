package me.hash.mediaroulette.utils.terminal.commands;

import me.hash.mediaroulette.utils.browser.RateLimiter;
import me.hash.mediaroulette.utils.terminal.Command;
import me.hash.mediaroulette.utils.terminal.CommandResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static me.hash.mediaroulette.utils.terminal.TerminalColors.*;

public class RateLimitCommand extends Command {

    public RateLimitCommand() {
        super("ratelimit", "Manage rate limits for API sources", "ratelimit <status|reset|trigger> [source] [duration]", List.of("rl"));
    }

    @Override
    public CommandResult execute(String[] args) {
        if (args.length < 1) {
            return CommandResult.error("Usage: " + getUsage());
        }

        String action = args[0].toLowerCase();

        return switch (action) {
            case "status", "s" -> showRateLimitStatus();
            case "reset", "r" -> {
                if (args.length < 2) yield CommandResult.error("Usage: ratelimit reset <source>");
                yield resetRateLimit(args[1]);
            }
            case "trigger", "t" -> {
                if (args.length < 3) yield CommandResult.error("Usage: ratelimit trigger <source> <duration_seconds>");
                try {
                    int duration = Integer.parseInt(args[2]);
                    yield triggerRateLimit(args[1], duration);
                } catch (NumberFormatException e) {
                    yield CommandResult.error("Invalid duration: " + args[2]);
                }
            }
            default -> CommandResult.error("Unknown action: " + action + 
                    "\nAvailable actions: status, reset, trigger");
        };
    }

    @Override
    public List<String> getCompletions(String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> actions = List.of("status", "reset", "trigger");
            for (String action : actions) {
                if (action.startsWith(partial)) {
                    completions.add(action);
                }
            }
        } else if (args.length == 2 && List.of("reset", "r", "trigger", "t").contains(args[0].toLowerCase())) {
            String partial = args[1].toLowerCase();
            // Get actual sources from rate limiter
            List<String> sources = getAvailableSources();
            for (String source : sources) {
                if (source.toLowerCase().startsWith(partial)) {
                    completions.add(source);
                }
            }
        } else if (args.length == 3 && List.of("trigger", "t").contains(args[0].toLowerCase())) {
            // Common duration suggestions
            completions.addAll(List.of("60", "300", "600", "1800", "3600"));
        }

        return completions;
    }

    /**
     * Get available source names for completion.
     */
    private List<String> getAvailableSources() {
        try {
            ConcurrentHashMap<String, String> status = RateLimiter.getAllRateLimitStatus();
            return new ArrayList<>(status.keySet());
        } catch (Exception e) {
            // Fallback to common sources
            return List.of("reddit", "4chan", "tenor", "google", "youtube", "tmdb", "imgur", "rule34", "picsum", "urban");
        }
    }

    private CommandResult showRateLimitStatus() {
        try {
            ConcurrentHashMap<String, String> status = RateLimiter.getAllRateLimitStatus();
            
            StringBuilder result = new StringBuilder();
            result.append(header("Rate Limit Status")).append("\n");
            result.append(dim("─".repeat(50))).append("\n\n");
            
            if (status.isEmpty()) {
                result.append(dim("  No rate limits currently tracked."));
                return CommandResult.success(result.toString());
            }
            
            // Sort by name
            List<String> sources = status.keySet().stream().sorted().collect(Collectors.toList());
            
            for (String source : sources) {
                String statusText = status.get(source);
                int limit = RateLimiter.getRateLimit(source);
                
                boolean isLimited = statusText.contains("LIMITED");
                String indicator = isLimited ? red("●") : green("●");
                String statusDisplay = isLimited ? red("RATE LIMITED") : green("OK");
                
                result.append("  ").append(indicator).append(" ");
                result.append(bold(String.format("%-12s", source.toUpperCase())));
                result.append("  ").append(statusDisplay);
                result.append(dim(" (" + limit + "/min)")).append("\n");
            }
            
            result.append("\n").append(dim("Legend: ")).append(green("●")).append(dim(" OK  "));
            result.append(red("●")).append(dim(" Rate Limited"));
            
            return CommandResult.success(result.toString());
        } catch (Exception e) {
            return CommandResult.error("Failed to get rate limit status: " + e.getMessage());
        }
    }

    private CommandResult resetRateLimit(String source) {
        try {
            RateLimiter.resetRateLimit(source);
            return CommandResult.success(green("✓") + " Rate limit reset for: " + bold(source.toUpperCase()));
        } catch (Exception e) {
            return CommandResult.error("Failed to reset rate limit for " + source + ": " + e.getMessage());
        }
    }

    private CommandResult triggerRateLimit(String source, int durationSeconds) {
        try {
            RateLimiter.triggerRateLimit(source, durationSeconds);
            return CommandResult.success(yellow("⚠") + " Manual rate limit triggered for " + 
                    bold(source.toUpperCase()) + " for " + cyan(durationSeconds + "s"));
        } catch (Exception e) {
            return CommandResult.error("Failed to trigger rate limit for " + source + ": " + e.getMessage());
        }
    }

    @Override
    public String getDetailedHelp() {
        StringBuilder help = new StringBuilder();
        
        help.append(header("Command: ")).append(command("ratelimit")).append("\n");
        help.append("Manage rate limits for external API sources.\n\n");
        
        help.append(header("Subcommands:")).append("\n");
        help.append("  ").append(cyan("status")).append("                    - Show all rate limit statuses\n");
        help.append("  ").append(cyan("reset <source>")).append("            - Clear rate limit for a source\n");
        help.append("  ").append(cyan("trigger <source> <secs>")).append("   - Manually trigger rate limit\n\n");
        
        help.append(header("Sources:")).append("\n");
        help.append("  reddit, 4chan, tenor, google, youtube, tmdb, imgur, etc.\n\n");
        
        help.append(header("Examples:")).append("\n");
        help.append("  ").append(dim("ratelimit status")).append("          - View all sources\n");
        help.append("  ").append(dim("ratelimit reset reddit")).append("    - Clear Reddit limit\n");
        help.append("  ").append(dim("ratelimit trigger imgur 300")).append(" - Block Imgur for 5 min\n\n");
        
        help.append(header("Common Durations:")).append("\n");
        help.append("  60 = 1 min, 300 = 5 min, 600 = 10 min, 3600 = 1 hour\n\n");
        
        help.append(header("Aliases:")).append("\n");
        help.append("  rl\n");
        
        return help.toString();
    }
}