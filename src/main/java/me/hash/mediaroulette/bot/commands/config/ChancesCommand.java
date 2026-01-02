package me.hash.mediaroulette.bot.commands.config;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.commands.BaseCommand;
import me.hash.mediaroulette.bot.utils.CommandCooldown;
import me.hash.mediaroulette.bot.utils.Emoji;
import me.hash.mediaroulette.model.ImageOptions;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.locale.LocaleManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.modals.Modal;

import java.awt.Color;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
        
        registerSource("rule34xxx", "Rule34", "🔞", "NSFW");
        registerSource("urban", "Urban Dictionary", Emoji.URBAN_DICTIONARY_LOGO.getFormat(), "Text");
    }

    private static void registerSource(String key, String name, String emoji, String category) {
        SOURCE_METADATA.put(key, new SourceMetadata(name, emoji, category));
    }

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
            ChancesSession session = getOrCreateSession(event.getUser());
            updateDisplay(event.getHook(), session, event.getUser(), null);
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
        }

        event.deferEdit().queue();
        Main.getBot().getExecutor().execute(() -> {
            ChancesSession session = getOrCreateSession(event.getUser());
            String message = switch (action) {
                case "reset" -> session.resetToDefaults();
                case "save" -> session.saveChanges();
                case "toggle_all_on" -> session.toggleAll(true);
                case "toggle_all_off" -> session.toggleAll(false);
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
                    message = String.format("📌 Selected: %s %s (%.1f%% chance) - Use buttons below to edit or toggle", 
                            meta.emoji(), meta.displayName(), opt.getChance());
                }
            }
            updateDisplay(event.getHook(), session, event.getUser(), message);
        });
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().startsWith("chances:edit:")) return;

        event.deferEdit().queue();
        Main.getBot().getExecutor().execute(() -> {
            ChancesSession session = getOrCreateSession(event.getUser());
            String imageType = event.getModalId().split(":")[2];
            String message = handleModalInput(event, session, imageType);
            updateDisplay(event.getHook(), session, event.getUser(), message);
        });
    }

    // --- Handlers & Helpers ---

    private void handleEditRequest(ButtonInteractionEvent event, String imageType) {
        ChancesSession session = getOrCreateSession(event.getUser());
        ImageOptions option = session.getImageOption(imageType);
        SourceMetadata meta = SOURCE_METADATA.get(imageType);
        
        if (option == null || meta == null) return;

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

        Modal modal = Modal.create("chances:edit:" + imageType, "✏️ Edit: " + meta.displayName())
                .addComponents(Label.of("Enabled (true/false)", enabledInput), Label.of("Chance (0-100)", chanceInput))
                .build();

        event.replyModal(modal).queue();
    }

    private String handleModalInput(ModalInteractionEvent event, ChancesSession session, String imageType) {
        String enabledStr = event.getValue("enabled_input").getAsString().toLowerCase().trim();
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

    // --- UI Generation ---

    private void updateDisplay(InteractionHook hook, ChancesSession session, net.dv8tion.jda.api.entities.User user, String statusMessage) {
        MessageEmbed embed = createEmbed(session, user, statusMessage);
        List<ActionRow> components = createComponents(session);
        
        if (hook.isExpired()) return; 
        
        // editOriginalEmbeds is preferred for interaction responses
        hook.editOriginalEmbeds(embed).setComponents(components).queue(null, e -> {}); 
    }

    private MessageEmbed createEmbed(ChancesSession session, net.dv8tion.jda.api.entities.User user, String statusMessage) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎲 Image Source Configuration")
                .setColor(PRIMARY_COLOR)
                .setTimestamp(Instant.now())
                .setThumbnail(user.getAvatarUrl())
                .setDescription("Select a category, then choose a source to configure.\n**Current Category:** " + session.getSelectedCategoryName());

        // Source List
        List<ImageOptions> items = session.getCategoryItems();
        if (items.isEmpty()) {
            embed.addField("📦 No Sources", "```No sources in this category.```", false);
        } else {
            StringBuilder sb = new StringBuilder();
            for (ImageOptions opt : items) {
                SourceMetadata meta = SOURCE_METADATA.getOrDefault(opt.getImageType(), new SourceMetadata(opt.getImageType(), "❓", "Unknown"));
                sb.append(String.format("%s %s %s - %.1f%%\n", 
                        opt.isEnabled() ? "🟢" : "🔴", meta.emoji(), meta.displayName(), opt.getChance()));
            }
            embed.addField("📋 " + session.getSelectedCategoryName() + " Sources", sb.toString(), false);
        }

        // Stats
        Map<String, Number> stats = session.getStatistics();
        embed.addField("📊 Statistics", String.format("```Total: %d | Enabled: %d | Total Chance: %.1f%%```",
                stats.get("total"), stats.get("enabled"), stats.get("totalChance")), false);

        // Footer / Status
        if (session.hasUnsavedChanges()) {
            embed.addField("⚠️ Status", "```Unsaved changes! Click Save to apply.```", false);
        }
        if (statusMessage != null) {
            embed.addField("📢 Update", statusMessage, false);
        }

        // Selection Detail
        String lastSel = session.getLastSelectedSource();
        if (lastSel != null) {
            ImageOptions selOpt = session.getImageOption(lastSel);
            SourceMetadata selMeta = SOURCE_METADATA.get(lastSel);
            if (selOpt != null && selMeta != null) {
                embed.addField("🎯 Selected Source", String.format("```%s %s %s\nChance: %.1f%% | Status: %s```",
                        selOpt.isEnabled() ? "🟢" : "🔴", selMeta.emoji(), selMeta.displayName(), selOpt.getChance(),
                        selOpt.isEnabled() ? "Enabled" : "Disabled"), false);
            }
        }
        return embed.build();
    }

    private List<ActionRow> createComponents(ChancesSession session) {
        List<ActionRow> rows = new ArrayList<>();

        // 1. Category Menu
        StringSelectMenu.Builder catMenu = StringSelectMenu.create("chances:category")
                .setPlaceholder("📂 Select category...")
                .addOption("🌐 All Sources", "all", "Show all image sources")
                .addOption("🖼️ Images", "images", "Image hosting and galleries")
                .addOption("🎬 Media", "media", "Movies, TV shows, videos")
                .addOption("🔞 NSFW", "nsfw", "Adult content sources")
                .addOption("📚 Text", "text", "Text-based content")
                .setDefaultValues(session.getSelectedCategoryKey());
        rows.add(ActionRow.of(catMenu.build()));

        // 2. Source Menu
        StringSelectMenu.Builder sourceMenu = StringSelectMenu.create("chances:source_select")
                .setPlaceholder("🎯 Select source to configure...");
        
        List<ImageOptions> items = session.getCategoryItems();
        if (items.isEmpty()) {
            sourceMenu.addOption("No sources", "none", "Select a different category").setDisabled(true);
        } else {
            for (ImageOptions opt : items) {
                SourceMetadata meta = SOURCE_METADATA.getOrDefault(opt.getImageType(), new SourceMetadata(opt.getImageType(), "❓", "Unknown"));
                sourceMenu.addOption(
                        String.format("%s %s", opt.isEnabled() ? "🟢" : "🔴", meta.displayName()),
                        opt.getImageType(),
                        String.format("%.1f%% chance | %s", opt.getChance(), opt.isEnabled() ? "Enabled" : "Disabled")
                );
            }
        }
        rows.add(ActionRow.of(sourceMenu.build()));

        // 3. Selection Buttons
        String lastSel = session.getLastSelectedSource();
        if (lastSel != null) {
            ImageOptions selOpt = session.getImageOption(lastSel);
            SourceMetadata selMeta = SOURCE_METADATA.get(lastSel);
            if (selOpt != null && selMeta != null) {
                rows.add(ActionRow.of(
                        Button.secondary("chances:toggle_" + lastSel, (selOpt.isEnabled() ? "🔴 Disable " : "🟢 Enable ") + selMeta.displayName()),
                        Button.primary("chances:edit_" + lastSel, "✏️ Edit " + selMeta.displayName())
                ));
            }
        }

        // 4. Global Actions
        rows.add(ActionRow.of(
                Button.primary("chances:save", "💾 Save Changes").withDisabled(!session.hasUnsavedChanges()),
                Button.success("chances:toggle_all_on", "🟢 Enable All"),
                Button.danger("chances:toggle_all_off", "🔴 Disable All"),
                Button.secondary("chances:reset", "🔄 Reset Defaults")
        ));

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

        public ChancesSession(User user) {
            this.user = user;
            resetFromUser();
        }

        private void resetFromUser() {
            workingOptions.clear();
            user.getImageOptionsMap().forEach((k, v) -> 
                workingOptions.put(k, new ImageOptions(v.getImageType(), v.isEnabled(), v.getChance())));
            hasUnsavedChanges = false;
        }

        public void setSelectedCategory(String key) { this.selectedCategoryKey = key; }
        public String getSelectedCategoryKey() { return selectedCategoryKey; }
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
                        if (selectedCategoryKey.equals("all")) return true;
                        SourceMetadata meta = SOURCE_METADATA.get(opt.getImageType());
                        return meta != null && meta.category().equalsIgnoreCase(getSelectedCategoryName());
                    })
                    .sorted(Comparator.comparing(a -> SOURCE_METADATA.getOrDefault(a.getImageType(), new SourceMetadata(a.getImageType(), "", "")).displayName()))
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
                return String.format("✅ %s %s %s", meta.displayName(), opt.isEnabled() ? "enabled" : "disabled", opt.isEnabled() ? "🟢" : "🔴");
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