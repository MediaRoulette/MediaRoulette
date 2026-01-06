package me.hash.mediaroulette.utils.terminal.commands;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.utils.terminal.Command;
import me.hash.mediaroulette.utils.terminal.CommandResult;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static me.hash.mediaroulette.utils.terminal.TerminalColors.*;

public class UserCommand extends Command {

    public UserCommand() {
        super("user", "Manage users", "user <userId> <action> [value]", List.of("u"));
    }

    @Override
    public CommandResult execute(String[] args) {
        if (args.length < 2) {
            return CommandResult.error("Usage: " + getUsage());
        }

        try {
            long userId = Long.parseLong(args[0]);
            String action = args[1].toLowerCase();

            return switch (action) {
                case "info", "i" -> getUserInfo(userId);
                case "setadmin" -> {
                    if (args.length < 3) yield CommandResult.error("Usage: user <userId> setadmin <true|false>");
                    boolean isAdmin = Boolean.parseBoolean(args[2]);
                    yield setUserAdmin(userId, isAdmin);
                }
                case "setpremium" -> {
                    if (args.length < 3) yield CommandResult.error("Usage: user <userId> setpremium <true|false>");
                    boolean isPremium = Boolean.parseBoolean(args[2]);
                    yield setUserPremium(userId, isPremium);
                }
                default -> CommandResult.error("Unknown action: " + action +
                        "\nAvailable actions: info, setadmin, setpremium");
            };
        } catch (NumberFormatException e) {
            return CommandResult.error("Invalid user ID: " + args[0]);
        }
    }

    @Override
    public List<String> getCompletions(String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Complete with recent user IDs
            String partial = args[0];
            try {
                List<String> recentUsers = getRecentUserIds();
                for (String userId : recentUsers) {
                    if (userId.startsWith(partial)) {
                        completions.add(userId);
                    }
                }
            } catch (Exception e) {
                // Ignore errors in completion
            }
        } else if (args.length == 2) {
            // Complete actions
            String partial = args[1].toLowerCase();
            List<String> actions = List.of("info", "setadmin", "setpremium");
            for (String action : actions) {
                if (action.startsWith(partial)) {
                    completions.add(action);
                }
            }
        } else if (args.length == 3 && ("setadmin".equalsIgnoreCase(args[1]) || "setpremium".equalsIgnoreCase(args[1]))) {
            // Complete boolean values
            String partial = args[2].toLowerCase();
            List<String> booleans = List.of("true", "false");
            for (String bool : booleans) {
                if (bool.startsWith(partial)) {
                    completions.add(bool);
                }
            }
        }

        return completions;
    }

    /**
     * Get recent user IDs for tab completion.
     */
    private List<String> getRecentUserIds() {
        try {
            if (Main.getUserService() == null) {
                return List.of();
            }
            // Get recent users (limit to 50 for performance)
            return Main.getUserService().getRecentUserIds(50);
        } catch (Exception e) {
            return List.of();
        }
    }

    private CommandResult setUserAdmin(long userId, boolean isAdmin) {
        try {
            User user = Main.getUserService().getOrCreateUser(String.valueOf(userId));
            if (user == null) {
                return CommandResult.error("User not found: " + userId);
            }

            user.setAdmin(isAdmin);
            Main.getUserService().updateUser(user);

            return CommandResult.success(green("✓") + " User " + bold(String.valueOf(userId)) + 
                    " admin status set to: " + (isAdmin ? green("true") : red("false")));
        } catch (Exception e) {
            return CommandResult.error("Failed to set admin status: " + e.getMessage());
        }
    }

    private CommandResult setUserPremium(long userId, boolean isPremium) {
        try {
            User user = Main.getUserService().getOrCreateUser(String.valueOf(userId));
            if (user == null) {
                return CommandResult.error("User not found: " + userId);
            }

            user.setPremium(isPremium);
            Main.getUserService().updateUser(user);

            return CommandResult.success(green("✓") + " User " + bold(String.valueOf(userId)) + 
                    " premium status set to: " + (isPremium ? green("true") : red("false")));
        } catch (Exception e) {
            return CommandResult.error("Failed to set premium status: " + e.getMessage());
        }
    }

    private CommandResult getUserInfo(long userId) {
        try {
            User user = Main.getUserService().getOrCreateUser(String.valueOf(userId));
            if (user == null) {
                return CommandResult.error("User not found: " + userId);
            }

            StringBuilder info = new StringBuilder();
            info.append(header("User Information")).append("\n");
            info.append(dim("─".repeat(40))).append("\n\n");
            
            info.append("  ").append(bold("ID:")).append("              ").append(cyan(user.getUserId())).append("\n");
            info.append("  ").append(bold("Admin:")).append("           ");
            info.append(user.isAdmin() ? green("Yes") : dim("No")).append("\n");
            info.append("  ").append(bold("Premium:")).append("         ");
            info.append(user.isPremium() ? green("Yes") : dim("No")).append("\n");
            info.append("  ").append(bold("Images Generated:")).append(" ").append(cyan(String.format("%,d", user.getImagesGenerated()))).append("\n");

            return CommandResult.success(info.toString());
        } catch (Exception e) {
            return CommandResult.error("Failed to get user info: " + e.getMessage());
        }
    }

    @Override
    public String getDetailedHelp() {
        StringBuilder help = new StringBuilder();
        
        help.append(header("Command: ")).append(command("user")).append("\n");
        help.append("Manage user accounts and permissions.\n\n");
        
        help.append(header("Subcommands:")).append("\n");
        help.append("  ").append(cyan("info <userId>")).append("              - Show user information\n");
        help.append("  ").append(cyan("setadmin <userId> <bool>")).append("   - Set admin status\n");
        help.append("  ").append(cyan("setpremium <userId> <bool>")).append(" - Set premium status\n\n");
        
        help.append(header("Examples:")).append("\n");
        help.append("  ").append(dim("user 123456789 info")).append("         - Show user details\n");
        help.append("  ").append(dim("user 123456789 setadmin true")).append("  - Grant admin\n");
        help.append("  ").append(dim("user 123456789 setpremium true")).append(" - Grant premium\n\n");
        
        help.append(header("Tab Completion:")).append("\n");
        help.append("  Press Tab to complete recently active user IDs.\n\n");
        
        help.append(header("Aliases:")).append("\n");
        help.append("  u\n");
        
        return help.toString();
    }
}
