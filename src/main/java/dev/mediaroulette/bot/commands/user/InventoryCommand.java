package dev.mediaroulette.bot.commands.user;

import dev.mediaroulette.Main;
import dev.mediaroulette.bot.utils.EmbedFactory;
import dev.mediaroulette.bot.commands.CommandHandler;
import dev.mediaroulette.locale.LocaleManager;
import dev.mediaroulette.domain.economy.InventoryItem;
import dev.mediaroulette.domain.economy.Inventory;
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

import static dev.mediaroulette.bot.utils.EmbedFactory.PRIMARY_COLOR;
import static dev.mediaroulette.bot.utils.EmbedFactory.SUCCESS_COLOR;

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
        Main.getBot().getExecutor().execute(() -> {
            String subcommand = event.getSubcommandName();
            String userId = event.getUser().getId();
            dev.mediaroulette.domain.user.Account account = Main.getAccountService().getAccount(userId);
            Inventory inventory = Main.getInventoryService().getInventory(userId);
            LocaleManager locale = LocaleManager.getInstance(account.getLocale());

            switch (subcommand) {
                case "view" -> handleView(event, inventory, account, locale);
                case "sort" -> handleSort(event, inventory, locale);
                case "use" -> handleUse(event, inventory, locale);
                case "info" -> handleInfo(event, inventory, locale);
            }
        });
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        
        if (componentId.startsWith("inventory:")) {
            event.deferEdit().queue();
            Main.getBot().getExecutor().execute(() -> handleInventoryButton(event, componentId));
        }
    }

    private void handleView(SlashCommandInteractionEvent event, Inventory inventory, dev.mediaroulette.domain.user.Account account, LocaleManager locale) {
        String filter = event.getOption("filter") != null ? event.getOption("filter").getAsString() : null;
        int page = event.getOption("page") != null ? event.getOption("page").getAsInt() : 1;

        List<InventoryItem> items = inventory.getItems();
        
        if (filter != null) {
            String filterLower = filter.toLowerCase();
            items = items.stream()
                    .filter(item -> item.getType().toLowerCase().contains(filterLower) || 
                                  item.getRarity().toLowerCase().contains(filterLower))
                    .toList();
        }

        if (items.isEmpty()) {
            String filterSuffix = filter != null ? locale.get("inventory.filter_suffix", filter) : "";
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(locale.get("inventory.title"))
                    .setDescription(locale.get("inventory.empty", filterSuffix))
                    .setColor(PRIMARY_COLOR)
                    .setTimestamp(Instant.now());
            event.getHook().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        int totalPages = (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE);
        page = Math.max(1, Math.min(page, totalPages));
        
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, items.size());
        List<InventoryItem> pageItems = items.subList(startIndex, endIndex);

        String filtered = filter != null ? " (Filtered)" : "";
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(locale.get("inventory.title") + filtered)
                .setColor(PRIMARY_COLOR)
                .setTimestamp(Instant.now());

        StringBuilder description = new StringBuilder();
        description.append(locale.get("inventory.usage", inventory.getUniqueItemsCount(), Inventory.MAX_INVENTORY_SIZE)).append("\n");
        description.append(locale.get("inventory.total", inventory.getTotalItems())).append("\n\n");

        for (InventoryItem item : pageItems) {
            description.append(String.format("%s **%s** (x%d)\n", 
                item.toString(), item.getName(), item.getQuantity()));
            description.append(String.format("   *%s* • ID: `%s`\n\n", 
                item.getDescription(), item.getId()));
        }

        embed.setDescription(description.toString());
        embed.setFooter(locale.get("inventory.page_footer", page, totalPages, items.size()), null);

        ActionRow buttons = ActionRow.of(
                Button.primary("inventory:prev:" + (page - 1) + ":" + (filter != null ? filter : ""), "◀ " + locale.get("ui.previous"))
                        .withDisabled(page <= 1),
                Button.primary("inventory:next:" + (page + 1) + ":" + (filter != null ? filter : ""), locale.get("ui.next") + " ▶")
                        .withDisabled(page >= totalPages),
                Button.secondary("inventory:refresh:" + page + ":" + (filter != null ? filter : ""), "🔄 " + locale.get("ui.refresh"))
        );

        event.getHook().sendMessageEmbeds(embed.build()).setComponents(buttons).queue();
    }

    private void handleSort(SlashCommandInteractionEvent event, Inventory inventory, LocaleManager locale) {
        String sortBy = event.getOption("by").getAsString();

        inventory.sortItems(sortBy);
        Main.getInventoryService().updateInventory(inventory);

        String description = locale.get("inventory.sorted", sortBy);
        EmbedBuilder embed = EmbedFactory.createSuccess(locale.get("inventory.sorted_title"), description);

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }

    private void handleUse(SlashCommandInteractionEvent event, Inventory inventory, LocaleManager locale) {
        String itemId = event.getOption("item").getAsString();
        int quantity = event.getOption("quantity") != null ? event.getOption("quantity").getAsInt() : 1;

        InventoryItem item = inventory.getItem(itemId);
        if (item == null) {
            sendError(event, locale.get("inventory.item_not_found", itemId), locale);
            return;
        }

        if (!inventory.hasItem(itemId, quantity)) {
            sendError(event, locale.get("inventory.not_enough", item.getQuantity(), quantity), locale);
            return;
        }

        if (!"consumable".equals(item.getType()) && !"tool".equals(item.getType())) {
            sendError(event, locale.get("inventory.cannot_use", item.getName()), locale);
            return;
        }

        boolean success = inventory.removeItem(itemId, quantity);
        if (success) {
            Main.getInventoryService().updateInventory(inventory);
            
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(locale.get("inventory.item_used"))
                    .setDescription(locale.get("inventory.item_used_desc", quantity, item.getName(), item.getDescription()))
                    .setColor(SUCCESS_COLOR)
                    .setTimestamp(Instant.now());

            event.getHook().sendMessageEmbeds(embed.build()).queue();
        } else {
            sendError(event, locale.get("inventory.use_failed"), locale);
        }
    }

    private void handleInfo(SlashCommandInteractionEvent event, Inventory inventory, LocaleManager locale) {
        String itemId = event.getOption("item").getAsString();

        InventoryItem item = inventory.getItem(itemId);
        if (item == null) {
            sendError(event, locale.get("inventory.item_not_found", itemId), locale);
            return;
        }

        EmbedBuilder embed = EmbedFactory.createBase()
                .setTitle(item.getTypeEmoji() + " " + item.getName())
                .setDescription(item.getDescription())
                .setColor(PRIMARY_COLOR);

        embed.addField(locale.get("inventory.details"), 
                String.format("**%s:** %s\n**%s:** %s %s\n**%s:** %d", 
                    locale.get("inventory.type"), item.getType(),
                    locale.get("inventory.rarity"), item.getRarityEmoji(), item.getRarity(),
                    locale.get("inventory.quantity"), item.getQuantity()), true);

        embed.addField(locale.get("inventory.info"), 
                String.format("**ID:** `%s`\n**%s:** %s\n**%s:** %s", 
                    item.getId(),
                    locale.get("inventory.stackable"), item.isStackable() ? "Yes" : "No",
                    locale.get("inventory.source"), item.getSource() != null ? item.getSource() : "Unknown"), true);

        embed.addField(locale.get("inventory.acquired"), 
                String.format("<t:%d:F>", item.getAcquiredAt().getEpochSecond()), false);

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }

    private void handleInventoryButton(ButtonInteractionEvent event, String componentId) {
        String[] parts = componentId.split(":");
        if (parts.length < 3) return;

        String action = parts[1];
        String userId = event.getUser().getId();
        dev.mediaroulette.domain.user.Account account = Main.getAccountService().getAccount(userId);
            Inventory inventory = Main.getInventoryService().getInventory(userId);
        LocaleManager locale = LocaleManager.getInstance(account.getLocale());

        switch (action) {
            case "prev", "next", "refresh" -> {
                int page = Integer.parseInt(parts[2]);
                String filter = parts.length > 3 && !parts[3].isEmpty() ? parts[3] : null;
                updateInventoryView(event, inventory, account, filter, page, locale);
            }
        }
    }

    private void updateInventoryView(ButtonInteractionEvent event, Inventory inventory, dev.mediaroulette.domain.user.Account account, String filter, int page, LocaleManager locale) {
        List<InventoryItem> items = inventory.getItems();
        
        if (filter != null) {
            String filterLower = filter.toLowerCase();
            items = items.stream()
                    .filter(item -> item.getType().toLowerCase().contains(filterLower) || 
                                  item.getRarity().toLowerCase().contains(filterLower))
                    .toList();
        }

        if (items.isEmpty()) {
            String filterSuffix = filter != null ? locale.get("inventory.filter_suffix", filter) : "";
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(locale.get("inventory.title"))
                    .setDescription(locale.get("inventory.empty", filterSuffix))
                    .setColor(PRIMARY_COLOR)
                    .setTimestamp(Instant.now());
            event.getHook().editOriginalEmbeds(embed.build()).setComponents().queue();
            return;
        }

        int totalPages = (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE);
        page = Math.max(1, Math.min(page, totalPages));
        
        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, items.size());
        List<InventoryItem> pageItems = items.subList(startIndex, endIndex);

        String filtered = filter != null ? " (Filtered)" : "";
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(locale.get("inventory.title") + filtered)
                .setColor(PRIMARY_COLOR)
                .setTimestamp(Instant.now());

        StringBuilder description = new StringBuilder();
        description.append(locale.get("inventory.usage", inventory.getUniqueItemsCount(), Inventory.MAX_INVENTORY_SIZE)).append("\n");
        description.append(locale.get("inventory.total", inventory.getTotalItems())).append("\n\n");

        for (InventoryItem item : pageItems) {
            description.append(String.format("%s **%s** (x%d)\n", 
                item.toString(), item.getName(), item.getQuantity()));
            description.append(String.format("   *%s* • ID: `%s`\n\n", 
                item.getDescription(), item.getId()));
        }

        embed.setDescription(description.toString());
        embed.setFooter(locale.get("inventory.page_footer", page, totalPages, items.size()), null);

        ActionRow buttons = ActionRow.of(
                Button.primary("inventory:prev:" + (page - 1) + ":" + (filter != null ? filter : ""), "◀ " + locale.get("ui.previous"))
                        .withDisabled(page <= 1),
                Button.primary("inventory:next:" + (page + 1) + ":" + (filter != null ? filter : ""), locale.get("ui.next") + " ▶")
                        .withDisabled(page >= totalPages),
                Button.secondary("inventory:refresh:" + page + ":" + (filter != null ? filter : ""), "🔄 " + locale.get("ui.refresh"))
        );

        event.getHook().editOriginalEmbeds(embed.build()).setComponents(buttons).queue();
    }

    private void sendError(SlashCommandInteractionEvent event, String message, LocaleManager locale) {
        event.getHook().sendMessageEmbeds(EmbedFactory.createError(locale.get("error.title"), message).build()).queue();
    }
}