package me.hash.mediaroulette.bot.commands.admin;

import com.fasterxml.jackson.databind.ser.Serializers;
import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.bot.commands.BaseCommand;
import me.hash.mediaroulette.bot.utils.CommandCooldown;
import me.hash.mediaroulette.config.LocalConfig;
import me.hash.mediaroulette.bot.MediaContainerManager;
import me.hash.mediaroulette.bot.commands.CommandHandler;
import me.hash.mediaroulette.model.BotInventoryItem;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.service.BotInventoryService;
import me.hash.mediaroulette.service.GiveawayService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.modals.Modal;

import java.awt.Color;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AdminCommand extends BaseCommand {

    private static final Color PRIMARY_COLOR = new Color(88, 101, 242);
    private static final Color SUCCESS_COLOR = new Color(87, 242, 135);
    private static final Color ERROR_COLOR = new Color(220, 53, 69);
    private static final Color ADMIN_COLOR = new Color(255, 215, 0);

    private final BotInventoryService botInventoryService;
    private final GiveawayService giveawayService;
    private final ConcurrentHashMap<String, String> temporaryStorage;

    public AdminCommand() {
        this.botInventoryService = new BotInventoryService();
        this.giveawayService = new GiveawayService();
        this.temporaryStorage = new ConcurrentHashMap<>();
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash("admin", "Admin commands for bot management")
                .addSubcommands(
                        new SubcommandData("additem", "Add an item to bot inventory")
                                .addOption(OptionType.STRING, "type", "Item type", true)
                                .addOption(OptionType.STRING, "name", "Item name", true)
                                .addOption(OptionType.STRING, "description", "Item description", true)
                                .addOption(OptionType.STRING, "rarity", "Item rarity (common/rare/epic/legendary)", true)
                                .addOption(OptionType.STRING, "value", "Item value (for non-nitro items)", false)
                                .addOption(OptionType.INTEGER, "expires_days", "Days until expiration (for nitro)", false),
                        new SubcommandData("viewinventory", "View bot inventory"),
                        new SubcommandData("removeitem", "Remove an item from bot inventory")
                                .addOption(OptionType.STRING, "item_id", "Item ID to remove", true),
                        new SubcommandData("givecoins", "Give coins to a user")
                                .addOption(OptionType.USER, "user", "User to give coins to", true)
                                .addOption(OptionType.INTEGER, "amount", "Amount of coins", true)
                                .addOption(OptionType.STRING, "reason", "Reason for giving coins", false),
                        new SubcommandData("setpremium", "Set user premium status")
                                .addOption(OptionType.USER, "user", "User to modify", true)
                                .addOption(OptionType.BOOLEAN, "premium", "Premium status", true),
                        new SubcommandData("userlookup", "Look up detailed user information")
                                .addOption(OptionType.USER, "user", "Target user", true),
                        new SubcommandData("userstats", "View user usage statistics")
                                .addOption(OptionType.USER, "user", "Target user", true),
                        new SubcommandData("stats", "View comprehensive admin statistics"),
                        new SubcommandData("cleanup", "Clean up expired items and old data"),
                        new SubcommandData("maintenance", "Toggle maintenance mode")
                                .addOption(OptionType.BOOLEAN, "enabled", "Enable maintenance mode", true),
                        new SubcommandData("togglesource", "Toggle a source on/off")
                                .addOption(OptionType.STRING, "source", "Source to toggle", true, true)
                                .addOption(OptionType.BOOLEAN, "enabled", "Enable or disable the source", true),
                        new SubcommandData("listsources", "List all sources and their status")
                ).setIntegrationTypes(IntegrationType.GUILD_INSTALL);
    }

    @Override
    @CommandCooldown(value = 3, commands = {"admin"})
    public void handleCommand(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("admin")) return;

        User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
        // Check if user is admin
        if (!user.isAdmin()) {
            sendError(event, "Access Denied - You don't have permission to use admin commands.");
            return;
        }

        String subcommand = event.getSubcommandName();
        switch (subcommand) {
            case "additem" -> handleAddItem(event);
            case "viewinventory" -> handleViewInventory(event);
            case "removeitem" -> handleRemoveItem(event);
            case "givecoins" -> handleGiveCoins(event);
            case "setpremium" -> handleSetPremium(event);
            case "userlookup" -> handleUserLookup(event);
            case "userstats" -> handleUserStats(event);
            case "stats" -> handleStats(event);
            case "cleanup" -> handleCleanup(event);
            case "maintenance" -> handleMaintenance(event);
            case "togglesource" -> handleToggleSource(event);
            case "listsources" -> handleListSources(event);
            default -> sendError(event, "Unknown admin subcommand.");
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getModalId().startsWith("admin:addnitro:")) {
            handleNitroModal(event);
        }
    }

    private void handleAddItem(SlashCommandInteractionEvent event) {
        String type = event.getOption("type").getAsString();
        String name = event.getOption("name").getAsString();
        String description = event.getOption("description").getAsString();
        String rarity = event.getOption("rarity").getAsString();
        String value = event.getOption("value") != null ? event.getOption("value").getAsString() : null;
        Integer expiresDays = event.getOption("expires_days") != null ? event.getOption("expires_days").getAsInt() : null;

        if (!List.of("discord_nitro", "premium_voucher", "boost", "coins", "special").contains(type)) {
            sendError(event, "Invalid type. Use: discord_nitro, premium_voucher, boost, coins, special");
            return;
        }

        // Special handling for Discord Nitro - show modal for secure link input
        if ("discord_nitro".equals(type)) {
            String modalId = "admin:addnitro:" + System.currentTimeMillis();
            
            TextInput linkInput = TextInput.create("nitro_link", TextInputStyle.SHORT)
                    .setPlaceholder("https://discord.gift/...")
                    .setRequired(true)
                    .setMaxLength(200)
                    .build();

            Modal modal = Modal.create(modalId, "Add Discord Nitro Gift")
                    .addComponents(Label.of("Discord Nitro Gift Link", linkInput))
                    .build();

            // Store item data in temporary storage for retrieval
            String itemData = String.join("|", name, description, rarity, 
                String.valueOf(expiresDays != null ? expiresDays : 30));
            
            event.replyModal(modal).queue();
            temporaryStorage.put(modalId, itemData);
            return;
        }

        // Create regular item
        BotInventoryItem item = new BotInventoryItem();
        item.setName(name);
        item.setDescription(description);
        item.setType(type);
        item.setRarity(rarity);
        item.setValue(value != null ? value : "N/A");
        
        if (expiresDays != null) {
            item.setExpiresAt(Instant.now().plus(expiresDays, ChronoUnit.DAYS));
        }

        botInventoryService.addItem(item);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Item Added to Bot Inventory")
                .setDescription(String.format("**%s** has been added to the bot inventory!", name))
                .addField("Type", type, true)
                .addField("Rarity", rarity, true)
                .addField("ID", "`" + item.getId() + "`", true)
                .setColor(SUCCESS_COLOR)
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleNitroModal(ModalInteractionEvent event) {
        String modalId = event.getModalId();
        String itemData = temporaryStorage.get(modalId);
        
        if (itemData == null) {
            sendModalError(event, "Session expired. Please try again.");
            return;
        }

        String[] parts = itemData.split("\\|");
        String name = parts[0];
        String description = parts[1];
        String rarity = parts[2];
        int expiresDays = Integer.parseInt(parts[3]);

        String nitroLink = event.getValue("nitro_link").getAsString();
        
        if (!nitroLink.startsWith("https://discord.gift/")) {
            sendModalError(event, "Invalid Discord gift link format.");
            return;
        }

        BotInventoryItem item = new BotInventoryItem();
        item.setName(name);
        item.setDescription(description);
        item.setType("discord_nitro");
        item.setRarity(rarity);
        item.setValue(nitroLink);
        item.setExpiresAt(Instant.now().plus(expiresDays, ChronoUnit.DAYS));

        botInventoryService.addItem(item);
        temporaryStorage.remove(modalId);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Discord Nitro Added to Bot Inventory")
                .setDescription(String.format("**%s** has been securely added to the bot inventory!", name))
                .addField("Type", "Discord Nitro", true)
                .addField("Rarity", rarity, true)
                .addField("Expires", String.format("<t:%d:R>", item.getExpiresAt().getEpochSecond()), true)
                .addField("ID", "`" + item.getId() + "`", false)
                .setColor(SUCCESS_COLOR)
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleViewInventory(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        
        Main.getBot().getExecutor().submit(() -> {
            List<BotInventoryItem> items = botInventoryService.getAllItems();
            
            if (items.isEmpty()) {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("Bot Inventory")
                        .setDescription("The bot inventory is empty.")
                        .setColor(PRIMARY_COLOR)
                        .setTimestamp(Instant.now());
                event.getHook().sendMessageEmbeds(embed.build()).queue();
                return;
            }

            StringBuilder description = new StringBuilder();
            int activeCount = 0;
            int expiredCount = 0;
            
            for (BotInventoryItem item : items) {
                if (item.isExpired()) {
                    expiredCount++;
                    description.append(String.format("~~**%s** (%s) - `%s` [EXPIRED]~~\n", 
                        item.getName(), item.getRarity(), item.getId()));
                } else {
                    activeCount++;
                    description.append(String.format("**%s** (%s) - `%s`\n", 
                        item.getName(), item.getRarity(), item.getId()));
                }
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Bot Inventory")
                    .setDescription(description.toString())
                    .addField("Active Items", String.valueOf(activeCount), true)
                    .addField("Expired Items", String.valueOf(expiredCount), true)
                    .addField("Total Items", String.valueOf(items.size()), true)
                    .setColor(ADMIN_COLOR)
                    .setTimestamp(Instant.now());

            event.getHook().sendMessageEmbeds(embed.build()).queue();
        });
    }

    private void handleRemoveItem(SlashCommandInteractionEvent event) {
        String itemId = event.getOption("item_id").getAsString();
        
        event.deferReply(true).queue();
        
        Main.getBot().getExecutor().submit(() -> {
            boolean removed = botInventoryService.removeItem(itemId);
            
            if (removed) {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("Item Removed")
                        .setDescription(String.format("Item with ID `%s` has been removed from bot inventory.", itemId))
                        .setColor(SUCCESS_COLOR)
                        .setTimestamp(Instant.now());
                event.getHook().sendMessageEmbeds(embed.build()).queue();
            } else {
                sendError(event, "Item not found: " + itemId);
            }
        });
    }

    private void handleGiveCoins(SlashCommandInteractionEvent event) {
        net.dv8tion.jda.api.entities.User targetUser = event.getOption("user").getAsUser();
        int amount = event.getOption("amount").getAsInt();
        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString() : "Admin gift";
        
        event.deferReply(true).queue();
        
        Main.getBot().getExecutor().submit(() -> {
            try {
                User user = Main.getUserService().getOrCreateUser(targetUser.getId());
                user.setCoins(user.getCoins() + amount);
                Main.getUserService().updateUser(user);
                
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("Coins Given")
                        .setDescription(String.format("Given **%,d coins** to %s", amount, targetUser.getAsMention()))
                        .addField("Reason", reason, false)
                        .addField("New Balance", String.format("%,d coins", user.getCoins()), false)
                        .setColor(SUCCESS_COLOR)
                        .setTimestamp(Instant.now());
                
                event.getHook().sendMessageEmbeds(embed.build()).queue();
                
            } catch (Exception e) {
                sendError(event, "Failed to give coins: " + e.getMessage());
            }
        });
    }

    private void handleSetPremium(SlashCommandInteractionEvent event) {
        net.dv8tion.jda.api.entities.User targetUser = event.getOption("user").getAsUser();
        boolean premium = event.getOption("premium").getAsBoolean();
        
        event.deferReply(true).queue();
        
        Main.getBot().getExecutor().submit(() -> {
            try {
                User user = Main.getUserService().getOrCreateUser(targetUser.getId());
                user.setPremium(premium);
                Main.getUserService().updateUser(user);
                
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("Premium Status Updated")
                        .setDescription(String.format("Set premium status for %s to **%s**", 
                            targetUser.getAsMention(), premium ? "ENABLED" : "DISABLED"))
                        .setColor(premium ? SUCCESS_COLOR : ERROR_COLOR)
                        .setTimestamp(Instant.now());
                
                event.getHook().sendMessageEmbeds(embed.build()).queue();
                
            } catch (Exception e) {
                sendError(event, "Failed to update premium status: " + e.getMessage());
            }
        });
    }

    private void handleStats(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        
        Main.getBot().getExecutor().submit(() -> {
            long totalUsers = Main.getUserService().getTotalUsers();
            long totalImages = Main.getUserService().getTotalImagesGenerated();
            List<BotInventoryItem> botItems = botInventoryService.getAllItems();
            
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Comprehensive Admin Statistics")
                    .setColor(ADMIN_COLOR)
                    .setTimestamp(Instant.now());

            // User statistics
            embed.addField("Users", String.format("%,d total users", totalUsers), true);
            embed.addField("Images", String.format("%,d total generated", totalImages), true);
            embed.addField("Bot Items", String.format("%d total items", botItems.size()), true);

            // Item breakdown
            long activeItems = botItems.stream().filter(BotInventoryItem::canBeGivenAway).count();
            long expiredItems = botItems.stream().filter(BotInventoryItem::isExpired).count();
            long nitroItems = botItems.stream().filter(i -> "discord_nitro".equals(i.getType())).count();
            
            embed.addField("Active Items", String.valueOf(activeItems), true);
            embed.addField("Expired Items", String.valueOf(expiredItems), true);
            embed.addField("Nitro Items", String.valueOf(nitroItems), true);

            // Bot statistics
            embed.addField("Guilds", String.valueOf(Main.getBot().getShardManager().getGuilds().size()), true);
            embed.addField("Shards", String.valueOf(Main.getBot().getShardManager().getShards().size()), true);
            embed.addField("Uptime", getUptimeString(), true);

            // Giveaway statistics
            int activeGiveaways = giveawayService.getActiveGiveaways().size();
            embed.addField("Active Giveaways", String.valueOf(activeGiveaways), true);
            embed.addField("Cache Size", String.valueOf(temporaryStorage.size()), true);
            embed.addField("Memory Usage", getMemoryUsage(), true);

            event.getHook().sendMessageEmbeds(embed.build()).queue();
        });
    }

    private void handleCleanup(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        
        Main.getBot().getExecutor().submit(() -> {
            try {
                // Clean up expired items
                List<BotInventoryItem> expiredItems = botInventoryService.getAllItems().stream()
                    .filter(BotInventoryItem::isExpired)
                    .toList();
                
                int removedItems = 0;
                for (BotInventoryItem item : expiredItems) {
                    if (botInventoryService.removeItem(item.getId())) {
                        removedItems++;
                    }
                }
                
                // Clean up completed giveaways older than 30 days
                int cleanedGiveaways = giveawayService.cleanupOldGiveaways(30);
                
                // Clean up temporary storage entries older than 1 hour
                long oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000);
                temporaryStorage.entrySet().removeIf(entry -> {
                    try {
                        String[] parts = entry.getKey().split(":");
                        if (parts.length >= 3) {
                            long timestamp = Long.parseLong(parts[2]);
                            return timestamp < oneHourAgo;
                        }
                    } catch (NumberFormatException ignored) {}
                    return false;
                });
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Cleanup Complete")
                    .setColor(SUCCESS_COLOR)
                    .setDescription(String.format(
                        "**Cleanup Results:**\n" +
                        "• Removed %d expired items\n" +
                        "• Cleaned %d old giveaways\n" +
                        "• Cleared temporary cache",
                        removedItems, cleanedGiveaways))
                    .setTimestamp(Instant.now());
                
                event.getHook().sendMessageEmbeds(embed.build()).queue();
                
            } catch (Exception e) {
                sendError(event, "Failed to perform cleanup: " + e.getMessage());
            }
        });
    }

    private String getUptimeString() {
        long uptime = System.currentTimeMillis() - Main.getBot().getShardManager().getShards().get(0).getGatewayPing();
        long seconds = uptime / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours % 24, minutes % 60);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else {
            return String.format("%dm %ds", minutes, seconds % 60);
        }
    }

    private String getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long total = runtime.totalMemory();
        return String.format("%.1f/%.1f MB", used / 1024.0 / 1024.0, total / 1024.0 / 1024.0);
    }

    private void sendError(SlashCommandInteractionEvent event, String message) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Error")
                .setDescription(message)
                .setColor(ERROR_COLOR)
                .setTimestamp(Instant.now());
        
        if (event.isAcknowledged()) {
            event.getHook().sendMessageEmbeds(embed.build()).setEphemeral(true).queue();
        } else {
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        }
    }

    private void sendModalError(ModalInteractionEvent event, String message) {
        event.replyEmbeds(MediaContainerManager.createError("Error", message).build()).setEphemeral(true).queue();
    }
    
    private void handleUserLookup(SlashCommandInteractionEvent event) {
        net.dv8tion.jda.api.entities.User targetDiscordUser = event.getOption("user").getAsUser();
        User targetUser = Main.getUserService().getOrCreateUser(targetDiscordUser.getId());
        
        EmbedBuilder embed = MediaContainerManager.createUserEmbed("User Lookup", null, targetDiscordUser, targetUser);
        
        // Basic Information
        embed.addField("👤 Basic Info", 
                String.format("**User ID:** `%s`\n**Username:** %s\n**Premium:** %s\n**Admin:** %s", 
                        targetUser.getUserId(), 
                        targetDiscordUser.getAsMention(),
                        targetUser.isPremium() ? "✅ Yes" : "❌ No",
                        targetUser.isAdmin() ? "✅ Yes" : "❌ No"), true);
        
        // Account Statistics
        embed.addField("📊 Account Stats", 
                String.format("**Images Generated:** %,d\n**NSFW Enabled:** %s\n**Locale:** %s\n**Theme:** %s", 
                        targetUser.getImagesGenerated(),
                        targetUser.isNsfw() ? "✅ Yes" : "❌ No",
                        targetUser.getLocale(),
                        targetUser.getTheme() != null ? targetUser.getTheme() : "Default"), true);
        
        // Economy Information
        MediaContainerManager.addCoinField(embed, "💰 Current Balance", targetUser.getCoins(), true);
        MediaContainerManager.addCoinField(embed, "📈 Total Earned", targetUser.getTotalCoinsEarned(), true);
        MediaContainerManager.addCoinField(embed, "📉 Total Spent", targetUser.getTotalCoinsSpent(), true);
        
        // Quest Information
        embed.addField("🎯 Quest Stats", 
                String.format("**Total Completed:** %,d\n**Today:** %d\n**Active Quests:** %d", 
                        targetUser.getTotalQuestsCompleted(),
                        targetUser.getQuestsCompletedToday(),
                        targetUser.getDailyQuests().size()), true);
        
        // Inventory Information
        embed.addField("📦 Inventory", 
                String.format("**Unique Items:** %d/%d\n**Total Items:** %d\n**Favorites:** %d/%d", 
                        targetUser.getUniqueInventoryItems(),
                        User.MAX_INVENTORY_SIZE,
                        targetUser.getTotalInventoryItems(),
                        targetUser.getFavorites().size(),
                        targetUser.getFavoriteLimit()), true);
        
        // Activity Information
        if (targetUser.getAccountCreatedDate() != null) {
            embed.addField("📅 Account Created", 
                    String.format("<t:%d:F>", targetUser.getAccountCreatedDate().toEpochSecond(java.time.ZoneOffset.UTC)), true);
        }
        
        if (targetUser.getLastActiveDate() != null) {
            embed.addField("🕐 Last Active", 
                    String.format("<t:%d:R>", targetUser.getLastActiveDate().toEpochSecond(java.time.ZoneOffset.UTC)), true);
        }
        
        embed.setFooter("Admin User Lookup • Use /admin userstats for detailed usage statistics", null);
        
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
    
    private void handleUserStats(SlashCommandInteractionEvent event) {
        net.dv8tion.jda.api.entities.User targetDiscordUser = event.getOption("user").getAsUser();
        User targetUser = Main.getUserService().getOrCreateUser(targetDiscordUser.getId());
        
        EmbedBuilder embed = MediaContainerManager.createUserEmbed("User Usage Statistics", null, targetDiscordUser, targetUser);
        
        // Command Usage Statistics
        StringBuilder commandStats = new StringBuilder();
        commandStats.append(String.format("**Total Commands Used:** %,d\n", targetUser.getTotalCommandsUsed()));
        commandStats.append(String.format("**Most Used Command:** %s\n", targetUser.getMostUsedCommand()));
        
        var topCommands = targetUser.getCommandUsageCount().entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .collect(java.util.stream.Collectors.toList());
        
        if (!topCommands.isEmpty()) {
            commandStats.append("**Top Commands:**\n");
            for (var entry : topCommands) {
                commandStats.append(String.format("• %s: %,d uses\n", entry.getKey(), entry.getValue()));
            }
        }
        
        embed.addField("🎮 Command Usage", commandStats.toString(), true);
        
        // Source Usage Statistics
        StringBuilder sourceStats = new StringBuilder();
        sourceStats.append(String.format("**Most Used Source:** %s\n", targetUser.getMostUsedSource()));
        
        var topSources = targetUser.getSourceUsageCount().entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .collect(java.util.stream.Collectors.toList());
        
        if (!topSources.isEmpty()) {
            sourceStats.append("**Source Usage:**\n");
            for (var entry : topSources) {
                sourceStats.append(String.format("• %s: %,d times\n", entry.getKey(), entry.getValue()));
            }
        }
        
        embed.addField("📊 Source Usage", sourceStats.toString(), true);
        
        // Subreddit Usage Statistics
        StringBuilder subredditStats = new StringBuilder();
        var topSubreddits = targetUser.getTopSubreddits(5);
        
        if (!topSubreddits.isEmpty()) {
            subredditStats.append("**Top Subreddits:**\n");
            for (String subreddit : topSubreddits) {
                int count = targetUser.getSubredditUsageCount().getOrDefault(subreddit, 0);
                subredditStats.append(String.format("• r/%s: %d times\n", subreddit, count));
            }
        } else {
            subredditStats.append("No custom subreddits used yet.");
        }
        
        subredditStats.append(String.format("\n**Custom Subreddits Saved:** %d/%d", 
                targetUser.getCustomSubreddits().size(), User.MAX_CUSTOM_SUBREDDITS));
        
        embed.addField("🔍 Subreddit Usage", subredditStats.toString(), false);
        
        // Activity Pattern
        if (targetUser.getLastActiveDate() != null && targetUser.getAccountCreatedDate() != null) {
            long daysSinceCreation = java.time.temporal.ChronoUnit.DAYS.between(
                    targetUser.getAccountCreatedDate().toLocalDate(), 
                    java.time.LocalDate.now());
            
            double avgCommandsPerDay = daysSinceCreation > 0 ? 
                    (double) targetUser.getTotalCommandsUsed() / daysSinceCreation : 0;
            
            embed.addField("📈 Activity Pattern", 
                    String.format("**Account Age:** %d days\n**Avg Commands/Day:** %.1f\n**Images/Day:** %.1f", 
                            daysSinceCreation,
                            avgCommandsPerDay,
                            daysSinceCreation > 0 ? (double) targetUser.getImagesGenerated() / daysSinceCreation : 0), true);
        }
        
        embed.setFooter("Admin User Statistics • Data tracked since user registration", null);
        
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
    
    private void handleMaintenance(SlashCommandInteractionEvent event) {
        boolean enabled = event.getOption("enabled").getAsBoolean();
        LocalConfig config = LocalConfig.getInstance();
        
        try {
            // Update local config
            config.setMaintenanceMode(enabled);
            
            if (enabled) {
                Main.getBot().getShardManager().setActivity(Activity.playing("🔧 Under Maintenance"));
                Main.getBot().getShardManager().setStatus(OnlineStatus.DO_NOT_DISTURB);
            } else {
                Main.getBot().getShardManager().setActivity(Activity.playing("Use /support for help! | Alpha :3"));
                Main.getBot().getShardManager().setStatus(OnlineStatus.ONLINE);
            }
            
            String status = enabled ? "DO_NOT_DISTURB" : "ONLINE";
            String activity = enabled ? "🔧 Under Maintenance" : "Use /support for help! | Alpha :3";
            
            EmbedBuilder embed = MediaContainerManager.createSuccess(
                enabled ? "Maintenance Mode Enabled" : "Maintenance Mode Disabled",
                String.format("**Status:** %s\n**Activity:** %s\n**Shards:** %d\n**Saved to:** config.json", 
                        status, activity, Main.getBot().getShardManager().getShards().size()));
            
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
            
        } catch (Exception e) {
            sendError(event, "Failed to toggle maintenance mode: " + e.getMessage());
        }
    }
    
    private void handleToggleSource(SlashCommandInteractionEvent event) {
        String source = event.getOption("source").getAsString();
        boolean enabled = event.getOption("enabled").getAsBoolean();
        LocalConfig config = LocalConfig.getInstance();
        
        // Update source status
        config.setSourceEnabled(source, enabled);
        
        EmbedBuilder embed = MediaContainerManager.createSuccess("Source Updated", 
                String.format("**Source:** %s\n**Status:** %s\n**Saved to:** config.json", 
                        source, enabled ? "✅ Enabled" : "❌ Disabled"));
        
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
    
    private void handleListSources(SlashCommandInteractionEvent event) {
        LocalConfig config = LocalConfig.getInstance();
        Map<String, Boolean> sources = config.getEnabledSources();
        
        EmbedBuilder embed = MediaContainerManager.createInfo("Source Status", null);
        
        StringBuilder enabledSources = new StringBuilder();
        StringBuilder disabledSources = new StringBuilder();
        
        for (Map.Entry<String, Boolean> entry : sources.entrySet()) {
            String sourceName = entry.getKey();
            boolean isEnabled = entry.getValue();
            
            if (isEnabled) {
                enabledSources.append("✅ ").append(sourceName).append("\n");
            } else {
                disabledSources.append("❌ ").append(sourceName).append("\n");
            }
        }
        
        if (enabledSources.length() > 0) {
            embed.addField("🟢 Enabled Sources", enabledSources.toString(), true);
        }
        
        if (disabledSources.length() > 0) {
            embed.addField("🔴 Disabled Sources", disabledSources.toString(), true);
        }
        
        embed.addField("📝 Configuration", 
                String.format("**Total Sources:** %d\n**Config File:** config.json\n**Maintenance Mode:** %s\n**Sensitive Data:** .env file", 
                        sources.size(), 
                        config.getMaintenanceMode() ? "🔧 Enabled" : "✅ Disabled"), false);
        
        embed.setFooter("Use /admin togglesource to change source status", null);
        
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}