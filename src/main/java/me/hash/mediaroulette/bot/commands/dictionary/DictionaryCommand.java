package me.hash.mediaroulette.bot.commands.dictionary;

import com.fasterxml.jackson.databind.ser.Serializers;
import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.bot.commands.BaseCommand;
import me.hash.mediaroulette.bot.commands.CommandHandler;
import me.hash.mediaroulette.bot.utils.CommandCooldown;
import me.hash.mediaroulette.model.Dictionary;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.service.DictionaryService;
import me.hash.mediaroulette.utils.LocaleManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.modals.Modal;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class DictionaryCommand extends BaseCommand {
    private static final Color PRIMARY_COLOR = new Color(88, 101, 242);
    private static final Color SUCCESS_COLOR = new Color(87, 242, 135);
    private static final Color ERROR_COLOR = new Color(220, 53, 69);
    
    private final DictionaryService dictionaryService;
    
    public DictionaryCommand(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }
    
    @Override
    public CommandData getCommandData() {
        return Commands.slash("dictionary", "📚 Manage your dictionaries")
                .addSubcommands(
                    new SubcommandData("create", "Create a new dictionary")
                        .addOption(OptionType.STRING, "name", "Dictionary name", true)
                        .addOption(OptionType.STRING, "description", "Dictionary description", false),
                    new SubcommandData("list", "List your dictionaries"),
                    new SubcommandData("view", "View a dictionary")
                        .addOption(OptionType.STRING, "id", "Dictionary ID", true),
                    new SubcommandData("edit", "Edit a dictionary")
                        .addOption(OptionType.STRING, "id", "Dictionary ID", true),
                    new SubcommandData("delete", "Delete a dictionary")
                        .addOption(OptionType.STRING, "id", "Dictionary ID", true),
                    new SubcommandData("public", "Browse public dictionaries")
                ).setIntegrationTypes(IntegrationType.ALL)
                .setContexts(InteractionContextType.ALL);
    }
    
    @Override
    @CommandCooldown(value = 3, commands = {"dictionary"})
    public void handleCommand(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("dictionary")) return;
        
        event.deferReply().queue();
        Main.getBot().getExecutor().execute(() -> {
            String subcommand = event.getSubcommandName();
            String userId = event.getUser().getId();
            
            switch (subcommand) {
                case "create" -> handleCreate(event, userId);
                case "list" -> handleList(event, userId);
                case "view" -> handleView(event, userId);
                case "edit" -> handleEdit(event, userId);
                case "delete" -> handleDelete(event, userId);
                case "public" -> handlePublic(event, userId);
            }
        });
    }
    
    private void handleCreate(SlashCommandInteractionEvent event, String userId) {
        String name = event.getOption("name").getAsString();
        String description = event.getOption("description") != null ? 
            event.getOption("description").getAsString() : "No description";
            
        Dictionary dict = dictionaryService.createDictionary(name, description, userId);
        
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("✅ Dictionary Created")
            .setDescription(String.format("**%s** has been created!\nID: `%s`", name, dict.getId()))
            .setColor(SUCCESS_COLOR)
            .addField("📝 Description", description, false)
            .addField("🔧 Next Steps", "Use `/dictionary edit` to add words", false);
            
        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
    
    private void handleList(SlashCommandInteractionEvent event, String userId) {
        List<Dictionary> dictionaries = dictionaryService.getUserDictionaries(userId);
        
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("📚 Your Dictionaries")
            .setColor(PRIMARY_COLOR);
            
        if (dictionaries.isEmpty()) {
            embed.setDescription("You haven't created any dictionaries yet.\nUse `/dictionary create` to get started!");
        } else {
            StringBuilder sb = new StringBuilder();
            for (Dictionary dict : dictionaries) {
                sb.append(String.format("**%s** (`%s`)\n%s\n📊 %d words | 🔄 %d uses\n\n", 
                    dict.getName(), dict.getId(), dict.getDescription(), 
                    dict.getWordCount(), dict.getUsageCount()));
            }
            embed.setDescription(sb.toString());
        }
        
        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
    
    private void handleView(SlashCommandInteractionEvent event, String userId) {
        String id = event.getOption("id").getAsString();
        Optional<Dictionary> dictOpt = dictionaryService.getDictionary(id);
        
        if (dictOpt.isEmpty() || !dictOpt.get().canBeViewedBy(userId)) {
            sendError(event, "Dictionary not found or access denied.");
            return;
        }
        
        Dictionary dict = dictOpt.get();
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("📖 " + dict.getName())
            .setDescription(dict.getDescription())
            .setColor(PRIMARY_COLOR)
            .addField("📊 Statistics", 
                String.format("Words: %d\nUsage: %d times\nPublic: %s", 
                    dict.getWordCount(), dict.getUsageCount(), 
                    dict.isPublic() ? "Yes" : "No"), true);
                    
        if (dict.getWordCount() > 0) {
            String words = String.join(", ", dict.getWords().subList(0, Math.min(10, dict.getWordCount())));
            if (dict.getWordCount() > 10) words += "...";
            embed.addField("📝 Words (showing first 10)", words, false);
        }
        
        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
    
    private void handleEdit(SlashCommandInteractionEvent event, String userId) {
        String id = event.getOption("id").getAsString();
        Optional<Dictionary> dictOpt = dictionaryService.getDictionary(id);
        
        if (dictOpt.isEmpty() || !dictOpt.get().canBeEditedBy(userId)) {
            sendError(event, "Dictionary not found or access denied.");
            return;
        }
        
        Dictionary dict = dictOpt.get();
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("✏️ Edit: " + dict.getName())
            .setDescription("Choose what you want to edit:")
            .setColor(PRIMARY_COLOR);
            
        List<Button> buttons = Arrays.asList(
            Button.primary("dict_edit_words:" + id, "📝 Edit Words"),
            Button.secondary("dict_edit_info:" + id, "ℹ️ Edit Info"),
            Button.success("dict_toggle_public:" + id, dict.isPublic() ? "🔒 Make Private" : "🌐 Make Public")
        );
        
        event.getHook().sendMessageEmbeds(embed.build())
            .addComponents(ActionRow.of(buttons)).queue();
    }
    
    private void handleDelete(SlashCommandInteractionEvent event, String userId) {
        String id = event.getOption("id").getAsString();
        
        if (dictionaryService.deleteDictionary(id, userId)) {
            sendSuccess(event, "Dictionary deleted successfully.");
        } else {
            sendError(event, "Failed to delete dictionary. Check ID and permissions.");
        }
    }
    
    private void handlePublic(SlashCommandInteractionEvent event, String userId) {
        List<Dictionary> publicDicts = dictionaryService.getAccessibleDictionaries(userId);
        
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("🌐 Public Dictionaries")
            .setColor(PRIMARY_COLOR);
            
        if (publicDicts.isEmpty()) {
            embed.setDescription("No public dictionaries available.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (Dictionary dict : publicDicts.subList(0, Math.min(10, publicDicts.size()))) {
                sb.append(String.format("**%s** (`%s`)\n%s\n📊 %d words\n\n", 
                    dict.getName(), dict.getId(), dict.getDescription(), dict.getWordCount()));
            }
            embed.setDescription(sb.toString());
        }
        
        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
    
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("dict_")) return;
        
        String[] parts = event.getComponentId().split(":");
        String action = parts[0];
        String dictId = parts.length > 1 ? parts[1] : null;
        String userId = event.getUser().getId();
        
        Main.getBot().getExecutor().execute(() -> {
            User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
            LocaleManager localeManager = LocaleManager.getInstance(user.getLocale());

            String originalUserId = event.getMessage().getInteractionMetadata().getUser().getId();
            if (!event.getUser().getId().equals(originalUserId)) {
                event.reply(localeManager.get("error.not_your_menu")).setEphemeral(true).queue();
                return;
            }

            // Check permissions first
            Optional<Dictionary> dictOpt = dictionaryService.getDictionary(dictId);
            if (dictOpt.isEmpty() || !dictOpt.get().canBeEditedBy(userId)) {
                event.reply("❌ Dictionary not found or you don't have permission to edit it.")
                    .setEphemeral(true).queue();
                return;
            }
            
            switch (action) {
                case "dict_edit_words" -> showWordsEditModal(event, dictId);
                case "dict_edit_info" -> showInfoEditModal(event, dictId, dictOpt.get());
                case "dict_toggle_public" -> handleTogglePublic(event, dictId, dictOpt.get());
            }
        });
    }
    
    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().startsWith("dict_")) return;
        
        event.deferReply().setEphemeral(true).queue();
        Main.getBot().getExecutor().execute(() -> {
            String[] parts = event.getModalId().split(":");
            String action = parts[0];
            String dictId = parts.length > 1 ? parts[1] : null;
            String userId = event.getUser().getId();
            
            // Check permissions
            Optional<Dictionary> dictOpt = dictionaryService.getDictionary(dictId);
            if (dictOpt.isEmpty() || !dictOpt.get().canBeEditedBy(userId)) {
                event.getHook().sendMessage("❌ Dictionary not found or access denied.").queue();
                return;
            }
            
            Dictionary dict = dictOpt.get();
            
            switch (action) {
                case "dict_words_edit" -> handleWordsEdit(event, dict);
                case "dict_info_edit" -> handleInfoEdit(event, dict);
            }
        });
    }
    
    private void handleWordsEdit(ModalInteractionEvent event, Dictionary dict) {
        String wordsInput = event.getValue("words").getAsString();
        
        try {
            // Clear existing words and add new ones
            dict.clearWords();
            
            if (!wordsInput.trim().isEmpty()) {
                String[] words = wordsInput.split(",");
                for (String word : words) {
                    String trimmed = word.trim();
                    if (!trimmed.isEmpty()) {
                        dict.addWord(trimmed);
                    }
                }
            }
            
            // Save the dictionary
            dictionaryService.updateDictionary(dict);
            
            event.getHook().sendMessage(String.format("✅ Dictionary **%s** updated with %d words!", 
                dict.getName(), dict.getWordCount())).queue();
                
        } catch (Exception e) {
            event.getHook().sendMessage("❌ Failed to update dictionary: " + e.getMessage()).queue();
        }
    }
    
    private void handleInfoEdit(ModalInteractionEvent event, Dictionary dict) {
        String name = event.getValue("name").getAsString().trim();
        String description = event.getValue("description").getAsString().trim();
        
        try {
            dict.setName(name);
            dict.setDescription(description);
            
            // Save the dictionary
            dictionaryService.updateDictionary(dict);
            event.getHook().sendMessage(String.format("✅ Dictionary **%s** info updated!", name)).queue();
            
        } catch (Exception e) {
            event.getHook().sendMessage("❌ Failed to update dictionary info: " + e.getMessage()).queue();
        }
    }
    
    private void showWordsEditModal(ButtonInteractionEvent event, String dictId) {
        Optional<Dictionary> dictOpt = dictionaryService.getDictionary(dictId);
        if (dictOpt.isEmpty()) return;
        
        Dictionary dict = dictOpt.get();
        String currentWords = String.join(", ", dict.getWords());
        
        TextInput.Builder wordsInputBuilder = TextInput.create("words", TextInputStyle.PARAGRAPH)
            .setPlaceholder("word1, word2, word3...")
            .setRequiredRange(0, 2000);
        
        // Only set value if we have words, otherwise let it be empty and show placeholder
        if (!currentWords.trim().isEmpty()) {
            // Truncate if too long for modal
            if (currentWords.length() > 2000) {
                currentWords = currentWords.substring(0, 2000);
            }
            wordsInputBuilder.setValue(currentWords);
        }
        
        TextInput wordsInput = wordsInputBuilder.build();
            
        Modal modal = Modal.create("dict_words_edit:" + dictId, "Edit Dictionary Words")
            .addComponents(Label.of("Words (comma-separated)", wordsInput))
            .build();
            
        event.replyModal(modal).queue();
    }
    
    private void showInfoEditModal(ButtonInteractionEvent event, String dictId, Dictionary dict) {
        // Name is required, so provide a fallback
        String name = dict.getName();
        if (name == null || name.trim().isEmpty()) {
            name = "Unnamed Dictionary";
        }
        
        TextInput nameInput = TextInput.create("name", TextInputStyle.SHORT)
            .setPlaceholder("Enter dictionary name...")
            .setValue(name)
            .setRequiredRange(1, 100)
            .build();
            
        TextInput.Builder descInputBuilder = TextInput.create("description", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Enter description...")
            .setRequiredRange(0, 500);
            
        // Only set description value if it's not empty
        String description = dict.getDescription();
        if (description != null && !description.trim().isEmpty()) {
            descInputBuilder.setValue(description);
        }
            
        TextInput descInput = descInputBuilder.build();
            
        Modal modal = Modal.create("dict_info_edit:" + dictId, "Edit Dictionary Info")
            .addComponents(Label.of("Dictionary Name", nameInput), Label.of("Description", descInput))
            .build();
            
        event.replyModal(modal).queue();
    }
    
    private void handleTogglePublic(ButtonInteractionEvent event, String dictId, Dictionary dict) {
        event.deferEdit().queue();
        
        try {
            dict.setPublic(!dict.isPublic());
            // Save dictionary
            dictionaryService.updateDictionary(dict);
            
            String status = dict.isPublic() ? "public" : "private";
            event.getHook().sendMessage(String.format("✅ Dictionary **%s** is now %s!", 
                dict.getName(), status)).queue();
                
        } catch (Exception e) {
            event.getHook().sendMessage("❌ Failed to update dictionary visibility.").queue();
        }
    }
    
    private void sendSuccess(SlashCommandInteractionEvent event, String message) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("✅ Success")
            .setDescription(message)
            .setColor(SUCCESS_COLOR);
        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
    
    private void sendError(SlashCommandInteractionEvent event, String message) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("❌ Error")
            .setDescription(message)
            .setColor(ERROR_COLOR);
        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
}