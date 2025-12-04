package me.hash.mediaroulette.bot.commands.config;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.bot.commands.BaseCommand;
import me.hash.mediaroulette.bot.utils.CommandCooldown;
import me.hash.mediaroulette.bot.utils.Emoji;
import me.hash.mediaroulette.bot.commands.CommandHandler;
import me.hash.mediaroulette.model.ImageOptions;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.utils.LocaleManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.modals.Modal;

import java.awt.Color;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChancesCommand extends BaseCommand {

    private static final Color PRIMARY_COLOR = new Color(88, 101, 242);
    private static final Color SUCCESS_COLOR = new Color(87, 242, 135);
    private static final Color ERROR_COLOR = new Color(220, 53, 69);
    
    private static final Map<Long, ChancesSession> USER_SESSIONS = new ConcurrentHashMap<>();

    @Override
    public CommandData getCommandData() {
        return Commands.slash("chances", "🎲 Configure image source chances by category")
                .setIntegrationTypes(IntegrationType.ALL)
                .setContexts(InteractionContextType.ALL);
    }

    @Override
    @CommandCooldown(value = 3, commands = {"chances"})
    public void handleCommand(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("chances")) return;

        event.deferReply().queue();
        Main.getBot().getExecutor().execute(() -> {
            long userId = event.getUser().getIdLong();

            User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
            
            if (user.getImageOptionsMap().isEmpty()) {
                initializeDefaultOptions(user);
            }

            ChancesSession session = new ChancesSession(user);
            USER_SESSIONS.put(userId, session);

            EmbedBuilder embed = createChancesEmbed(session, event.getUser());
            List<ActionRow> components = createChancesComponents(session);

            event.getHook().sendMessageEmbeds(embed.build())
                    .addComponents(components)
                    .queue();
        });
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("chances:")) return;

        long userId = event.getUser().getIdLong();
        User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
        LocaleManager localeManager = LocaleManager.getInstance(user.getLocale());

        String originalUserId = event.getMessage().getInteractionMetadata().getUser().getId();
        if (!event.getUser().getId().equals(originalUserId)) {
            event.getHook().sendMessage(localeManager.get("error.not_your_menu")).setEphemeral(true).queue();
            return;
        }

        String action = event.getComponentId().split(":")[1];
        
        // Handle edit actions differently (no defer, direct modal reply)
        if (action.startsWith("edit_")) {
            Main.getBot().getExecutor().execute(() -> {
                ChancesSession session = USER_SESSIONS.get(userId);
                
                if (session == null) {
                    if (user.getImageOptionsMap().isEmpty()) {
                        initializeDefaultOptions(user);
                    }
                    session = new ChancesSession(user);
                    USER_SESSIONS.put(userId, session);
                }
                
                String imageType = action.substring(5);
                handleEditSource(event, session, imageType);
            });
            return;
        }

        // For all other actions, defer edit first
        event.deferEdit().queue();
        Main.getBot().getExecutor().execute(() -> {
            ChancesSession session = USER_SESSIONS.get(userId);
            
            if (session == null) {
                if (user.getImageOptionsMap().isEmpty()) {
                    initializeDefaultOptions(user);
                }
                session = new ChancesSession(user);
                USER_SESSIONS.put(userId, session);
            }

            switch (action) {
                case "reset" -> handleResetAll(event, session);
                case "save" -> handleSaveChanges(event, session);
                case "toggle_all_on" -> handleToggleAll(event, session, true);
                case "toggle_all_off" -> handleToggleAll(event, session, false);
                default -> {
                    if (action.startsWith("toggle_")) {
                        String imageType = action.substring(7);
                        handleToggleSource(event, session, imageType);
                    }
                }
            }
        });
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().startsWith("chances:")) return;

        event.deferEdit().queue();
        Main.getBot().getExecutor().execute(() -> {
            long userId = event.getUser().getIdLong();

            User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
            LocaleManager localeManager = LocaleManager.getInstance(user.getLocale());

            String originalUserId = event.getMessage().getInteractionMetadata().getUser().getId();
            if (!event.getUser().getId().equals(originalUserId)) {
                event.getHook().sendMessage(localeManager.get("error.not_your_menu")).setEphemeral(true).queue();
                return;
            }

            ChancesSession session = USER_SESSIONS.get(userId);
            
            if (session == null) return;

            String componentId = event.getComponentId();
            
            if (componentId.equals("chances:category")) {
                String category = event.getValues().getFirst();
                session.setSelectedCategory(category);
                updateChancesDisplay(event, session);
            } else if (componentId.equals("chances:source_select")) {
                String selectedSource = event.getValues().getFirst();
                if (!selectedSource.equals("none")) {
                    handleSourceSelect(event, session, selectedSource);
                }
            }
        });
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().startsWith("chances:edit:")) return;

        event.deferEdit().queue();
        Main.getBot().getExecutor().execute(() -> {
            long userId = event.getUser().getIdLong();
            ChancesSession session = USER_SESSIONS.get(userId);
            
            if (session == null) return;

            String imageType = event.getModalId().split(":")[2];
            String enabledValue = event.getValue("enabled_input").getAsString().toLowerCase().trim();
            String chanceValue = event.getValue("chance_input").getAsString();

            try {
                boolean enabled;
                if (enabledValue.equals("true") || enabledValue.equals("1") || enabledValue.equals("yes")) {
                    enabled = true;
                } else if (enabledValue.equals("false") || enabledValue.equals("0") || enabledValue.equals("no")) {
                    enabled = false;
                } else {
                    updateChancesDisplay(event, session, "❌ Enabled must be 'true' or 'false'!");
                    return;
                }

                double chance = Double.parseDouble(chanceValue);
                if (chance < 0 || chance > 100) {
                    updateChancesDisplay(event, session, "❌ Chance must be between 0 and 100!");
                    return;
                }

                session.updateSource(imageType, enabled, chance);
                updateChancesDisplay(event, session, 
                        String.format("✅ %s updated: %s, %.1f%% chance!", 
                                formatSourceName(imageType),
                                enabled ? "Enabled" : "Disabled",
                                chance));
                
            } catch (NumberFormatException e) {
                updateChancesDisplay(event, session, "❌ Invalid number format!");
            }
        });
    }

    private void initializeDefaultOptions(User user) {
        List<ImageOptions> defaultOptions = ImageOptions.getDefaultOptions();
        for (ImageOptions option : defaultOptions) {
            user.setChances(option);
        }
        Main.getUserService().updateUser(user);
    }

    private EmbedBuilder createChancesEmbed(ChancesSession session, net.dv8tion.jda.api.entities.User discordUser) {
        EmbedBuilder embed = new EmbedBuilder();

        embed.setTitle("🎲 Image Source Configuration");
        embed.setColor(PRIMARY_COLOR);
        embed.setTimestamp(Instant.now());

        if (discordUser.getAvatarUrl() != null) {
            embed.setThumbnail(discordUser.getAvatarUrl());
        }

        embed.setDescription("Select a category, then choose a source to configure.\n" +
                "**Current Category:** " + session.getSelectedCategory());

        List<ImageOptions> categoryItems = session.getCategoryItems();
        
        if (categoryItems.isEmpty()) {
            embed.addField("📦 No Sources", "```No sources in this category.```", false);
        } else {
            StringBuilder sourceList = new StringBuilder("");
            for (ImageOptions option : categoryItems) {
                String status = option.isEnabled() ? "🟢" : "🔴";
                sourceList.append(String.format("%s %s %s - %.1f%%\n", 
                    status, getSourceEmoji(option.getImageType()), 
                    formatSourceName(option.getImageType()), option.getChance()));
            }
            embed.addField("📋 " + session.getSelectedCategory() + " Sources", sourceList.toString(), false);
        }

        Map<String, Integer> stats = session.getStatistics();
        embed.addField("📊 Statistics", 
                String.format("```Total: %d | Enabled: %d | Total Chance: %.1f%%```",
                        stats.get("total"), stats.get("enabled"), stats.get("totalChance") / 10.0), false);

        if (session.hasUnsavedChanges()) {
            embed.addField("⚠️ Status", "```Unsaved changes! Click Save to apply.```", false);
        }

        String lastSelected = session.getLastSelectedSource();
        if (lastSelected != null && !lastSelected.equals("none")) {
            ImageOptions selectedOption = session.getImageOption(lastSelected);
            if (selectedOption != null) {
                embed.addField("🎯 Selected Source", 
                        String.format("```%s %s %s\nChance: %.1f%% | Status: %s```",
                                selectedOption.isEnabled() ? "🟢" : "🔴",
                                getSourceEmoji(lastSelected),
                                formatSourceName(lastSelected),
                                selectedOption.getChance(),
                                selectedOption.isEnabled() ? "Enabled" : "Disabled"), false);
            }
        }

        return embed;
    }

    private List<ActionRow> createChancesComponents(ChancesSession session) {
        List<ActionRow> components = new ArrayList<>();

        // Category dropdown
        StringSelectMenu.Builder categoryMenu = StringSelectMenu.create("chances:category")
                .setPlaceholder("📂 Select category...")
                .addOption("🌐 All Sources", "all", "Show all image sources")
                .addOption("🖼️ Images", "images", "Image hosting and galleries")
                .addOption("🎬 Media", "media", "Movies, TV shows, videos")
                .addOption("🔞 NSFW", "nsfw", "Adult content sources")
                .addOption("📚 Text", "text", "Text-based content");

        components.add(ActionRow.of(categoryMenu.build()));

        // Source selector dropdown
        StringSelectMenu.Builder sourceMenu = StringSelectMenu.create("chances:source_select")
                .setPlaceholder("🎯 Select source to configure...");

        List<ImageOptions> categoryItems = session.getCategoryItems();
        if (!categoryItems.isEmpty()) {
            for (ImageOptions option : categoryItems) {
                String status = option.isEnabled() ? "🟢" : "🔴";
                String description = String.format("%.1f%% chance | %s",
                        option.getChance(), option.isEnabled() ? "Enabled" : "Disabled");

                sourceMenu.addOption(
                        String.format("%s %s", status, formatSourceName(option.getImageType())),
                        option.getImageType(),
                        description
                );
            }
        } else {
            sourceMenu.addOption("No sources in category", "none", "Select a different category");
            sourceMenu = sourceMenu.setDisabled(true);
        }

        components.add(ActionRow.of(sourceMenu.build()));

        // Selected source action buttons (if a source is selected)
        String lastSelectedSource = session.getLastSelectedSource();
        if (lastSelectedSource != null && !lastSelectedSource.equals("none")) {
            List<Button> sourceButtons = new ArrayList<>();
            ImageOptions selectedOption = session.getImageOption(lastSelectedSource);
            if (selectedOption != null) {
                String toggleText = selectedOption.isEnabled() ? "🔴 Disable" : "🟢 Enable";
                sourceButtons.add(Button.secondary("chances:toggle_" + lastSelectedSource, 
                        toggleText + " " + formatSourceName(lastSelectedSource)));
                sourceButtons.add(Button.primary("chances:edit_" + lastSelectedSource, 
                        "✏️ Edit " + formatSourceName(lastSelectedSource)));
            }
            if (!sourceButtons.isEmpty()) {
                components.add(ActionRow.of(sourceButtons));
            }
        }

        // Global action buttons
        List<Button> actionButtons = new ArrayList<>();
        actionButtons.add(Button.primary("chances:save", "💾 Save Changes")
                .withDisabled(!session.hasUnsavedChanges()));
        actionButtons.add(Button.success("chances:toggle_all_on", "🟢 Enable All"));
        actionButtons.add(Button.danger("chances:toggle_all_off", "🔴 Disable All"));
        actionButtons.add(Button.secondary("chances:reset", "🔄 Reset to Default"));

        components.add(ActionRow.of(actionButtons));

        return components;
    }

    private void handleSourceSelect(StringSelectInteractionEvent event, ChancesSession session, String imageType) {
        session.setLastSelectedSource(imageType);
        ImageOptions option = session.getImageOption(imageType);
        if (option == null) return;

        updateChancesDisplay(event, session, 
                String.format("📌 Selected: %s %s (%.1f%% chance) - Use buttons below to edit or toggle", 
                        getSourceEmoji(imageType), 
                        formatSourceName(imageType), 
                        option.getChance()));
    }

    private void handleToggleSource(ButtonInteractionEvent event, ChancesSession session, String imageType) {
        ImageOptions option = session.getImageOption(imageType);
        if (option == null) return;

        boolean newState = !option.isEnabled();
        session.updateSource(imageType, newState, option.getChance());
        
        updateChancesDisplay(event, session, 
                String.format("✅ %s %s %s", 
                        formatSourceName(imageType),
                        newState ? "enabled" : "disabled",
                        newState ? "🟢" : "🔴"));
    }

    private void handleEditSource(ButtonInteractionEvent event, ChancesSession session, String imageType) {
        ImageOptions option = session.getImageOption(imageType);
        if (option == null) return;

        TextInput enabledInput = TextInput.create("enabled_input", TextInputStyle.SHORT)
                .setPlaceholder("true or false")
                .setValue(String.valueOf(option.isEnabled()))
                .setRequiredRange(4, 5)
                .build();

        TextInput chanceInput = TextInput.create("chance_input", TextInputStyle.SHORT)
                .setPlaceholder("Enter chance percentage")
                .setValue(String.valueOf(option.getChance()))
                .setRequiredRange(1, 10)
                .build();

        Modal modal = Modal.create("chances:edit:" + imageType,
                        "✏️ Edit: " + formatSourceName(imageType))
                .addComponents(
                        Label.of("Discord Nitro Gift Link", enabledInput),
                        Label.of("Chance Percentage (0-100)", chanceInput)
                )
                .build();

        // Don't defer edit when showing modal - reply with modal directly
        event.replyModal(modal).queue();
    }

    private void handleToggleAll(ButtonInteractionEvent event, ChancesSession session, boolean enabled) {
        session.toggleAllSources(enabled);
        updateChancesDisplay(event, session, 
                String.format("✅ All sources %s", enabled ? "enabled" : "disabled"));
    }

    private void handleResetAll(ButtonInteractionEvent event, ChancesSession session) {
        session.resetToDefaults();
        updateChancesDisplay(event, session, "🔄 All sources reset to default values and chances!");
    }

    private void handleSaveChanges(ButtonInteractionEvent event, ChancesSession session) {
        try {
            session.saveChanges();
            Main.getUserService().updateUser(session.getUser());
            updateChancesDisplay(event, session, "✅ Changes saved successfully!");
        } catch (Exception e) {
            updateChancesDisplay(event, session, "❌ Failed to save changes.");
        }
    }

    private void updateChancesDisplay(ButtonInteractionEvent event, ChancesSession session) {
        updateChancesDisplay(event, session, null);
    }

    private void updateChancesDisplay(StringSelectInteractionEvent event, ChancesSession session) {
        updateChancesDisplay(event, session, null);
    }

    private void updateChancesDisplay(ModalInteractionEvent event, ChancesSession session) {
        updateChancesDisplay(event, session, null);
    }

    private void updateChancesDisplay(ButtonInteractionEvent event, ChancesSession session, String message) {
        EmbedBuilder embed = createChancesEmbed(session, event.getUser());
        if (message != null) {
            embed.addField("📢 Update", message, false);
        }
        List<ActionRow> components = createChancesComponents(session);
        event.getHook().editOriginalEmbeds(embed.build()).setComponents(components).queue();
    }

    private void updateChancesDisplay(StringSelectInteractionEvent event, ChancesSession session, String message) {
        EmbedBuilder embed = createChancesEmbed(session, event.getUser());
        if (message != null) {
            embed.addField("📢 Update", message, false);
        }
        List<ActionRow> components = createChancesComponents(session);
        event.getHook().editOriginalEmbeds(embed.build()).setComponents(components).queue();
    }

    private void updateChancesDisplay(ModalInteractionEvent event, ChancesSession session, String message) {
        EmbedBuilder embed = createChancesEmbed(session, event.getUser());
        if (message != null) {
            embed.addField("📢 Update", message, false);
        }
        List<ActionRow> components = createChancesComponents(session);
        event.getHook().editOriginalEmbeds(embed.build()).setComponents(components).queue();
    }

    private String formatSourceName(String imageType) {
        return switch (imageType.toLowerCase()) {
            case "reddit" -> "Reddit";
            case "imgur" -> "Imgur";
            case "4chan" -> "4Chan";
            case "picsum" -> "Picsum";
            case "rule34xxx" -> "Rule34";
            case "tenor" -> "Tenor";
            case "google" -> "Google";
            case "movies" -> "Movies";
            case "tvshow" -> "TV Shows";
            case "youtube" -> "YouTube";
            case "short" -> "YouTube Shorts";
            case "urban" -> "Urban Dictionary";
            default -> imageType.substring(0, 1).toUpperCase() + imageType.substring(1);
        };
    }

    private String getSourceEmoji(String imageType) {
        return switch (imageType.toLowerCase()) {
            case "reddit" -> Emoji.REDDIT_LOGO.getFormat();
            case "imgur" -> Emoji.IMGUR_LOGO.getFormat();
            case "4chan" -> Emoji._4CHAN_LOGO.getFormat();
            case "picsum" -> "🖼️";
            case "rule34xxx" -> "🔞";
            case "tenor" -> Emoji.TENOR_LOGO.getFormat();
            case "google" -> Emoji.GOOGLE_LOGO.getFormat();
            case "movies" -> "🎬";
            case "tvshow" -> "📺";
            case "youtube" -> Emoji.YT_LOGO.getFormat();
            case "short" -> Emoji.YT_SHORTS_LOGO.getFormat();
            case "urban" -> Emoji.URBAN_DICTIONARY_LOGO.getFormat();
            default -> "🎲";
        };
    }

    // Simplified session class
    private static class ChancesSession {
        private final User user;
        private final Map<String, ImageOptions> workingOptions;
        private String selectedCategory;
        private String lastSelectedSource;
        private boolean hasUnsavedChanges;

        public ChancesSession(User user) {
            this.user = user;
            this.workingOptions = new HashMap<>();
            
            for (Map.Entry<String, ImageOptions> entry : user.getImageOptionsMap().entrySet()) {
                ImageOptions original = entry.getValue();
                this.workingOptions.put(entry.getKey(), 
                    new ImageOptions(original.getImageType(), original.isEnabled(), original.getChance()));
            }
            
            this.selectedCategory = "All Sources";
            this.lastSelectedSource = null;
            this.hasUnsavedChanges = false;
        }

        public void setSelectedCategory(String category) {
            this.selectedCategory = switch (category) {
                case "all" -> "All Sources";
                case "images" -> "Images";
                case "media" -> "Media";
                case "nsfw" -> "NSFW";
                case "text" -> "Text";
                default -> "All Sources";
            };
        }

        public List<ImageOptions> getCategoryItems() {
            return workingOptions.values().stream()
                    .filter(option -> isInCategory(option.getImageType(), selectedCategory))
                    .sorted((a, b) -> formatSourceName(a.getImageType()).compareTo(formatSourceName(b.getImageType())))
                    .toList();
        }

        private boolean isInCategory(String imageType, String category) {
            return switch (category) {
                case "All Sources" -> true;
                case "Images" -> List.of("reddit", "imgur", "4chan", "picsum", "google").contains(imageType);
                case "Media" -> List.of("movies", "tvshow", "youtube", "short", "tenor").contains(imageType);
                case "NSFW" -> List.of("rule34xxx").contains(imageType);
                case "Text" -> List.of("urban").contains(imageType);
                default -> true;
            };
        }

        public void updateSource(String imageType, boolean enabled, double chance) {
            ImageOptions option = workingOptions.get(imageType);
            if (option != null) {
                option.setEnabled(enabled);
                option.setChance(chance);
                hasUnsavedChanges = true;
            }
        }

        public void toggleAllSources(boolean enabled) {
            for (ImageOptions option : workingOptions.values()) {
                option.setEnabled(enabled);
            }
            hasUnsavedChanges = true;
        }

        public void resetToDefaults() {
            List<ImageOptions> defaultOptions = ImageOptions.getDefaultOptions();
            workingOptions.clear();
            
            for (ImageOptions defaultOption : defaultOptions) {
                workingOptions.put(defaultOption.getImageType(), 
                    new ImageOptions(defaultOption.getImageType(), defaultOption.isEnabled(), defaultOption.getChance()));
            }
            
            hasUnsavedChanges = true;
        }

        public void saveChanges() {
            for (ImageOptions option : workingOptions.values()) {
                user.setChances(option);
            }
            hasUnsavedChanges = false;
        }

        public Map<String, Integer> getStatistics() {
            Map<String, Integer> stats = new HashMap<>();
            int total = workingOptions.size();
            int enabled = (int) workingOptions.values().stream().filter(ImageOptions::isEnabled).count();
            double totalChance = workingOptions.values().stream()
                    .filter(ImageOptions::isEnabled)
                    .mapToDouble(ImageOptions::getChance)
                    .sum();

            stats.put("total", total);
            stats.put("enabled", enabled);
            stats.put("totalChance", (int) (totalChance * 10));

            return stats;
        }

        private String formatSourceName(String imageType) {
            return switch (imageType.toLowerCase()) {
                case "reddit" -> "Reddit";
                case "imgur" -> "Imgur";
                case "4chan" -> "4Chan";
                case "picsum" -> "Picsum";
                case "rule34xxx" -> "Rule34";
                case "tenor" -> "Tenor";
                case "google" -> "Google";
                case "movies" -> "Movies";
                case "tvshow" -> "TV Shows";
                case "youtube" -> "YouTube";
                case "short" -> "YouTube Shorts";
                case "urban" -> "Urban Dictionary";
                default -> imageType.substring(0, 1).toUpperCase() + imageType.substring(1);
            };
        }

        public void setLastSelectedSource(String source) {
            this.lastSelectedSource = source;
        }

        // Getters 
        public User getUser() { return user; }
        public String getSelectedCategory() { return selectedCategory; }
        public String getLastSelectedSource() { return lastSelectedSource; }
        public boolean hasUnsavedChanges() { return hasUnsavedChanges; }
        public ImageOptions getImageOption(String imageType) { return workingOptions.get(imageType); }
    }
}