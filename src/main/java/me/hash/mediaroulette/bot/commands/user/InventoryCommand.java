package me.hash.mediaroulette.bot.commands.user;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.bot.MediaContainerManager;
import me.hash.mediaroulette.bot.commands.CommandHandler;
import me.hash.mediaroulette.model.InventoryItem;
import me.hash.mediaroulette.model.User;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.time.Instant;
import java.util.List;

import static me.hash.mediaroulette.bot.MediaContainerManager.PRIMARY_COLOR;
import static me.hash.mediaroulette.bot.MediaContainerManager.SUCCESS_COLOR;

public class InventoryCommand extends ListenerAdapter implements CommandHandler {

    private static final int ITEMS_PER_PAGE = 10;

    @Override
    public CommandData getCommandData() {
        return Commands.slash("inventory", "📦 Manage your inventory")
                .addSubcommands(
                        new SubcommandData("view", "View your inventory")
                                .addOption(OptionType.STRING, "filter", "Filter by type or rarity", false)
                                .addOption(OptionType.INTEGER, "page", "Page number", false),
                        new SubcommandData("sort", "Sort your inventory")
                                .addOption(OptionType.STRING, "by", "Sort by: name, type, rarity, quantity, acquired", true),
                        new SubcommandData("use", "Use an item from your inventory")
                                .addOption(OptionType.STRING, "item", "Item ID to use", true)
                                .addOption(OptionType.INTEGER, "quantity", "Quantity to use (default: 1)", false),
                        new SubcommandData("info", "Get detailed information about an item")
                                .addOption(OptionType.STRING, "item", "Item ID", true)
                )
                .setIntegrationTypes(IntegrationType.ALL)
                .setContexts(InteractionContextType.ALL);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("inventory")) return;

