package me.hash.mediaroulette.bot.commands.config;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.commands.BaseCommand;
import me.hash.mediaroulette.bot.utils.CommandCooldown;
import me.hash.mediaroulette.model.*;
import me.hash.mediaroulette.service.DictionaryService;
import com.mongodb.client.MongoCollection;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import org.bson.Document;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import me.hash.mediaroulette.locale.LocaleManager;

import java.awt.Color;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class SettingsCommand extends BaseCommand {
    private static final Logger logger = LoggerFactory.getLogger(SettingsCommand.class);

    private static final Color PRIMARY_COLOR = new Color(88, 101, 242);
    private static final Color SUCCESS_COLOR = new Color(87, 242, 135);
    private static final Color ERROR_COLOR = new Color(220, 53, 69);
    
    private static final List<String> SUPPORTED_SOURCES = Arrays.asList(
        "tenor", "google", "reddit"
    );
    
    // Supported locales with display info
    private record SupportedLocale(String code, String displayName, String flag) {}
    private static final List<SupportedLocale> SUPPORTED_LOCALES = Arrays.asList(
        new SupportedLocale("en_US", "English (US)", "🇺🇸"),
        new SupportedLocale("es", "Español", "🇪🇸"),
        new SupportedLocale("fr", "Français", "🇫🇷"),
        new SupportedLocale("ar", "العربية", "🇸🇦")
    );
    
    private final DictionaryService dictionaryService;
    
    public SettingsCommand(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }
    
    @Override
    public CommandData getCommandData() {
        return Commands.slash("settings", "⚙️ Configure your MediaRoulette settings")
                .addSubcommands(
                    new SubcommandData("view", "View all your settings in an interactive panel"),
                    new SubcommandData("locale", "Change your language preference")
                        .addOption(OptionType.STRING, "language", "Language code (en_US, es, fr, ar)", false, true),
                    new SubcommandData("assign", "Assign a dictionary to a source")
                        .addOption(OptionType.STRING, "source", "Source name (tenor, reddit, etc.)", true)
                        .addOption(OptionType.STRING, "dictionary", "Dictionary ID", true),
                    new SubcommandData("unassign", "Remove dictionary assignment")
                        .addOption(OptionType.STRING, "source", "Source name", true),
                    new SubcommandData("shareconfig", "Share your configuration")
                        .addOption(OptionType.STRING, "title", "Title for the configuration share", false)
                        .addOption(OptionType.STRING, "description", "Description for the configuration share", false),
                    new SubcommandData("assigndefault", "Remove dictionary assignment and use default")
                        .addOption(OptionType.STRING, "source", "Source name (tenor, reddit, etc.)", true),
                    new SubcommandData("reset", "Reset all settings to default values")
                ).setIntegrationTypes(IntegrationType.ALL)
                .setContexts(InteractionContextType.ALL);
    }
    
    @Override
    @CommandCooldown(value = 3, commands = {"settings"})
    public void handleCommand(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("settings")) return;
        
        event.deferReply().queue();
        Main.getBot().getExecutor().execute(() -> {
            String subcommand = event.getSubcommandName();
            String userId = event.getUser().getId();
            User user = Main.getUserService().getOrCreateUser(userId);
            LocaleManager locale = LocaleManager.getInstance(user.getLocale());
            
            switch (subcommand) {
                case "view" -> handleView(event, user, locale);
                case "locale" -> handleLocale(event, user, locale);
                case "assign" -> handleAssign(event, userId, locale);
                case "unassign" -> handleUnassign(event, userId, locale);
                case "shareconfig" -> handleShareConfig(event, userId, locale);
                case "assigndefault" -> handleAssignDefault(event, userId, locale);
                case "reset" -> handleReset(event, userId, locale);
            }
        });
    }
    
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        
        if (componentId.startsWith("settings:setconfig:")) {
            event.deferReply(true).queue(); // Ephemeral response
            Main.getBot().getExecutor().execute(() -> handleApplyConfig(event, componentId));
        } else if (componentId.equals("settings:share")) {
            event.deferReply().queue();
            Main.getBot().getExecutor().execute(() -> handleShareConfigFromButton(event));
        } else if (componentId.equals("settings:reset")) {
            event.deferReply().queue();
            Main.getBot().getExecutor().execute(() -> handleResetFromButton(event));
        }
    }
    
    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();
        
        if (!componentId.equals("settings:locale_select")) return;
        
        // Validate user owns this interaction
        String eventUserId = event.getUser().getId();
        if (!event.getMessage().getInteractionMetadata().getUser().getId().equals(eventUserId)) {
            event.reply(LocaleManager.getInstance(Main.getUserService().getOrCreateUser(eventUserId).getLocale())
                    .get("error.not_your_menu")).setEphemeral(true).queue();
            return;
        }
        
        event.deferEdit().queue();
        Main.getBot().getExecutor().execute(() -> {
            String selectedLocale = event.getValues().getFirst();
            String userId = event.getUser().getId();
            User user = Main.getUserService().getOrCreateUser(userId);
            
            // Update locale
            user.setLocale(selectedLocale);
            Main.getUserService().updateUser(user);
            
            LocaleManager newLocale = LocaleManager.getInstance(selectedLocale);
            
            // Find display name for the selected locale
            String displayName = SUPPORTED_LOCALES.stream()
                    .filter(l -> l.code().equals(selectedLocale))
                    .findFirst()
                    .map(l -> l.flag() + " " + l.displayName())
                    .orElse(selectedLocale);
            
            // Refresh the view with updated locale
            updateSettingsDisplay(event.getHook(), user, event.getUser(), 
                    newLocale.get("settings.language_updated", displayName));
        });
    }
    
    private void handleAssign(SlashCommandInteractionEvent event, String userId, LocaleManager locale) {
        String source = event.getOption("source").getAsString().toLowerCase();
        String dictionaryId = event.getOption("dictionary").getAsString();
        
        if (!SUPPORTED_SOURCES.contains(source)) {
            sendError(event, locale.get("settings.unsupported_source", String.join(", ", SUPPORTED_SOURCES)));
            return;
        }
        
        Optional<Dictionary> dictOpt = dictionaryService.getDictionary(dictionaryId);
        if (dictOpt.isEmpty() || !dictOpt.get().canBeViewedBy(userId)) {
            sendError(event, locale.get("error.dictionary_not_found"));
            return;
        }
        
        dictionaryService.assignDictionary(userId, source, dictionaryId);
        
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(locale.get("settings.assignment_complete"))
            .setDescription(locale.get("settings.assignment_description", dictOpt.get().getName(), source))
            .setColor(SUCCESS_COLOR);
            
        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
    
    private void handleView(SlashCommandInteractionEvent event, User user, LocaleManager locale) {
        updateSettingsDisplay(event.getHook(), user, event.getUser(), null);
    }
    
    private void handleLocale(SlashCommandInteractionEvent event, User user, LocaleManager locale) {
        var languageOption = event.getOption("language");
        
        if (languageOption == null) {
            // Show locale selection UI (same as view but focused on locale)
            updateSettingsDisplay(event.getHook(), user, event.getUser(), null);
            return;
        }
        
        String newLocaleCode = languageOption.getAsString();
        
        // Validate the locale
        SupportedLocale found = SUPPORTED_LOCALES.stream()
                .filter(l -> l.code().equals(newLocaleCode))
                .findFirst()
                .orElse(null);
        
        if (found == null) {
            String supportedCodes = SUPPORTED_LOCALES.stream()
                    .map(SupportedLocale::code)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            sendError(event, locale.get("settings.unsupported_locale", supportedCodes));
            return;
        }
        
        // Update user locale
        user.setLocale(newLocaleCode);
        Main.getUserService().updateUser(user);
        
        LocaleManager newLocale = LocaleManager.getInstance(newLocaleCode);
        String displayName = found.flag() + " " + found.displayName();
        
        // Send success with the new Container UI
        updateSettingsDisplay(event.getHook(), user, event.getUser(), 
                newLocale.get("settings.language_updated", displayName));
    }
    
    /**
     * Build and display the settings Container UI.
     */
    private void updateSettingsDisplay(net.dv8tion.jda.api.interactions.InteractionHook hook, 
                                       User user, net.dv8tion.jda.api.entities.User discordUser, 
                                       String statusMessage) {
        String userId = user.getUserId();
        LocaleManager locale = LocaleManager.getInstance(user.getLocale());
        
        List<ContainerChildComponent> components = new ArrayList<>();
        
        // Header Section with user avatar
        components.add(Section.of(
                Thumbnail.fromUrl(discordUser.getEffectiveAvatarUrl()),
                TextDisplay.of("## " + locale.get("settings.your_settings")),
                TextDisplay.of("**" + locale.get("settings.personalize") + "**")
        ));
        
        components.add(Separator.createDivider(Separator.Spacing.SMALL));
        
        // === Language Section ===
        SupportedLocale currentLocale = SUPPORTED_LOCALES.stream()
                .filter(l -> l.code().equals(user.getLocale()))
                .findFirst()
                .orElse(SUPPORTED_LOCALES.getFirst());
        
        StringBuilder langContent = new StringBuilder();
        langContent.append("### ").append(locale.get("settings.language_title")).append("\n");
        langContent.append(currentLocale.flag()).append(" **").append(currentLocale.displayName()).append("**");
        components.add(TextDisplay.of(langContent.toString()));
        
        components.add(Separator.createDivider(Separator.Spacing.SMALL));
        
        // === Appearance Section ===
        StringBuilder appearanceContent = new StringBuilder();
        appearanceContent.append("### ").append(locale.get("settings.appearance_title")).append("\n");
        String themeName = user.getTheme() != null ? user.getTheme() : "default";
        appearanceContent.append(locale.get("settings.theme_label")).append(" `").append(formatThemeName(themeName)).append("`");
        components.add(TextDisplay.of(appearanceContent.toString()));
        
        components.add(Separator.createDivider(Separator.Spacing.SMALL));
        
        // === Dictionary Assignments Section ===
        StringBuilder dictContent = new StringBuilder();
        dictContent.append("### ").append(locale.get("settings.dictionaries_title")).append("\n");
        
        for (String source : SUPPORTED_SOURCES) {
            Optional<String> assignedDict = dictionaryService.getAssignedDictionary(userId, source);
            if (assignedDict.isPresent()) {
                Optional<Dictionary> dict = dictionaryService.getDictionary(assignedDict.get());
                if (dict.isPresent()) {
                    dictContent.append("**").append(formatSourceName(source)).append("**: ")
                            .append(dict.get().getName()).append(" (`").append(dict.get().getId()).append("`)\n");
                } else {
                    dictContent.append("**").append(formatSourceName(source)).append("**: ")
                            .append(locale.get("settings.default_dictionary")).append("\n");
                }
            } else {
                dictContent.append("**").append(formatSourceName(source)).append("**: *")
                        .append(locale.get("settings.default_dictionary")).append("*\n");
            }
        }
        components.add(TextDisplay.of(dictContent.toString()));
        
        // === Status Message (if any) ===
        if (statusMessage != null) {
            components.add(Separator.createDivider(Separator.Spacing.SMALL));
            components.add(TextDisplay.of("📢 " + statusMessage));
        }
        
        Container container = Container.of((java.util.Collection<? extends ContainerChildComponent>) components)
                .withAccentColor(PRIMARY_COLOR);
        
        // Build the language select menu
        StringSelectMenu.Builder localeMenu = StringSelectMenu.create("settings:locale_select")
                .setPlaceholder(locale.get("settings.language_select_placeholder"));
        
        for (SupportedLocale loc : SUPPORTED_LOCALES) {
            SelectOption option = SelectOption.of(loc.displayName(), loc.code())
                    .withEmoji(Emoji.fromUnicode(loc.flag()))
                    .withDefault(loc.code().equals(user.getLocale()));
            localeMenu.addOptions(option);
        }
        
        // Action buttons
        ActionRow buttonRow = ActionRow.of(
                Button.secondary("settings:share", locale.get("settings.share_config")),
                Button.danger("settings:reset", locale.get("settings.reset_all"))
        );
        
        // Combine all components
        List<MessageTopLevelComponent> allComponents = new ArrayList<>();
        allComponents.add(container);
        allComponents.add(ActionRow.of(localeMenu.build()));
        allComponents.add(buttonRow);
        
        hook.editOriginalComponents(allComponents).useComponentsV2().queue(null, e -> {
            logger.error("Failed to update settings display: {}", e.getMessage());
        });
    }
    
    private String formatThemeName(String theme) {
        if (theme == null || theme.isEmpty()) return "Default";
        return Character.toUpperCase(theme.charAt(0)) + theme.substring(1).toLowerCase();
    }
    
    /**
     * Handle share config button from container UI.
     */
    private void handleShareConfigFromButton(ButtonInteractionEvent event) {
        String userId = event.getUser().getId();
        User user = Main.getUserService().getOrCreateUser(userId);
        LocaleManager locale = LocaleManager.getInstance(user.getLocale());
        
        try {
            String configId = generateShareableConfig(user, userId, null, null);
            
            if (configId != null) {
                Button applyConfigButton = Button.primary("settings:setconfig:" + configId, locale.get("settings.apply_config_button"));
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(locale.get("settings.config_shared"))
                    .setDescription(locale.get("settings.config_ready"))
                    .setColor(SUCCESS_COLOR)
                    .setTimestamp(Instant.now());
                    
                event.getHook().sendMessageEmbeds(embed.build())
                    .addComponents(ActionRow.of(applyConfigButton))
                    .queue();
            } else {
                sendErrorToHook(event.getHook(), locale.get("settings.config_failed"));
            }
        } catch (Exception e) {
            sendErrorToHook(event.getHook(), locale.get("settings.config_generate_failed", e.getMessage()));
        }
    }
    
    /**
     * Handle reset button from container UI.
     */
    private void handleResetFromButton(ButtonInteractionEvent event) {
        String userId = event.getUser().getId();
        User user = Main.getUserService().getOrCreateUser(userId);
        LocaleManager locale = LocaleManager.getInstance(user.getLocale());
        
        try {
            // Remove all dictionary assignments
            int removedAssignments = 0;
            for (String source : SUPPORTED_SOURCES) {
                if (dictionaryService.unassignDictionary(userId, source)) {
                    removedAssignments++;
                }
            }
            
            // Reset user settings to defaults
            user.setLocale("en_US");
            user.setTheme("default");
            Main.getUserService().updateUser(user);
            
            LocaleManager newLocale = LocaleManager.getInstance("en_US");
            
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle(newLocale.get("settings.reset_complete"))
                .setDescription(newLocale.get("settings.reset_description", removedAssignments))
                .setColor(SUCCESS_COLOR)
                .setTimestamp(Instant.now());
                
            event.getHook().sendMessageEmbeds(embed.build()).queue();
            
        } catch (Exception e) {
            sendErrorToHook(event.getHook(), locale.get("settings.reset_failed", e.getMessage()));
        }
    }
    
    private void sendErrorToHook(net.dv8tion.jda.api.interactions.InteractionHook hook, String message) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("❌ Error")
            .setDescription(message)
            .setColor(ERROR_COLOR);
        hook.sendMessageEmbeds(embed.build()).queue();
    }
    
    /**
     * Generate a shareable config and store it in the database.
     * @return The config ID if successful, null otherwise.
     */
    private String generateShareableConfig(User user, String userId, String customTitle, String customDescription) {
        StringBuilder configBuilder = new StringBuilder();
        
        configBuilder.append("=".repeat(50)).append("\n");
        if (customTitle != null) {
            configBuilder.append("📋 ").append(customTitle.toUpperCase()).append("\n");
        } else {
            configBuilder.append("📋 MEDIAROULETTE CONFIGURATION EXPORT\n");
        }
        configBuilder.append("=".repeat(50)).append("\n");
        configBuilder.append("User ID: ").append(userId).append("\n");
        configBuilder.append("Export Date: ").append(Instant.now().toString()).append("\n");
        if (customDescription != null) {
            configBuilder.append("Description: ").append(customDescription).append("\n");
        }
        configBuilder.append("\n");
        
        // Dictionary Assignments
        configBuilder.append("🎯 DICTIONARY ASSIGNMENTS\n");
        configBuilder.append("-".repeat(30)).append("\n");
        
        boolean hasAssignments = false;
        for (String source : SUPPORTED_SOURCES) {
            Optional<String> assignedDict = dictionaryService.getAssignedDictionary(userId, source);
            if (assignedDict.isPresent()) {
                Optional<Dictionary> dict = dictionaryService.getDictionary(assignedDict.get());
                if (dict.isPresent()) {
                    configBuilder.append(String.format("DICT_ASSIGN:%s|%s\n", source, dict.get().getId()));
                    configBuilder.append(String.format("%-10s: %s (%s)\n", 
                        formatSourceName(source), dict.get().getName(), dict.get().getId()));
                    hasAssignments = true;
                }
            }
        }
        
        if (!hasAssignments) {
            configBuilder.append("No custom dictionary assignments (using defaults)\n");
        }
        
        // User Settings
        configBuilder.append("\n⚙️ USER SETTINGS\n");
        configBuilder.append("-".repeat(30)).append("\n");
        configBuilder.append(String.format("USER_SETTING:nsfw|%s\n", user.isNsfw() ? "true" : "false"));
        configBuilder.append(String.format("USER_SETTING:locale|%s\n", user.getLocale()));
        configBuilder.append(String.format("USER_SETTING:theme|%s\n", user.getTheme()));
        
        configBuilder.append("\n").append("=".repeat(50)).append("\n");
        configBuilder.append("💡 To import this configuration, use /settings import <hastebin-url>\n");
        configBuilder.append("🆘 Need help? Use /support to join our Discord server\n");
        configBuilder.append("=".repeat(50));
        
        // Store in database
        return storeConfigInDatabase(configBuilder.toString(), customTitle, customDescription, userId);
    }
    
    private void handleUnassign(SlashCommandInteractionEvent event, String userId, LocaleManager locale) {
        String source = event.getOption("source").getAsString().toLowerCase();
        
        if (!SUPPORTED_SOURCES.contains(source)) {
            sendError(event, locale.get("settings.unsupported_source", String.join(", ", SUPPORTED_SOURCES)));
            return;
        }
        
        if (dictionaryService.unassignDictionary(userId, source)) {
            sendSuccess(event, locale.get("settings.unassign_success", formatSourceName(source)));
        } else {
            sendError(event, locale.get("settings.no_assignment", formatSourceName(source)));
        }
    }
    
    private void handleShareConfig(SlashCommandInteractionEvent event, String userId, LocaleManager locale) {
        try {
            User user = Main.getUserService().getOrCreateUser(userId);
            
            // Get optional title and description from command options
            String customTitle = event.getOption("title") != null ? event.getOption("title").getAsString() : null;
            String customDescription = event.getOption("description") != null ? event.getOption("description").getAsString() : null;
            
            StringBuilder configBuilder = new StringBuilder();
            
            configBuilder.append("=".repeat(50)).append("\n");
            if (customTitle != null) {
                configBuilder.append("📋 ").append(customTitle.toUpperCase()).append("\n");
            } else {
                configBuilder.append("📋 MEDIAROULETTE CONFIGURATION EXPORT\n");
            }
            configBuilder.append("=".repeat(50)).append("\n");
            configBuilder.append("User ID: ").append(userId).append("\n");
            configBuilder.append("Export Date: ").append(Instant.now().toString()).append("\n");
            if (customDescription != null) {
                configBuilder.append("Description: ").append(customDescription).append("\n");
            }
            configBuilder.append("\n");
            
            // Dictionary Assignments
            configBuilder.append("🎯 DICTIONARY ASSIGNMENTS\n");
            configBuilder.append("-".repeat(30)).append("\n");
            
            boolean hasAssignments = false;
            for (String source : SUPPORTED_SOURCES) {
                Optional<String> assignedDict = dictionaryService.getAssignedDictionary(userId, source);
                if (assignedDict.isPresent()) {
                    Optional<Dictionary> dict = dictionaryService.getDictionary(assignedDict.get());
                    if (dict.isPresent()) {
                        configBuilder.append(String.format("DICT_ASSIGN:%s|%s\n", source, dict.get().getId()));
                        configBuilder.append(String.format("%-10s: %s (%s)\n", 
                            formatSourceName(source), dict.get().getName(), dict.get().getId()));
                        hasAssignments = true;
                    }
                }
            }
            
            if (!hasAssignments) {
                configBuilder.append("No custom dictionary assignments (using defaults)\n");
            }
            
            // User's Dictionaries (Full Content for Independence)
            configBuilder.append("\n📚 DICTIONARY DEFINITIONS\n");
            configBuilder.append("-".repeat(30)).append("\n");
            
            List<Dictionary> userDictionaries = dictionaryService.getUserDictionaries(userId);
            if (userDictionaries.isEmpty()) {
                configBuilder.append("No custom dictionaries created\n");
            } else {
                for (Dictionary dict : userDictionaries) {
                    configBuilder.append(String.format("DICT_DEF:%s|%s|%s|%s\n", 
                        dict.getId(), dict.getName(), dict.getDescription(), dict.isPublic() ? "public" : "private"));
                    
                    // Include ALL words for complete independence
                    if (dict.getWordCount() > 0) {
                        List<String> words = dict.getWords();
                        configBuilder.append("DICT_WORDS:").append(dict.getId()).append("|");
                        configBuilder.append(String.join(",", words)).append("\n");
                    }
                    configBuilder.append("\n");
                }
            }
            
            // Source Chances Configuration
            configBuilder.append("🎲 SOURCE CHANCES CONFIGURATION\n");
            configBuilder.append("-".repeat(30)).append("\n");
            
            Map<String, ImageOptions> imageOptions = user.getImageOptionsMap();
            if (imageOptions.isEmpty()) {
                configBuilder.append("Using default source chances\n");
            } else {
                configBuilder.append(String.format("%-15s %-10s %-10s\n", "Source", "Enabled", "Chance %"));
                configBuilder.append("-".repeat(35)).append("\n");
                
                for (Map.Entry<String, ImageOptions> entry : imageOptions.entrySet()) {
                    ImageOptions option = entry.getValue();
                    String sourceName = entry.getKey();
                    try {
                        sourceName = formatSourceName(entry.getKey());
                    } catch (Exception e) {
                        // Use original key if formatting fails
                    }
                    configBuilder.append(String.format("%-15s %-10s %-10.1f\n", 
                        sourceName,
                        option.isEnabled() ? "✅ Yes" : "❌ No",
                        option.getChance()));
                }
            }
            
            // User Settings (Complete Configuration)
            configBuilder.append("\n⚙️ USER SETTINGS\n");
            configBuilder.append("-".repeat(30)).append("\n");
            configBuilder.append(String.format("USER_SETTING:nsfw|%s\n", user.isNsfw() ? "true" : "false"));
            configBuilder.append(String.format("USER_SETTING:locale|%s\n", user.getLocale()));
            configBuilder.append(String.format("USER_SETTING:theme|%s\n", user.getTheme()));
            
            // Favorites (Full Content)
            configBuilder.append("\n💾 FAVORITES\n");
            configBuilder.append("-".repeat(30)).append("\n");
            if (user.getFavorites().isEmpty()) {
                configBuilder.append("No favorites saved\n");
            } else {
                for (Favorite fav : user.getFavorites()) {
                    configBuilder.append(String.format("FAVORITE:%s|%s|%s\n", 
                        fav.getDescription(), fav.getImage(), fav.getType()));
                }
            }
            
            configBuilder.append("\n").append("=".repeat(50)).append("\n");
            configBuilder.append("💡 To import this configuration, use /settings import <hastebin-url>\n");
            configBuilder.append("🆘 Need help? Use /support to join our Discord server\n");
            configBuilder.append("=".repeat(50));
            
            // Store configuration in database and create shareable button
            String configId = storeConfigInDatabase(configBuilder.toString(), customTitle, customDescription, userId);
            
            if (configId != null) {
                String titleText = customTitle != null ? customTitle : "Configuration Share";
                String descText = customDescription != null ? customDescription : "MediaRoulette configuration";
                
                // Create a button that other users can click to apply this configuration
                Button applyConfigButton = Button.primary("settings:setconfig:" + configId, "📥 Apply This Configuration");
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📤 Configuration Shared Successfully!")
                    .setDescription("Your configuration is ready to share!")
                    .setColor(SUCCESS_COLOR)
                    .addField("📋 Title", titleText, true)
                    .addField("📝 Description", descText, true)
                    .setFooter("Configuration created at", null)
                    .setTimestamp(Instant.now());
                    
                event.getHook().sendMessageEmbeds(embed.build())
                    .addComponents(ActionRow.of(applyConfigButton))
                    .queue();
            } else {
                sendError(event, "Failed to save configuration. Please try again later.");
            }
            
        } catch (Exception e) {
            sendError(event, "Failed to generate configuration: " + e.getMessage());
        }
    }
    
    private void handleApplyConfig(ButtonInteractionEvent event, String componentId) {
        try {
            // Extract config ID from component ID: "settings:setconfig:configId"
            String configId = componentId.substring("settings:setconfig:".length());
            String userId = event.getUser().getId();
            
            // Get the configuration content from database
            Document configDoc = getConfigFromDatabase(configId);
            if (configDoc == null) {
                throw new Exception("Configuration not found or expired");
            }
            
            String configContent = configDoc.getString("content");
            String configTitle = configDoc.getString("title");
            String configDescription = configDoc.getString("description");
            
            // Parse and apply the configuration to the user who clicked the button
            parseAndApplyConfig(userId, configContent);
            
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("✅ Configuration Applied Successfully!")
                .setDescription("The shared configuration has been applied to your account.")
                .setColor(SUCCESS_COLOR)
                .addField("📋 Applied Config", configTitle != null ? configTitle : "Configuration", true)
                .addField("📝 Description", configDescription != null ? configDescription : "MediaRoulette configuration", true)
                .setFooter("Configuration applied at", null)
                .setTimestamp(Instant.now());
                
            event.getHook().sendMessageEmbeds(embed.build()).queue();
            
        } catch (Exception e) {
            logger.error("Failed to apply shared configuration: {}", e.getMessage());

            EmbedBuilder errorEmbed = new EmbedBuilder()
                .setTitle("❌ Configuration Apply Failed")
                .setDescription("Failed to apply the shared configuration. The configuration may be invalid or expired.")
                .setColor(ERROR_COLOR)
                .setTimestamp(Instant.now());
                
            event.getHook().sendMessageEmbeds(errorEmbed.build()).queue();
        }
    }
    
    private String storeConfigInDatabase(String configContent, String title, String description, String creatorUserId) {
        try {
            MongoCollection<Document> configCollection = Main.getDatabase().getCollection("shared_configs");
            
            String configId = UUID.randomUUID().toString();
            
            Document configDoc = new Document()
                .append("_id", configId)
                .append("content", configContent)
                .append("title", title)
                .append("description", description)
                .append("creatorUserId", creatorUserId)
                .append("createdAt", Instant.now())
                .append("expiresAt", Instant.now().plusSeconds(30 * 24 * 60 * 60)); // 30 days
            
            configCollection.insertOne(configDoc);
            return configId;
            
        } catch (Exception e) {
            logger.error("Failed to store configuration in database: {}", e.getMessage());
            return null;
        }
    }
    
    private Document getConfigFromDatabase(String configId) {
        try {
            MongoCollection<Document> configCollection = Main.getDatabase().getCollection("shared_configs");
            
            Document query = new Document("_id", configId)
                .append("expiresAt", new Document("$gt", Instant.now()));
            
            return configCollection.find(query).first();
            
        } catch (Exception e) {
            logger.error("Failed to retrieve configuration from database: {}", e.getMessage());
            return null;
        }
    }
    
    private void parseAndApplyConfig(String userId, String configContent) {
        try {
            User user = Main.getUserService().getOrCreateUser(userId);
            String[] lines = configContent.split("\n");
            
            // Maps to store created dictionaries for this import
            Map<String, String> oldToNewDictIds = new HashMap<>();
            
            for (String line : lines) {
                line = line.trim();
                
                // Parse dictionary definitions and create independent copies
                if (line.startsWith("DICT_DEF:")) {
                    String[] parts = line.substring("DICT_DEF:".length()).split("\\|");
                    if (parts.length >= 4) {
                        String oldDictId = parts[0];
                        String dictName = parts[1];
                        String dictDescription = parts[2];
                        boolean isPublic = "public".equals(parts[3]);
                        
                        // Create new independent dictionary
                        Dictionary newDict = dictionaryService.createDictionary(dictName, dictDescription, userId);
                        newDict.setPublic(isPublic);
                        
                        oldToNewDictIds.put(oldDictId, newDict.getId());
                    }
                }
                
                // Parse dictionary words and add to created dictionaries
                else if (line.startsWith("DICT_WORDS:")) {
                    String[] parts = line.substring("DICT_WORDS:".length()).split("\\|", 2);
                    if (parts.length == 2) {
                        String oldDictId = parts[0];
                        String wordsStr = parts[1];
                        
                        String newDictId = oldToNewDictIds.get(oldDictId);
                        if (newDictId != null) {
                            Optional<Dictionary> dictOpt = dictionaryService.getDictionary(newDictId);
                            if (dictOpt.isPresent()) {
                                List<String> words = Arrays.asList(wordsStr.split(","));
                                dictOpt.get().addWords(words);
                            }
                        }
                    }
                }
                
                // Parse dictionary assignments from DICT_ASSIGN lines
                else if (line.startsWith("DICT_ASSIGN:")) {
                    String[] parts = line.substring("DICT_ASSIGN:".length()).split("\\|");
                    if (parts.length == 2) {
                        String sourceKey = parts[0];
                        String oldDictionaryId = parts[1];
                        String newDictionaryId = oldToNewDictIds.get(oldDictionaryId);
                        
                        if (newDictionaryId != null && SUPPORTED_SOURCES.contains(sourceKey)) {
                            dictionaryService.assignDictionary(userId, sourceKey, newDictionaryId);
                        }
                    }
                }
                
                // Parse dictionary assignments (using new dictionary IDs) - legacy format
                else if (line.contains(":") && !line.startsWith("-") && !line.startsWith("=") && 
                         (line.contains("Tenor GIFs") || line.contains("Reddit") || line.contains("Google Images"))) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        String sourceName = parts[0].trim().toLowerCase();
                        String dictInfo = parts[1].trim();
                        
                        // Convert display names back to source keys
                        String sourceKey = switch (sourceName) {
                            case "tenor gifs" -> "tenor";
                            case "reddit" -> "reddit";
                            case "google images" -> "google";
                            default -> sourceName;
                        };
                        
                        // Extract old dictionary ID from parentheses
                        if (dictInfo.contains("(") && dictInfo.contains(")")) {
                            int start = dictInfo.lastIndexOf("(") + 1;
                            int end = dictInfo.lastIndexOf(")");
                            if (start < end) {
                                String oldDictionaryId = dictInfo.substring(start, end);
                                String newDictionaryId = oldToNewDictIds.get(oldDictionaryId);
                                
                                if (newDictionaryId != null) {
                                    dictionaryService.assignDictionary(userId, sourceKey, newDictionaryId);
                                }
                            }
                        }
                    }
                }
                
                // Parse source chances configuration
                else if (line.contains("✅") || line.contains("❌")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 3) {
                        String sourceName = parts[0].toLowerCase();
                        boolean enabled = line.contains("✅");
                        
                        try {
                            // Extract chance percentage (last part should be the number)
                            String chanceStr = parts[parts.length - 1];
                            double chance = Double.parseDouble(chanceStr);
                            
                            // Convert display names back to source keys
                            String sourceKey = switch (sourceName) {
                                case "tenor" -> "tenor";
                                case "reddit" -> "reddit";
                                case "google" -> "google";
                                default -> sourceName;
                            };
                            
                            // Apply the image options
                            ImageOptions imageOptions = user.getImageOptions(sourceKey);
                            if (imageOptions == null) {
                                // Create new ImageOptions if it doesn't exist
                                imageOptions = new ImageOptions(sourceKey, enabled, chance);
                            } else {
                                // Update existing ImageOptions
                                imageOptions.setEnabled(enabled);
                                imageOptions.setChance(chance);
                            }
                            user.setChances(imageOptions);
                            
                        } catch (NumberFormatException e) {
                            // Skip invalid chance values
                        }
                    }
                }
                
                // Parse user settings
                else if (line.startsWith("USER_SETTING:")) {
                    String[] parts = line.substring("USER_SETTING:".length()).split("\\|");
                    if (parts.length == 2) {
                        String setting = parts[0];
                        String value = parts[1];
                        
                        switch (setting) {
                            case "nsfw" -> user.setNsfw("true".equals(value));
                            case "locale" -> user.setLocale(value);
                            case "theme" -> user.setTheme(value);
                        }
                    }
                }
                
                // Parse favorites
                else if (line.startsWith("FAVORITE:")) {
                    String[] parts = line.substring("FAVORITE:".length()).split("\\|");
                    if (parts.length == 3) {
                        String description = parts[0];
                        String image = parts[1];
                        String type = parts[2];
                        
                        user.addFavorite(description, image, type);
                    }
                }
            }
            
            // Save the updated user configuration
            Main.getUserService().updateUser(user);
            
        } catch (Exception e) {
            logger.error("Failed to parse and apply configuration: {}", e.getMessage());
        }
    }
    
    private String formatSourceName(String source) {
        return switch (source) {
            case "tenor" -> "Tenor GIFs";
            case "reddit" -> "Reddit";
            case "google" -> "Google Images";
            default -> source.substring(0, 1).toUpperCase() + source.substring(1);
        };
    }
    
    private void sendSuccess(SlashCommandInteractionEvent event, String message) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("✅ Success")
            .setDescription(message)
            .setColor(SUCCESS_COLOR);
        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
    
    private void handleAssignDefault(SlashCommandInteractionEvent event, String userId, LocaleManager locale) {
        String source = event.getOption("source").getAsString().toLowerCase();
        
        if (!SUPPORTED_SOURCES.contains(source)) {
            sendError(event, "Unsupported source. Supported: " + String.join(", ", SUPPORTED_SOURCES));
            return;
        }
        
        // Remove the assignment (same as unassign)
        if (dictionaryService.unassignDictionary(userId, source)) {
            sendSuccess(event, String.format("Dictionary assignment removed from **%s**. Now using default dictionary.", 
                formatSourceName(source)));
        } else {
            sendError(event, String.format("No dictionary assigned to **%s**. Already using default.", formatSourceName(source)));
        }
    }
    
    private void handleReset(SlashCommandInteractionEvent event, String userId, LocaleManager locale) {
        try {
            User user = Main.getUserService().getOrCreateUser(userId);
            
            // Remove all dictionary assignments
            int removedAssignments = 0;
            for (String source : SUPPORTED_SOURCES) {
                if (dictionaryService.unassignDictionary(userId, source)) {
                    removedAssignments++;
                }
            }
            
            // Reset all image source chances to default values
            // Load default values from randomWeightValues.json
            try {
                Path externalConfig = Path.of("resources", "config", "randomWeightValues.json");
                InputStream inputStream = null;
                
                // Try external resources folder first
                if (Files.exists(externalConfig)) {
                    inputStream = Files.newInputStream(externalConfig);
                } else {
                    // Fallback to classpath
                    inputStream = getClass().getClassLoader().getResourceAsStream("config/randomWeightValues.json");
                }
                
                if (inputStream != null) {
                    String jsonContent = new String(inputStream.readAllBytes());
                    inputStream.close();
                    JSONArray defaultValues = new JSONArray(jsonContent);
                    
                    // Clear existing image options
                    user.getImageOptionsMap().clear();
                    
                    // Set default values
                    for (int i = 0; i < defaultValues.length(); i++) {
                        JSONObject item = defaultValues.getJSONObject(i);
                        String imageType = item.getString("imageType");
                        boolean enabled = item.getBoolean("enabled");
                        double chance = item.getDouble("chance");
                        
                        ImageOptions imageOptions = new ImageOptions(imageType, enabled, chance);
                        user.setChances(imageOptions);
                    }
                    
                    // Save the updated user
                    Main.getUserService().updateUser(user);
                    
                    EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("🔄 Settings Reset Complete")
                        .setDescription(String.format("Successfully reset your configuration:\n\n" +
                            "✅ Removed %d dictionary assignments\n" +
                            "✅ Reset all source chances to default values\n" +
                            "✅ All sources now use default dictionaries\n\n" +
                            "Your account is now using the default MediaRoulette configuration.", 
                            removedAssignments))
                        .setColor(SUCCESS_COLOR)
                        .setFooter("Reset completed at", null)
                        .setTimestamp(Instant.now());
                        
                    event.getHook().sendMessageEmbeds(embed.build()).queue();
                } else {
                    throw new Exception("Could not load default configuration values");
                }
            } catch (Exception e) {
                throw new Exception("Failed to reset image source chances: " + e.getMessage());
            }
            
        } catch (Exception e) {
            sendError(event, "Failed to reset settings: " + e.getMessage());
        }
    }
    
    private void sendError(SlashCommandInteractionEvent event, String message) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("❌ Error")
            .setDescription(message)
            .setColor(ERROR_COLOR);
        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
}