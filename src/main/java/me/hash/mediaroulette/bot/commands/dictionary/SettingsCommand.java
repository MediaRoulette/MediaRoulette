package me.hash.mediaroulette.bot.commands.dictionary;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.bot.commands.BaseCommand;
import me.hash.mediaroulette.bot.commands.CommandHandler;
import me.hash.mediaroulette.bot.utils.CommandCooldown;
import me.hash.mediaroulette.model.*;
import me.hash.mediaroulette.service.DictionaryService;
import com.mongodb.client.MongoCollection;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import org.bson.Document;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
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
    
    private final DictionaryService dictionaryService;
    
    public SettingsCommand(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }
    
    @Override
    public CommandData getCommandData() {
        return Commands.slash("settings", "⚙️ Configure dictionary assignments for sources")
                .addSubcommands(
                    new SubcommandData("assign", "Assign a dictionary to a source")
                        .addOption(OptionType.STRING, "source", "Source name (tenor, reddit, etc.)", true)
                        .addOption(OptionType.STRING, "dictionary", "Dictionary ID", true),
                    new SubcommandData("view", "View current assignments"),
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
        Bot.executor.execute(() -> {
            String subcommand = event.getSubcommandName();
            String userId = event.getUser().getId();
            
            switch (subcommand) {
                case "assign" -> handleAssign(event, userId);
                case "view" -> handleView(event, userId);
                case "unassign" -> handleUnassign(event, userId);
                case "shareconfig" -> handleShareConfig(event, userId);
                case "assigndefault" -> handleAssignDefault(event, userId);
                case "reset" -> handleReset(event, userId);
            }
        });
    }
    
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        
        if (componentId.startsWith("settings:setconfig:")) {
            event.deferReply(true).queue(); // Ephemeral response
            Bot.executor.execute(() -> handleApplyConfig(event, componentId));
        }
    }
    
    private void handleAssign(SlashCommandInteractionEvent event, String userId) {
        String source = event.getOption("source").getAsString().toLowerCase();
        String dictionaryId = event.getOption("dictionary").getAsString();
        
        if (!SUPPORTED_SOURCES.contains(source)) {
            sendError(event, "Unsupported source. Supported: " + String.join(", ", SUPPORTED_SOURCES));
            return;
        }
        
        Optional<Dictionary> dictOpt = dictionaryService.getDictionary(dictionaryId);
        if (dictOpt.isEmpty() || !dictOpt.get().canBeViewedBy(userId)) {
            sendError(event, "Dictionary not found or access denied.");
            return;
        }
        
        dictionaryService.assignDictionary(userId, source, dictionaryId);
        
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("✅ Assignment Complete")
            .setDescription(String.format("Dictionary **%s** assigned to **%s**", 
                dictOpt.get().getName(), source))
            .setColor(SUCCESS_COLOR);
            
        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
    
    private void handleView(SlashCommandInteractionEvent event, String userId) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("⚙️ Dictionary Assignments")
            .setColor(PRIMARY_COLOR);
            
        StringBuilder sb = new StringBuilder();
        boolean hasAssignments = false;
        
        for (String source : SUPPORTED_SOURCES) {
            Optional<String> assignedDict = dictionaryService.getAssignedDictionary(userId, source);
            if (assignedDict.isPresent()) {
                Optional<Dictionary> dict = dictionaryService.getDictionary(assignedDict.get());
                if (dict.isPresent()) {
                    sb.append(String.format("**%s**: %s (`%s`)\n", 
                        formatSourceName(source), dict.get().getName(), dict.get().getId()));
                    hasAssignments = true;
                }
            } else {
                sb.append(String.format("**%s**: *Default dictionary*\n", formatSourceName(source)));
            }
        }
        
        if (!hasAssignments) {
            embed.setDescription("No custom dictionary assignments. All sources use default dictionaries.");
        } else {
            embed.setDescription(sb.toString());
        }
        
        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
    
    private void handleUnassign(SlashCommandInteractionEvent event, String userId) {
        String source = event.getOption("source").getAsString().toLowerCase();
        
        if (!SUPPORTED_SOURCES.contains(source)) {
            sendError(event, "Unsupported source.");
            return;
        }
        
        // Remove the assignment
        if (dictionaryService.unassignDictionary(userId, source)) {
            sendSuccess(event, String.format("Dictionary assignment removed from **%s**. Now using default.", 
                formatSourceName(source)));
        } else {
            sendError(event, String.format("No dictionary assigned to **%s**.", formatSourceName(source)));
        }
    }
    
    private void handleShareConfig(SlashCommandInteractionEvent event, String userId) {
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
    
    private void handleAssignDefault(SlashCommandInteractionEvent event, String userId) {
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
    
    private void handleReset(SlashCommandInteractionEvent event, String userId) {
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
                InputStream inputStream = getClass().getClassLoader().getResourceAsStream("config/randomWeightValues.json");
                if (inputStream != null) {
                    String jsonContent = new String(inputStream.readAllBytes());
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