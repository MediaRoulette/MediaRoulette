package me.hash.mediaroulette.bot.commands.config;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.commands.BaseCommand;
import me.hash.mediaroulette.bot.utils.CommandCooldown;
import me.hash.mediaroulette.bot.utils.ContainerFactory;
import me.hash.mediaroulette.bot.utils.Emoji;
import me.hash.mediaroulette.model.ImageOptions;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.locale.LocaleManager;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.modals.Modal;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Chances command using modern container-based UI.
 * Configure image source chances with visual category filters and search.
 */
public class ChancesCommand extends BaseCommand {

    private static final Color PRIMARY_COLOR = new Color(88, 101, 242);
    private static final Map<Long, ChancesSession> USER_SESSIONS = new ConcurrentHashMap<>();

    // Metadata Registry
    private static final Map<String, SourceMetadata> SOURCE_METADATA = new HashMap<>();

    static {
        registerSource("reddit", "Reddit", Emoji.REDDIT_LOGO.getFormat(), "Images");
        registerSource("imgur", "Imgur", Emoji.IMGUR_LOGO.getFormat(), "Images");
        registerSource("4chan", "4Chan", Emoji._4CHAN_LOGO.getFormat(), "Images");
        registerSource("picsum", "Picsum", "🖼️", "Images");
        registerSource("google", "Google", Emoji.GOOGLE_LOGO.getFormat(), "Images");
        
        registerSource("movies", "Movies", "🎬", "Media");
        registerSource("tvshow", "TV Shows", "📺", "Media");
        registerSource("youtube", "YouTube", Emoji.YT_LOGO.getFormat(), "Media");
        registerSource("short", "YouTube Shorts", Emoji.YT_SHORTS_LOGO.getFormat(), "Media");
        registerSource("tenor", "Tenor", Emoji.TENOR_LOGO.getFormat(), "Media");
        
        registerSource("booru", "Booru", "🔞", "NSFW");
        registerSource("urban", "Urban Dictionary", Emoji.URBAN_DICTIONARY_LOGO.getFormat(), "Text");
    }

    public static void registerSource(String key, String name, String emoji, String category) {
        SOURCE_METADATA.put(key, new SourceMetadata(name, emoji, category));
    }
    
    /**
     * Get source display names for autocomplete.
     * @return Map of source key to display name
     */
    public static Map<String, String> getSourceDisplayNames() {
        Map<String, String> names = new HashMap<>();
        SOURCE_METADATA.forEach((key, meta) -> names.put(key, meta.displayName()));
        return names;
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash("chances", "🎲 Configure image source chances by category")
                .addOptions(
                        new OptionData(OptionType.STRING, "search", "🔍 Search for a specific source by name")
                                .setAutoComplete(true),
                        new OptionData(OptionType.STRING, "category", "📂 Filter by category")
                                .addChoice("🌐 All Sources", "all")
                                .addChoice("🖼️ Images", "images")
                                .addChoice("🎬 Media", "media")
                                .addChoice("🔞 NSFW", "nsfw")
                                .addChoice("📚 Text", "text"),
                        new OptionData(OptionType.STRING, "status", "🔘 Filter by enabled/disabled status")
                                .addChoice("🟢 Enabled Only", "enabled")
                                .addChoice("🔴 Disabled Only", "disabled")
                )
                .setIntegrationTypes(IntegrationType.ALL)
                .setContexts(InteractionContextType.ALL);
    }

    @Override
    @CommandCooldown(value = 3, commands = {"chances"})
    public void handleCommand(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("chances")) return;

