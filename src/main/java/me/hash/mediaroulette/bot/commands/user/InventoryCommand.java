package me.hash.mediaroulette.bot.commands.user;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.MediaContainerManager;
import me.hash.mediaroulette.bot.commands.CommandHandler;
import me.hash.mediaroulette.locale.LocaleManager;
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

import static me.hash.mediaroulette.bot.utils.EmbedFactory.PRIMARY_COLOR;
import static me.hash.mediaroulette.bot.utils.EmbedFactory.SUCCESS_COLOR;

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
            User user = Main.getUserService().getOrCreateUser(userId);
            LocaleManager locale = LocaleManager.getInstance(user.getLocale());

            switch (subcommand) {
                case "view" -> handleView(event, user, locale);
                case "sort" -> handleSort(event, user, locale);
                case "use" -> handleUse(event, user, locale);
                case "info" -> handleInfo(event, user, locale);
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

    private void handleView(SlashCommandInteractionEvent event, User user, LocaleManager locale) {
        String filter = event.getOption("filter") != null ? event.getOption("filter").getAsString() : null;
        int page = event.getOption("page") != null ? event.getOption("page").getAsInt() : 1;

        List<InventoryItem> items = user.getInventory();
        
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
        description.append(locale.get("inventory.usage", user.getUniqueInventoryItems(), User.MAX_INVENTORY_SIZE)).append("\n");
        description.append(locale.get("inventory.total", user.getTotalInventoryItems())).append("\n\n");

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

    private void handleSort(SlashCommandInteractionEvent event, User user, LocaleManager locale) {
        String sortBy = event.getOption("by").getAsString();

        user.sortInventory(sortBy);
        Main.getUserService().updateUser(user);

        String description = locale.get("inventory.sorted", sortBy);
        EmbedBuilder embed = MediaContainerManager.createSuccess(locale.get("inventory.sorted_title"), description);

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }

    private void handleUse(SlashCommandInteractionEvent event, User user, LocaleManager locale) {
        String itemId = event.getOption("item").getAsString();
        int quantity = event.getOption("quantity") != null ? event.getOption("quantity").getAsInt() : 1;

        InventoryItem item = user.getInventoryItem(itemId);
        if (item == null) {
            sendError(event, locale.get("inventory.item_not_found", itemId), locale);
            return;
        }

        if (!user.hasInventoryItem(itemId, quantity)) {
            sendError(event, locale.get("inventory.not_enough", item.getQuantity(), quantity), locale);
            return;
        }

        if (!"consumable".equals(item.getType()) && !"tool".equals(item.getType())) {
            sendError(event, locale.get("inventory.cannot_use", item.getName()), locale);
            return;
        }

        boolean success = user.removeInventoryItem(itemId, quantity);
        if (success) {
            Main.getUserService().updateUser(user);
            
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

    private void handleInfo(SlashCommandInteractionEvent event, User user, LocaleManager locale) {
        String itemId = event.getOption("item").getAsString();

        InventoryItem item = user.getInventoryItem(itemId);
        if (item == null) {
            sendError(event, locale.get("inventory.item_not_found", itemId), locale);
            return;
        }

        EmbedBuilder embed = MediaContainerManager.createBase()
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
        User user = Main.getUserService().getOrCreateUser(userId);
        LocaleManager locale = LocaleManager.getInstance(user.getLocale());

        switch (action) {
            case "prev", "next", "refresh" -> {
                int page = Integer.parseInt(parts[2]);
                String filter = parts.length > 3 && !parts[3].isEmpty() ? parts[3] : null;
                updateInventoryView(event, user, filter, page, locale);
            }
        }
    }

    private void updateInventoryView(ButtonInteractionEvent event, User user, String filter, int page, LocaleManager locale) {
        List<InventoryItem> items = user.getInventory();
        
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
        description.append(locale.get("inventory.usage", user.getUniqueInventoryItems(), User.MAX_INVENTORY_SIZE)).append("\n");
        description.append(locale.get("inventory.total", user.getTotalInventoryItems())).append("\n\n");

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
        event.getHook().sendMessageEmbeds(MediaContainerManager.createError(locale.get("error.title"), message).build()).queue();
    }
}