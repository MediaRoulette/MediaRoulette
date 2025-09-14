package me.hash.mediaroulette.bot.utils;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

public class CooldownManager extends ListenerAdapter {

    private final Map<String, Long> activeCooldowns = new ConcurrentHashMap<>();
    private final Map<String, Integer> commandCooldownConfig = new ConcurrentHashMap<>();
    private final List<String> registeredListeners = new ArrayList<>();

    /**
     * Register a command listener and scan it for cooldown annotations
     */
    public <T> void registerListener(T listener) {
        String listenerName = listener.getClass().getSimpleName();
        registeredListeners.add(listenerName);

        scanClassAnnotation(listener);
        scanMethodAnnotations(listener);
    }

    /**
     * Scan for class-level @CommandCooldown annotations
     */
    private void scanClassAnnotation(Object listener) {
        Class<?> clazz = listener.getClass();

        if (clazz.isAnnotationPresent(CommandCooldown.class)) {
            CommandCooldown annotation = clazz.getAnnotation(CommandCooldown.class);

            String[] commands = annotation.commands();
            if (commands.length > 0) {
                for (String command : commands) {
                    commandCooldownConfig.put(command, annotation.value());
                }
            } else {
                String inferredCommand = inferCommandFromClassName(clazz.getSimpleName());
                commandCooldownConfig.put(inferredCommand, annotation.value());
            }
        }
    }

    /**
     * Scan for method-level @CommandCooldown annotations
     */
    private void scanMethodAnnotations(Object listener) {
        Class<?> clazz = listener.getClass();
        Method[] methods = clazz.getDeclaredMethods();

        for (Method method : methods) {
            if (method.isAnnotationPresent(CommandCooldown.class)) {
                CommandCooldown annotation = method.getAnnotation(CommandCooldown.class);

                String[] commands = annotation.commands();
                if (commands.length > 0) {
                    for (String command : commands) {
                        commandCooldownConfig.put(command, annotation.value());
                    }
                } else {
                    String inferredCommand = inferCommandFromClassName(clazz.getSimpleName());
                    commandCooldownConfig.put(inferredCommand, annotation.value());
                }
            }
        }
    }

    /**
     * Try to infer command name from class name
     */
    private String inferCommandFromClassName(String className) {
        String lowerName = className.toLowerCase();

        if (lowerName.endsWith("command")) {
            return lowerName.substring(0, lowerName.length() - 7);
        } else if (lowerName.endsWith("cmd")) {
            return lowerName.substring(0, lowerName.length() - 3);
        } else if (lowerName.endsWith("listener")) {
            return lowerName.substring(0, lowerName.length() - 8);
        }

        return lowerName;
    }

    /**
     * Main event handler - intercepts all slash commands and applies cooldowns
     */
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        String userId = event.getUser().getId();

        // check if this command has a cooldown configured
        Integer cooldownSeconds = commandCooldownConfig.get(commandName);
        if (cooldownSeconds == null) {
            return; // no cooldown for this command
        }

        // check if user is currently on cooldown
        String cooldownKey = userId + ":" + commandName;
        Long cooldownExpiry = activeCooldowns.get(cooldownKey);
        long currentTime = System.currentTimeMillis();

        if (cooldownExpiry != null && currentTime < cooldownExpiry) {
            // user is on cooldown; block this event
            long remainingSeconds = (cooldownExpiry - currentTime) / 1000;

            event.reply("⏰ **Cooldown Active**\n" +
                            "Please wait **" + remainingSeconds + " seconds** before using `/" + commandName + "` again.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        // set new cooldown
        activeCooldowns.put(cooldownKey, currentTime + (cooldownSeconds * 1000L));
        cleanupExpiredCooldowns();
    }

    /**
     * Remove expired cooldowns to prevent memory leaks
     */
    private void cleanupExpiredCooldowns() {
        long currentTime = System.currentTimeMillis();
        activeCooldowns.entrySet().removeIf(entry -> currentTime >= entry.getValue());
    }

    // === utility shit ===

    /**
     * Manually set a cooldown for a command
     */
    public void setCooldown(String commandName, int seconds) {
        commandCooldownConfig.put(commandName, seconds);
    }

    /**
     * Remove cooldown for a command
     */
    public void removeCooldown(String commandName) {
        commandCooldownConfig.remove(commandName);
    }

    /**
     * Clear a specific user's cooldown for a command
     */
    public void clearUserCooldown(String userId, String commandName) {
        String key = userId + ":" + commandName;
        activeCooldowns.remove(key);
    }

    /**
     * Clear all cooldowns for a user
     */
    public void clearAllUserCooldowns(String userId) {
        activeCooldowns.entrySet().removeIf(entry -> entry.getKey().startsWith(userId + ":"));
    }

    /**
     * Get remaining cooldown time for a user on a command
     */
    public long getRemainingCooldown(String userId, String commandName) {
        String key = userId + ":" + commandName;
        Long expiry = activeCooldowns.get(key);

        if (expiry == null) return 0;

        long remaining = (expiry - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    public void printCooldownInfo() {
        System.out.println("\n📊 COOLDOWN MANAGER STATUS:");
        System.out.println("Registered Listeners: " + registeredListeners);
        System.out.println("Command Cooldowns: " + commandCooldownConfig);
        System.out.println("Active Cooldowns: " + activeCooldowns.size());

        if (!activeCooldowns.isEmpty()) {
            System.out.println("Current Active Cooldowns:");
            long now = System.currentTimeMillis();
            activeCooldowns.forEach((key, expiry) -> {
                long remaining = Math.max(0, (expiry - now) / 1000);
                System.out.println("  " + key + " -> " + remaining + "s remaining");
            });
        }
        System.out.println();
    }
}