        event.deferReply().queue();
        Bot.executor.execute(() -> {
            String subcommand = event.getSubcommandName();
            String userId = event.getUser().getId();

            switch (subcommand) {
                case "view" -> handleView(event, userId);
                case "sort" -> handleSort(event, userId);
                case "use" -> handleUse(event, userId);
                case "info" -> handleInfo(event, userId);
            }
        });
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        
        if (componentId.startsWith("inventory:")) {
            event.deferEdit().queue();
            Bot.executor.execute(() -> handleInventoryButton(event, componentId));
        }
    }

    private void handleView(SlashCommandInteractionEvent event, String userId) {
        User user = Main.getUserService().getOrCreateUser(userId);
        String filter = event.getOption("filter") != null ? event.getOption("filter").getAsString() : null;
        int page = event.getOption("page") != null ? event.getOption("page").getAsInt() : 1;

        List<InventoryItem> items = user.getInventory();
        
        // Apply filter if specified
        if (filter != null) {
            String filterLower = filter.toLowerCase();
            items = items.stream()
                    .filter(item -> item.getType().toLowerCase().contains(filterLower) || 
                                  item.getRarity().toLowerCase().contains(filterLower))
                    .toList();
        }

        if (items.isEmpty()) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📦 Your Inventory")
                    .setDescription("Your inventory is empty" + (filter != null ? " for filter: " + filter : "") + "!")
                    .setColor(PRIMARY_COLOR)
                    .setTimestamp(Instant.now());
            event.getHook().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        // Pagination
        int totalPages = (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE);
        page = Math.max(1, Math.min(page, totalPages));
        
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, items.size());
        List<InventoryItem> pageItems = items.subList(startIndex, endIndex);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📦 Your Inventory" + (filter != null ? " (Filtered)" : ""))
                .setColor(PRIMARY_COLOR)
                .setTimestamp(Instant.now());

        StringBuilder description = new StringBuilder();
        description.append(String.format("**Inventory Usage:** %d/%d slots\n", 
            user.getUniqueInventoryItems(), User.MAX_INVENTORY_SIZE));
        description.append(String.format("**Total Items:** %d items\n\n", user.getTotalInventoryItems()));

        for (InventoryItem item : pageItems) {
            description.append(String.format("%s **%s** (x%d)\n", 
                item.toString(), item.getName(), item.getQuantity()));
            description.append(String.format("   *%s* • ID: `%s`\n\n", 
                item.getDescription(), item.getId()));
        }

        embed.setDescription(description.toString());
        embed.setFooter(String.format("Page %d/%d • %d items total", page, totalPages, items.size()), null);

        // Add navigation buttons
        ActionRow buttons = ActionRow.of(
                Button.primary("inventory:prev:" + (page - 1) + ":" + (filter != null ? filter : ""), "◀ Previous")
                        .withDisabled(page <= 1),
                Button.primary("inventory:next:" + (page + 1) + ":" + (filter != null ? filter : ""), "Next ▶")
                        .withDisabled(page >= totalPages),
                Button.secondary("inventory:refresh:" + page + ":" + (filter != null ? filter : ""), "🔄 Refresh")
        );

        event.getHook().sendMessageEmbeds(embed.build()).setComponents(buttons).queue();
    }

    private void handleSort(SlashCommandInteractionEvent event, String userId) {
        User user = Main.getUserService().getOrCreateUser(userId);
        String sortBy = event.getOption("by").getAsString();

        user.sortInventory(sortBy);
        Main.getUserService().updateUser(user);

        String description = "Your inventory has been sorted by **" + sortBy + "**.";
        EmbedBuilder embed = MediaContainerManager.createSuccess("Inventory Sorted", description);

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }

    private void handleUse(SlashCommandInteractionEvent event, String userId) {
        User user = Main.getUserService().getOrCreateUser(userId);
        String itemId = event.getOption("item").getAsString();
        int quantity = event.getOption("quantity") != null ? event.getOption("quantity").getAsInt() : 1;

        InventoryItem item = user.getInventoryItem(itemId);
        if (item == null) {
            sendError(event, "Item not found in your inventory: " + itemId);
            return;
        }

        if (!user.hasInventoryItem(itemId, quantity)) {
            sendError(event, String.format("You don't have enough of this item. You have %d, need %d.", 
                item.getQuantity(), quantity));
            return;
        }

        // Check if item is usable
        if (!"consumable".equals(item.getType()) && !"tool".equals(item.getType())) {
            sendError(event, "This item cannot be used: " + item.getName());
            return;
        }

        // Use the item
        boolean success = user.removeInventoryItem(itemId, quantity);
        if (success) {
            Main.getUserService().updateUser(user);
            
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("✅ Item Used")
                    .setDescription(String.format("You used **%dx %s**!\n\n*%s*", 
                        quantity, item.getName(), item.getDescription()))
                    .setColor(SUCCESS_COLOR)
                    .setTimestamp(Instant.now());

            event.getHook().sendMessageEmbeds(embed.build()).queue();
        } else {
            sendError(event, "Failed to use the item. Please try again.");
        }
    }

    private void handleInfo(SlashCommandInteractionEvent event, String userId) {
        User user = Main.getUserService().getOrCreateUser(userId);
        String itemId = event.getOption("item").getAsString();

        InventoryItem item = user.getInventoryItem(itemId);
        if (item == null) {
            sendError(event, "Item not found in your inventory: " + itemId);
            return;
        }

        EmbedBuilder embed = MediaContainerManager.createBase()
                .setTitle(item.getTypeEmoji() + " " + item.getName())
                .setDescription(item.getDescription())
                .setColor(PRIMARY_COLOR);

        embed.addField("📊 Details", 
                String.format("**Type:** %s\n**Rarity:** %s\n**Quantity:** %d", 
                    item.getType(), item.getRarityEmoji() + " " + item.getRarity(), item.getQuantity()), true);

        embed.addField("🔍 Info", 
                String.format("**ID:** `%s`\n**Stackable:** %s\n**Source:** %s", 
                    item.getId(), item.isStackable() ? "Yes" : "No", 
                    item.getSource() != null ? item.getSource() : "Unknown"), true);

        embed.addField("📅 Acquired", 
                String.format("<t:%d:F>", item.getAcquiredAt().getEpochSecond()), false);

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }

    private void handleInventoryButton(ButtonInteractionEvent event, String componentId) {
        String[] parts = componentId.split(":");
        if (parts.length < 3) return;

        String action = parts[1];
        String userId = event.getUser().getId();

        switch (action) {
            case "prev", "next", "refresh" -> {
                int page = Integer.parseInt(parts[2]);
                String filter = parts.length > 3 && !parts[3].isEmpty() ? parts[3] : null;
                
                User user = Main.getUserService().getOrCreateUser(userId);
                // Recreate the view with new page
                updateInventoryView(event, user, filter, page);
            }
        }
    }

    private void updateInventoryView(ButtonInteractionEvent event, User user, String filter, int page) {
        List<InventoryItem> items = user.getInventory();
        
        // Apply filter if specified
        if (filter != null) {
            String filterLower = filter.toLowerCase();
            items = items.stream()
                    .filter(item -> item.getType().toLowerCase().contains(filterLower) || 
                                  item.getRarity().toLowerCase().contains(filterLower))
                    .toList();
        }

        if (items.isEmpty()) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📦 Your Inventory")
                    .setDescription("Your inventory is empty" + (filter != null ? " for filter: " + filter : "") + "!")
                    .setColor(PRIMARY_COLOR)
                    .setTimestamp(Instant.now());
            event.getHook().editOriginalEmbeds(embed.build()).setComponents().queue();
            return;
        }

        // Pagination
        int totalPages = (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE);
        page = Math.max(1, Math.min(page, totalPages));
        
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, items.size());
        List<InventoryItem> pageItems = items.subList(startIndex, endIndex);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📦 Your Inventory" + (filter != null ? " (Filtered)" : ""))
                .setColor(PRIMARY_COLOR)
                .setTimestamp(Instant.now());

        StringBuilder description = new StringBuilder();
        description.append(String.format("**Inventory Usage:** %d/%d slots\n", 
            user.getUniqueInventoryItems(), User.MAX_INVENTORY_SIZE));
        description.append(String.format("**Total Items:** %d items\n\n", user.getTotalInventoryItems()));

        for (InventoryItem item : pageItems) {
            description.append(String.format("%s **%s** (x%d)\n", 
                item.toString(), item.getName(), item.getQuantity()));
            description.append(String.format("   *%s* • ID: `%s`\n\n", 
                item.getDescription(), item.getId()));
        }

        embed.setDescription(description.toString());
        embed.setFooter(String.format("Page %d/%d • %d items total", page, totalPages, items.size()), null);

        // Add navigation buttons
        ActionRow buttons = ActionRow.of(
                Button.primary("inventory:prev:" + (page - 1) + ":" + (filter != null ? filter : ""), "◀ Previous")
                        .withDisabled(page <= 1),
                Button.primary("inventory:next:" + (page + 1) + ":" + (filter != null ? filter : ""), "Next ▶")
                        .withDisabled(page >= totalPages),
                Button.secondary("inventory:refresh:" + page + ":" + (filter != null ? filter : ""), "🔄 Refresh")
        );

        event.getHook().editOriginalEmbeds(embed.build()).setComponents(buttons).queue();
    }

    private void sendError(SlashCommandInteractionEvent event, String message) {
        event.getHook().sendMessageEmbeds(MediaContainerManager.createError("Error", message).build()).queue();
    }
}