        event.deferReply().queue();
        Main.getBot().getExecutor().execute(() -> {
            ChancesSession session = getOrCreateSession(event.getUser());
            
            // Apply command options if provided
            OptionMapping searchOpt = event.getOption("search");
            OptionMapping categoryOpt = event.getOption("category");
            OptionMapping statusOpt = event.getOption("status");
            
            String message = null;
            
            if (searchOpt != null) {
                session.setSearchQuery(searchOpt.getAsString());
                message = "🔍 Searching for: " + searchOpt.getAsString();
            }
            if (categoryOpt != null) {
                session.setSelectedCategory(categoryOpt.getAsString());
            }
            if (statusOpt != null) {
                session.setStatusFilter(statusOpt.getAsString());
                message = (message != null ? message + " | " : "") + 
                        ("enabled".equals(statusOpt.getAsString()) ? "🟢 Showing enabled" : "🔴 Showing disabled");
            }
            
            updateDisplay(event.getHook(), session, event.getUser(), message);
        });
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("chances:")) return;
        if (!validateUser(event)) return;

        String action = event.getComponentId().split(":")[1];

        // Direct Modal Response (No Defer)
        if (action.startsWith("edit_")) {
            Main.getBot().getExecutor().execute(() -> handleEditRequest(event, action.substring(5)));
            return;
        } else if (action.equals("search")) {
            Main.getBot().getExecutor().execute(() -> handleSearchRequest(event));
            return;
        }

        event.deferEdit().queue();
        Main.getBot().getExecutor().execute(() -> {
            ChancesSession session = getOrCreateSession(event.getUser());
            String message = switch (action) {
                case "reset" -> session.resetToDefaults();
                case "save" -> session.saveChanges();
                case "toggle_all_on" -> session.toggleAll(true);
                case "toggle_all_off" -> session.toggleAll(false);
                case "clear_filters" -> { session.clearAllFilters(); yield "✨ All filters cleared."; }
                // Quick Filter Buttons
                case "filter_enabled" -> session.toggleStatusFilter("enabled");
                case "filter_disabled" -> session.toggleStatusFilter("disabled");
                case "filter_high" -> session.toggleChanceFilter("high");
                case "filter_low" -> session.toggleChanceFilter("low");
                default -> action.startsWith("toggle_") ? session.toggleSource(action.substring(7)) : null;
            };
            updateDisplay(event.getHook(), session, event.getUser(), message);
        });
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().startsWith("chances:")) return;
        if (!validateUser(event)) return;

        event.deferEdit().queue();
        Main.getBot().getExecutor().execute(() -> {
            ChancesSession session = getOrCreateSession(event.getUser());
            String componentId = event.getComponentId();
            String value = event.getValues().getFirst();
            String message = null;

            if (componentId.equals("chances:category")) {
                session.setSelectedCategory(value);
            } else if (componentId.equals("chances:source_select") && !value.equals("none")) {
                session.setLastSelectedSource(value);
                SourceMetadata meta = SOURCE_METADATA.get(value);
                ImageOptions opt = session.getImageOption(value);
                if (meta != null && opt != null) {
                    message = String.format("📌 Selected: %s %s (%.1f%% chance)", 
                            meta.emoji(), meta.displayName(), opt.getChance());
                }
            }
            updateDisplay(event.getHook(), session, event.getUser(), message);
        });
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getModalId().startsWith("chances:edit:")) {
            event.deferEdit().queue();
            Main.getBot().getExecutor().execute(() -> {
                ChancesSession session = getOrCreateSession(event.getUser());
                String imageType = event.getModalId().split(":")[2];
                String message = handleModalInput(event, session, imageType);
                updateDisplay(event.getHook(), session, event.getUser(), message);
            });
        } else if (event.getModalId().equals("chances:search_modal")) {
            event.deferEdit().queue();
            Main.getBot().getExecutor().execute(() -> {
                ChancesSession session = getOrCreateSession(event.getUser());
                
                // Get search query
                String query = event.getValue("search_query") != null 
                        ? event.getValue("search_query").getAsString().trim() 
                        : "";
                
                // Get category filter
                net.dv8tion.jda.api.interactions.modals.ModalMapping catMapping = event.getValue("category_filter");
                String categoryFilter = "";
                if (catMapping != null) {
                    List<String> values = catMapping.getAsStringList();
                    if (!values.isEmpty()) categoryFilter = values.get(0).trim().toLowerCase();
                }
                
                // Get status filter
                net.dv8tion.jda.api.interactions.modals.ModalMapping statusMapping = event.getValue("status_filter");
                String statusFilter = "";
                if (statusMapping != null) {
                    List<String> values = statusMapping.getAsStringList();
                    if (!values.isEmpty()) statusFilter = values.get(0).trim().toLowerCase();
                }
                
                // Get chance filter
                String chanceFilter = event.getValue("chance_filter") != null 
                        ? event.getValue("chance_filter").getAsString().trim() 
                        : "";
                
                // Apply filters
                StringBuilder appliedFilters = new StringBuilder("🔍 Filters applied:");
                boolean hasFilters = false;
                
                if (!query.isEmpty()) {
                    session.setSearchQuery(query);
                    appliedFilters.append(" **Search:** `").append(query).append("`");
                    hasFilters = true;
                } else {
                    session.setSearchQuery(null);
                }
                
                if (!categoryFilter.isEmpty() && isValidCategory(categoryFilter)) {
                    session.setSelectedCategory(categoryFilter);
                    appliedFilters.append(hasFilters ? " |" : "").append(" **Category:** `").append(categoryFilter).append("`");
                    hasFilters = true;
                }
                
                if (!statusFilter.isEmpty() && (statusFilter.equals("enabled") || statusFilter.equals("disabled"))) {
                    session.setStatusFilter(statusFilter);
                    appliedFilters.append(hasFilters ? " |" : "").append(" **Status:** `").append(statusFilter).append("`");
                    hasFilters = true;
                } else {
                    session.setStatusFilter(null);
                }
                
                if (!chanceFilter.isEmpty()) {
                    Double chanceValue = parseChanceFilter(chanceFilter);
                    if (chanceValue != null) {
                        session.setChanceFilter(chanceFilter);
                        appliedFilters.append(hasFilters ? " |" : "").append(" **Chance:** `").append(chanceFilter).append("`");
                        hasFilters = true;
                    }
                } else {
                    session.setChanceFilter(null);
                }
                
                String message = hasFilters ? appliedFilters.toString() : "No filters applied.";
                updateDisplay(event.getHook(), session, event.getUser(), message);
            });
        }
    }
    
    private boolean isValidCategory(String category) {
        return Set.of("all", "images", "media", "nsfw", "text").contains(category);
    }
    
    private Double parseChanceFilter(String filter) {
        try {
            filter = filter.trim();
            if (filter.startsWith(">") || filter.startsWith("<")) {
                return Double.parseDouble(filter.substring(1).trim());
            } else {
                return Double.parseDouble(filter);
            }
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // --- Handlers & Helpers ---

    private void handleEditRequest(ButtonInteractionEvent event, String imageType) {
        ChancesSession session = getOrCreateSession(event.getUser());
        ImageOptions option = session.getImageOption(imageType);
        SourceMetadata meta = SOURCE_METADATA.get(imageType);
        
        if (option == null || meta == null) return;

        // Use Select Menu for Boolean Option
        StringSelectMenu enabledMenu = StringSelectMenu.create("enabled_select")
                .setPlaceholder("Enable or Disable?")
                .addOption("Enabled", "true", "Enable this source", net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🟢"))
                .addOption("Disabled", "false", "Disable this source", net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🔴"))
                .setDefaultValues(String.valueOf(option.isEnabled()))
                .build();

        TextInput chanceInput = TextInput.create("chance_input", TextInputStyle.SHORT)
                .setPlaceholder("Enter a number, e.g. 50.5")
                .setValue(String.valueOf(option.getChance()))
                .setRequiredRange(1, 6)
                .build();

        Modal modal = Modal.create("chances:edit:" + imageType, "✏️ Edit: " + meta.displayName())
                .addComponents(Label.of("Enabled?", enabledMenu), Label.of("Chance Percentage (0-100)", chanceInput))
                .build();

        event.replyModal(modal).queue();
    }

    private String handleModalInput(ModalInteractionEvent event, ChancesSession session, String imageType) {
        // Retrieve Select Menu Value
        String enabledStr = "false";
        net.dv8tion.jda.api.interactions.modals.ModalMapping enabledMapping = event.getValue("enabled_select");
        if (enabledMapping != null) {
            List<String> values = enabledMapping.getAsStringList();
            if (!values.isEmpty()) enabledStr = values.get(0);
        }
        
        String chanceStr = event.getValue("chance_input").getAsString();
        SourceMetadata meta = SOURCE_METADATA.get(imageType);
        String name = meta != null ? meta.displayName() : imageType;

        try {
            boolean enabled = parseBoolean(enabledStr);
            double chance = Double.parseDouble(chanceStr);

            if (chance < 0 || chance > 100) return "❌ Chance must be between 0 and 100!";

            session.updateSource(imageType, enabled, chance);
            return String.format("✅ %s updated: %s, %.1f%% chance!", name, enabled ? "Enabled" : "Disabled", chance);
        } catch (IllegalArgumentException e) {
            return "❌ " + e.getMessage();
        }
    }

    private void handleSearchRequest(ButtonInteractionEvent event) {
        ChancesSession session = getOrCreateSession(event.getUser());
        
        // 1. Search Query (Text Input)
        TextInput.Builder searchBuilder = TextInput.create("search_query", TextInputStyle.SHORT)
                .setPlaceholder("e.g., reddit, youtube, movies...")
                .setRequired(false);
        if (session.getSearchQuery() != null) {
            searchBuilder.setValue(session.getSearchQuery());
        }

        // 2. Category (Select Menu)
        String currentCategory = session.getSelectedCategoryKey();
        if (currentCategory == null || currentCategory.isEmpty()) currentCategory = "all";
        
        StringSelectMenu categoryMenu = StringSelectMenu.create("category_filter")
                .setPlaceholder("Select Category...")
                .addOption("🌐 All Sources", "all")
                .addOption("🖼️ Images", "images")
                .addOption("🎬 Media", "media")
                .addOption("🔞 NSFW", "nsfw")
                .addOption("📚 Text", "text")
                .setDefaultValues(currentCategory)
                .setMinValues(1)
                .setMaxValues(1)
                .build();
        
        // 3. Status (Select Menu)
        String currentStatus = session.getStatusFilter();
        if (currentStatus == null) currentStatus = "any";
        
        StringSelectMenu statusMenu = StringSelectMenu.create("status_filter")
                .setPlaceholder("Filter by Status...")
                .addOption("⚪ Any Status", "any")
                .addOption("🟢 Enabled Only", "enabled")
                .addOption("🔴 Disabled Only", "disabled")
                .setDefaultValues(currentStatus)
                .setMinValues(1)
                .setMaxValues(1)
                .build();
        
        // 4. Chance (Text Input)
        TextInput.Builder chanceBuilder = TextInput.create("chance_filter", TextInputStyle.SHORT)
                .setPlaceholder("e.g., >25, <10, 50")
                .setRequired(false);
        if (session.getChanceFilter() != null) {
            chanceBuilder.setValue(session.getChanceFilter());
        }

        Modal modal = Modal.create("chances:search_modal", "🔍 Search & Filter Sources")
                .addComponents(
                        Label.of("Search Query", searchBuilder.build()),
                        Label.of("Category", categoryMenu),
                        Label.of("Status", statusMenu),
                        Label.of("Chance (e.g., >50, <10)", chanceBuilder.build())
                )
                .build();

        event.replyModal(modal).queue();
    }

    private boolean parseBoolean(String value) {
        if (Set.of("true", "1", "yes").contains(value)) return true;
        if (Set.of("false", "0", "no").contains(value)) return false;
        throw new IllegalArgumentException("Invalid boolean value! Use true/false.");
    }

    private boolean validateUser(GenericInteractionCreateEvent event) {
        String eventUserId = event.getUser().getId();

        if (event instanceof ButtonInteractionEvent bie) {
             if (!bie.getMessage().getInteractionMetadata().getUser().getId().equals(eventUserId)) {
                 bie.reply(LocaleManager.getInstance(Main.getUserService().getOrCreateUser(eventUserId).getLocale())
                         .get("error.not_your_menu")).setEphemeral(true).queue();
                 return false;
             }
        } else if (event instanceof StringSelectInteractionEvent ssie) {
            if (!ssie.getMessage().getInteractionMetadata().getUser().getId().equals(eventUserId)) {
                ssie.reply(LocaleManager.getInstance(Main.getUserService().getOrCreateUser(eventUserId).getLocale())
                        .get("error.not_your_menu")).setEphemeral(true).queue();
                return false;
            }
        }
        return true;
    }

    private ChancesSession getOrCreateSession(net.dv8tion.jda.api.entities.User discordUser) {
        return USER_SESSIONS.computeIfAbsent(discordUser.getIdLong(), k -> {
            User user = Main.getUserService().getOrCreateUser(discordUser.getId());
            if (user.getImageOptionsMap().isEmpty()) {
                initializeDefaultOptions(user);
            }
            return new ChancesSession(user);
        });
    }

    private void initializeDefaultOptions(User user) {
        ImageOptions.getDefaultOptions().forEach(user::setChances);
        Main.getUserService().updateUser(user);
    }

    // --- UI Generation (Container-based) ---

    private void updateDisplay(InteractionHook hook, ChancesSession session, net.dv8tion.jda.api.entities.User user, String statusMessage) {
        Container container = createContainer(session, user, statusMessage);
        List<ActionRow> componentRows = createComponentRows(session);
        
        if (hook.isExpired()) return; 
        
        // Build combined component list: container first, then action rows
        List<MessageTopLevelComponent> allComponents = new ArrayList<>();
        allComponents.add(container);
        allComponents.addAll(componentRows);
        
        hook.editOriginalComponents(allComponents).useComponentsV2().queue(null, e -> {});
    }

    /**
     * Create the main container with source configuration display.
     */
    private Container createContainer(ChancesSession session, net.dv8tion.jda.api.entities.User user, String statusMessage) {
        List<ContainerChildComponent> components = new ArrayList<>();
        
        // Header Section
        components.add(Section.of(
                Thumbnail.fromUrl(user.getEffectiveAvatarUrl()),
                TextDisplay.of("## 🎲 Image Source Configuration"),
                TextDisplay.of("**Select a category, search, or use quick filters to configure sources**")
        ));
        
        components.add(Separator.createDivider(Separator.Spacing.SMALL));
        
        // Active Filters Display
        String activeFilters = session.getActiveFiltersDescription();
        if (!activeFilters.isEmpty()) {
            components.add(TextDisplay.of("### 🏷️ Active Filters\n" + activeFilters));
            components.add(Separator.createDivider(Separator.Spacing.SMALL));
        }
        
        // Source List
        List<ImageOptions> items = session.getCategoryItems();
        
        String listTitle = session.hasSearchQuery() 
                ? "🔍 Search Results: \"" + session.getSearchQuery() + "\"" 
                : "📋 " + session.getSelectedCategoryName() + " Sources";

        if (items.isEmpty()) {
            components.add(TextDisplay.of("### " + listTitle + "\n*No sources found matching your filters.*"));
        } else {
            StringBuilder sourceContent = new StringBuilder();
            sourceContent.append("### ").append(listTitle).append("\n");
            
            for (ImageOptions opt : items) {
                SourceMetadata meta = SOURCE_METADATA.getOrDefault(opt.getImageType(), 
                        new SourceMetadata(opt.getImageType(), "❓", "Unknown"));
                
                String statusIcon = opt.isEnabled() ? "🟢" : "🔴";
                String line = String.format("%s %s **%s** `%.1f%%`", 
                        statusIcon, meta.emoji(), meta.displayName(), opt.getChance());
                        
                sourceContent.append(line).append("\n");
            }
            
            components.add(TextDisplay.of(sourceContent.toString()));
        }
        
        components.add(Separator.createDivider(Separator.Spacing.SMALL));
        
        // Statistics
        Map<String, Number> stats = session.getStatistics();
        components.add(TextDisplay.of(String.format("### 📊 Statistics\n**Total:** %d sources | **Enabled:** %d | **Total Chance:** %.1f%%",
                stats.get("total"), stats.get("enabled"), stats.get("totalChance"))));
        
        // Unsaved Changes Banner
        if (session.hasUnsavedChanges()) {
            components.add(Separator.createDivider(Separator.Spacing.SMALL));
            components.add(TextDisplay.of("### ⚠️ Unsaved Changes\n*You have unsaved changes. Click **Save Changes** to apply them.*"));
        }
        
        // Status Message
        if (statusMessage != null) {
            components.add(Separator.createDivider(Separator.Spacing.SMALL));
            components.add(TextDisplay.of("📢 " + statusMessage));
        }
        
        // Selected Source Details
        String lastSel = session.getLastSelectedSource();
        if (lastSel != null && !session.hasSearchQuery()) {
            ImageOptions selOpt = session.getImageOption(lastSel);
            SourceMetadata selMeta = SOURCE_METADATA.get(lastSel);
            if (selOpt != null && selMeta != null) {
                components.add(Separator.createDivider(Separator.Spacing.SMALL));
                components.add(TextDisplay.of(String.format("### 🎯 Selected Source\n**%s %s**\nStatus: %s\nChance: `%.1f%%`", 
                        selMeta.emoji(), selMeta.displayName(), 
                        selOpt.isEnabled() ? "🟢 Enabled" : "🔴 Disabled",
                        selOpt.getChance())));
            }
        }
        
        return Container.of((java.util.Collection<? extends ContainerChildComponent>) components).withAccentColor(PRIMARY_COLOR);
    }

    /**
     * Create the component rows (select menus and buttons) that go outside the container.
     */
    private List<ActionRow> createComponentRows(ChancesSession session) {
        List<ActionRow> rows = new ArrayList<>();

        // 1. Category Menu (Disabled if searching)
        StringSelectMenu.Builder catMenu = StringSelectMenu.create("chances:category")
                .setPlaceholder("📂 Select category...")
                .addOption("🌐 All Sources", "all", "Show all image sources")
                .addOption("🖼️ Images", "images", "Image hosting and galleries")
                .addOption("🎬 Media", "media", "Movies, TV shows, videos")
                .addOption("🔞 NSFW", "nsfw", "Adult content sources")
                .addOption("📚 Text", "text", "Text-based content")
                .setDefaultValues(session.getSelectedCategoryKey())
                .setDisabled(session.hasSearchQuery());
                
        rows.add(ActionRow.of(catMenu.build()));

        // 2. Source Menu
        StringSelectMenu.Builder sourceMenu = StringSelectMenu.create("chances:source_select")
                .setPlaceholder("🎯 Select source to configure...");
        
        List<ImageOptions> items = session.getCategoryItems();
        if (items.isEmpty()) {
            sourceMenu.addOption("No sources found", "none", "Try a different category or search").setDisabled(true);
        } else {
             int count = 0;
            for (ImageOptions opt : items) {
                if (count >= 25) break; 
                SourceMetadata meta = SOURCE_METADATA.getOrDefault(opt.getImageType(), 
                        new SourceMetadata(opt.getImageType(), "❓", "Unknown"));
                sourceMenu.addOption(
                        String.format("%s %s", opt.isEnabled() ? "🟢" : "🔴", meta.displayName()),
                        opt.getImageType(),
                        String.format("%.1f%% chance", opt.getChance())
                );
                count++;
            }
        }
        rows.add(ActionRow.of(sourceMenu.build()));

        // 3. Quick Filter Buttons
        List<Button> filterButtons = new ArrayList<>();
        filterButtons.add(Button.secondary("chances:filter_enabled", 
                session.isStatusFilterActive("enabled") ? "✓ Enabled" : "🟢 Enabled")
                .withStyle(session.isStatusFilterActive("enabled") ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY));
        filterButtons.add(Button.secondary("chances:filter_disabled", 
                session.isStatusFilterActive("disabled") ? "✓ Disabled" : "🔴 Disabled")
                .withStyle(session.isStatusFilterActive("disabled") ? net.dv8tion.jda.api.components.buttons.ButtonStyle.DANGER : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY));
        filterButtons.add(Button.secondary("chances:filter_high", 
                session.isChanceFilterActive("high") ? "✓ High (>25%)" : "📈 High (>25%)")
                .withStyle(session.isChanceFilterActive("high") ? net.dv8tion.jda.api.components.buttons.ButtonStyle.PRIMARY : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY));
        filterButtons.add(Button.secondary("chances:filter_low", 
                session.isChanceFilterActive("low") ? "✓ Low (<10%)" : "📉 Low (<10%)")
                .withStyle(session.isChanceFilterActive("low") ? net.dv8tion.jda.api.components.buttons.ButtonStyle.PRIMARY : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY));
        rows.add(ActionRow.of(filterButtons));

        // 4. Selection Buttons (if a source is selected)
        String lastSel = session.getLastSelectedSource();
        if (lastSel != null) {
            ImageOptions selOpt = session.getImageOption(lastSel);
            SourceMetadata selMeta = SOURCE_METADATA.get(lastSel);
            if (selOpt != null && selMeta != null) {
                rows.add(ActionRow.of(
                        Button.secondary("chances:toggle_" + lastSel, 
                                (selOpt.isEnabled() ? "🔴 Disable " : "🟢 Enable ") + selMeta.displayName()),
                        Button.primary("chances:edit_" + lastSel, "✏️ Edit " + selMeta.displayName())
                ));
            }
        }

        // 5. Global Actions + Search + Clear
        List<Button> globalButtons = new ArrayList<>();
        globalButtons.add(Button.success("chances:save", "💾 Save").withDisabled(!session.hasUnsavedChanges()));
        globalButtons.add(Button.secondary("chances:search", "🔍 Search"));
        
        if (session.hasActiveFilters()) {
            globalButtons.add(Button.secondary("chances:clear_filters", "✖️ Clear Filters"));
        }
        
        globalButtons.add(Button.danger("chances:reset", "🔄 Reset"));
        
        rows.add(ActionRow.of(globalButtons));

        return rows;
    }

    // --- Inner Classes ---

    private record SourceMetadata(String displayName, String emoji, String category) {}

    private static class ChancesSession {
        private final User user;
        private final Map<String, ImageOptions> workingOptions = new HashMap<>();
        private String selectedCategoryKey = "all";
        private String lastSelectedSource = null;
        private boolean hasUnsavedChanges = false;
        private String searchQuery = null;
        private String statusFilter = null;  // "enabled" or "disabled"
        private String chanceFilter = null;  // e.g., ">25", "<10"

        public ChancesSession(User user) {
            this.user = user;
            resetFromUser();
        }

        private void resetFromUser() {
            workingOptions.clear();
            user.getImageOptionsMap().forEach((k, v) -> 
                workingOptions.put(k, new ImageOptions(v.getImageType(), v.isEnabled(), v.getChance())));
            hasUnsavedChanges = false;
            searchQuery = null;
            statusFilter = null;
            chanceFilter = null;
        }

        public void setSelectedCategory(String key) { 
            this.selectedCategoryKey = key; 
        }
        public String getSelectedCategoryKey() { return selectedCategoryKey; }
        
        public void setSearchQuery(String query) { 
            this.searchQuery = query != null && !query.trim().isEmpty() ? query.toLowerCase().trim() : null; 
        }
        public String getSearchQuery() { return searchQuery; }
        public boolean hasSearchQuery() { return searchQuery != null && !searchQuery.isEmpty(); }
        
        public void setStatusFilter(String filter) {
            this.statusFilter = filter;
        }
        public String getStatusFilter() { return statusFilter; }
        
        public void setChanceFilter(String filter) {
            this.chanceFilter = filter;
        }
        public String getChanceFilter() { return chanceFilter; }
        
        public boolean hasActiveFilters() {
            return hasSearchQuery() || statusFilter != null || chanceFilter != null;
        }
        
        public void clearAllFilters() {
            this.searchQuery = null;
            this.statusFilter = null;
            this.chanceFilter = null;
            this.selectedCategoryKey = "all";
        }
        
        public String toggleStatusFilter(String filter) {
            if (filter.equals(statusFilter)) {
                statusFilter = null;
                return "🔄 Status filter cleared.";
            } else {
                statusFilter = filter;
                return "enabled".equals(filter) ? "🟢 Showing enabled sources only." : "🔴 Showing disabled sources only.";
            }
        }
        
        public String toggleChanceFilter(String filter) {
            String currentActive = getActiveChanceFilterType();
            if (filter.equals(currentActive)) {
                chanceFilter = null;
                return "🔄 Chance filter cleared.";
            } else {
                chanceFilter = "high".equals(filter) ? ">25" : "<10";
                return "high".equals(filter) ? "📈 Showing high chance sources (>25%)." : "📉 Showing low chance sources (<10%).";
            }
        }
        
        public boolean isStatusFilterActive(String filter) {
            return filter.equals(statusFilter);
        }
        
        public boolean isChanceFilterActive(String filter) {
            if (chanceFilter == null) return false;
            if ("high".equals(filter)) return chanceFilter.startsWith(">");
            if ("low".equals(filter)) return chanceFilter.startsWith("<");
            return false;
        }
        
        private String getActiveChanceFilterType() {
            if (chanceFilter == null) return null;
            if (chanceFilter.startsWith(">")) return "high";
            if (chanceFilter.startsWith("<")) return "low";
            return null;
        }
        
        public String getActiveFiltersDescription() {
            List<String> filters = new ArrayList<>();
            if (hasSearchQuery()) {
                filters.add("🔍 Search: `" + searchQuery + "`");
            }
            if (statusFilter != null) {
                filters.add("enabled".equals(statusFilter) ? "🟢 Enabled only" : "🔴 Disabled only");
            }
            if (chanceFilter != null) {
                filters.add("📊 Chance: `" + chanceFilter + "`");
            }
            return String.join(" | ", filters);
        }

        public String getSelectedCategoryName() {
            return switch (selectedCategoryKey) {
                case "images" -> "Images";
                case "media" -> "Media";
                case "nsfw" -> "NSFW";
                case "text" -> "Text";
                default -> "All Sources";
            };
        }

        public void setLastSelectedSource(String source) { this.lastSelectedSource = source; }
        public String getLastSelectedSource() { return lastSelectedSource; }
        public ImageOptions getImageOption(String type) { return workingOptions.get(type); }
        public boolean hasUnsavedChanges() { return hasUnsavedChanges; }
        public User getUser() { return user; }

        public List<ImageOptions> getCategoryItems() {
            return workingOptions.values().stream()
                    .filter(opt -> {
                        SourceMetadata meta = SOURCE_METADATA.get(opt.getImageType());
                        String name = meta != null ? meta.displayName() : opt.getImageType();
                        
                        // Apply search filter
                        if (hasSearchQuery()) {
                            if (!name.toLowerCase().contains(searchQuery) && !opt.getImageType().toLowerCase().contains(searchQuery)) {
                                return false;
                            }
                        }
                        
                        // Apply category filter
                        if (!selectedCategoryKey.equals("all")) {
                            if (meta == null || !meta.category().equalsIgnoreCase(getSelectedCategoryName())) {
                                return false;
                            }
                        }
                        
                        // Apply status filter
                        if (statusFilter != null) {
                            boolean isEnabled = opt.isEnabled();
                            if ("enabled".equals(statusFilter) && !isEnabled) return false;
                            if ("disabled".equals(statusFilter) && isEnabled) return false;
                        }
                        
                        // Apply chance filter
                        if (chanceFilter != null) {
                            double chance = opt.getChance();
                            if (chanceFilter.startsWith(">")) {
                                double threshold = Double.parseDouble(chanceFilter.substring(1).trim());
                                if (chance <= threshold) return false;
                            } else if (chanceFilter.startsWith("<")) {
                                double threshold = Double.parseDouble(chanceFilter.substring(1).trim());
                                if (chance >= threshold) return false;
                            } else {
                                double exactValue = Double.parseDouble(chanceFilter.trim());
                                if (Math.abs(chance - exactValue) > 0.1) return false;
                            }
                        }
                        
                        return true;
                    })
                    .sorted(Comparator.comparing(a -> SOURCE_METADATA.getOrDefault(a.getImageType(), 
                            new SourceMetadata(a.getImageType(), "", "")).displayName()))
                    .collect(Collectors.toList());
        }

        public void updateSource(String type, boolean enabled, double chance) {
            ImageOptions opt = workingOptions.get(type);
            if (opt != null) {
                opt.setEnabled(enabled);
                opt.setChance(chance);
                hasUnsavedChanges = true;
            }
        }

        public String toggleSource(String type) {
            ImageOptions opt = workingOptions.get(type);
            if (opt != null) {
                updateSource(type, !opt.isEnabled(), opt.getChance());
                SourceMetadata meta = SOURCE_METADATA.get(type);
                return String.format("✅ %s %s %s", meta.displayName(), 
                        opt.isEnabled() ? "enabled" : "disabled", opt.isEnabled() ? "🟢" : "🔴");
            }
            return null;
        }

        public String toggleAll(boolean enabled) {
            workingOptions.values().forEach(opt -> opt.setEnabled(enabled));
            hasUnsavedChanges = true;
            return String.format("✅ All sources %s", enabled ? "enabled" : "disabled");
        }

        public String resetToDefaults() {
            workingOptions.clear();
            ImageOptions.getDefaultOptions().forEach(opt -> 
                workingOptions.put(opt.getImageType(), new ImageOptions(opt.getImageType(), opt.isEnabled(), opt.getChance())));
            hasUnsavedChanges = true;
            clearAllFilters();
            return "🔄 All sources reset to default values!";
        }

        public String saveChanges() {
            try {
                workingOptions.values().forEach(user::setChances);
                Main.getUserService().updateUser(user);
                hasUnsavedChanges = false;
                return "✅ Changes saved successfully!";
            } catch (Exception e) {
                return "❌ Failed to save changes.";
            }
        }

        public Map<String, Number> getStatistics() {
            long enabled = workingOptions.values().stream().filter(ImageOptions::isEnabled).count();
            double totalChance = workingOptions.values().stream()
                    .filter(ImageOptions::isEnabled)
                    .mapToDouble(ImageOptions::getChance)
                    .sum();
            return Map.of("total", workingOptions.size(), "enabled", (int)enabled, "totalChance", totalChance);
        }
    }
}