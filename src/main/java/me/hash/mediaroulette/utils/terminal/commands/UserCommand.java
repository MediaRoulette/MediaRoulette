package me.hash.mediaroulette.utils.terminal.commands;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.utils.terminal.Command;
import me.hash.mediaroulette.utils.terminal.CommandResult;

import java.util.ArrayList;
import java.util.List;

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

            switch (action) {
                case "setadmin":
                    if (args.length < 3) {
                        return CommandResult.error("Usage: user <userId> setadmin <true|false>");
                    }
                    boolean isAdmin = Boolean.parseBoolean(args[2]);
                    return setUserAdmin(userId, isAdmin);

                case "setpremium":
                    if (args.length < 3) {
                        return CommandResult.error("Usage: user <userId> setpremium <true|false>");
                    }
                    boolean isPremium = Boolean.parseBoolean(args[2]);
                    return setUserPremium(userId, isPremium);

                case "info":
                    return getUserInfo(userId);

                default:
                    return CommandResult.error("Unknown action: " + action +
                            "\nAvailable actions: setadmin, setpremium, info");
            }
        } catch (NumberFormatException e) {
            return CommandResult.error("Invalid user ID: " + args[0]);
        }
    }

    @Override
    public List<String> getCompletions(String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Auto-complete user IDs (this would require a method to get user IDs)
            // For now, we'll just return a placeholder
            completions.add("<userId>");
        } else if (args.length == 2) {
            // Auto-complete actions
            String partial = args[1].toLowerCase();
            List<String> actions = List.of("setadmin", "setpremium", "info");
            for (String action : actions) {
                if (action.startsWith(partial)) {
                    completions.add(action);
                }
            }
        } else if (args.length == 3 && ("setadmin".equalsIgnoreCase(args[1]) || "setpremium".equalsIgnoreCase(args[1]))) {
            // Auto-complete boolean values for setadmin and setpremium
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

    private CommandResult setUserAdmin(long userId, boolean isAdmin) {
        try {
            User user = Main.getUserService().getOrCreateUser(String.valueOf(userId));
            if (user == null) {
                return CommandResult.error("User not found: " + userId);
            }

            user.setAdmin(isAdmin);
            Main.getUserService().updateUser(user);

            return CommandResult.success("User " + userId + " admin status set to: " + isAdmin);
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

            return CommandResult.success("User " + userId + " premium status set to: " + isPremium);
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
            info.append("User Information:\n");
            info.append("  ID: ").append(user.getUserId()).append("\n");
            info.append("  Admin: ").append(user.isAdmin()).append("\n");
            info.append("  Premium: ").append(user.isPremium()).append("\n");
            info.append("  Images Generated: ").append(user.getImagesGenerated()).append("\n");
            // Add more user properties as needed

            return CommandResult.success(info.toString());
        } catch (Exception e) {
            return CommandResult.error("Failed to get user info: " + e.getMessage());
        }
    }
}
