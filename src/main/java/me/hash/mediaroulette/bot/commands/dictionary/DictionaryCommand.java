package me.hash.mediaroulette.bot.commands.dictionary;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.commands.BaseCommand;
import me.hash.mediaroulette.bot.utils.CommandCooldown;
import me.hash.mediaroulette.model.Dictionary;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.service.DictionaryService;
import me.hash.mediaroulette.locale.LocaleManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
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
            User user = Main.getUserService().getOrCreateUser(userId);
            LocaleManager locale = LocaleManager.getInstance(user.getLocale());
            
            switch (subcommand) {
                case "create" -> handleCreate(event, userId, locale);
                case "list" -> handleList(event, userId, locale);
                case "view" -> handleView(event, userId, locale);
                case "edit" -> handleEdit(event, userId, locale);
                case "delete" -> handleDelete(event, userId, locale);
                case "public" -> handlePublic(event, userId, locale);
            }
        });
    }
    
    private void handleCreate(SlashCommandInteractionEvent event, String userId, LocaleManager locale) {
        String name = event.getOption("name").getAsString();
        String description = event.getOption("description") != null ? 
            event.getOption("description").getAsString() : locale.get("info.no_description");
            
        Dictionary dict = dictionaryService.createDictionary(name, description, userId);
        
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(locale.get("dictionary.created_title"))
            .setDescription(locale.get("dictionary.created_description", name, dict.getId()))
            .setColor(SUCCESS_COLOR)
            .addField(locale.get("dictionary.description_label"), description, false)
            .addField("🔧 " + locale.get("info.next_steps"), locale.get("dictionary.next_steps"), false);
            
        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
    
    private void handleList(SlashCommandInteractionEvent event, String userId, LocaleManager locale) {
        List<Dictionary> dictionaries = dictionaryService.getUserDictionaries(userId);
        
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(locale.get("dictionary.list_title"))
            .setColor(PRIMARY_COLOR);
            
        if (dictionaries.isEmpty()) {
            embed.setDescription(locale.get("dictionary.list_empty"));
        } else {
            StringBuilder sb = new StringBuilder();
            for (Dictionary dict : dictionaries) {
                sb.append(locale.get("dictionary.list_item", 
                    dict.getName(), dict.getId(), dict.getDescription(), 
                    dict.getWordCount(), dict.getUsageCount()));
            }
            embed.setDescription(sb.toString());
        }
        
        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
    
    private void handleView(SlashCommandInteractionEvent event, String userId, LocaleManager locale) {
        String id = event.getOption("id").getAsString();
        Optional<Dictionary> dictOpt = dictionaryService.getDictionary(id);
        
        if (dictOpt.isEmpty() || !dictOpt.get().canBeViewedBy(userId)) {
            sendError(event, locale.get("error.dictionary_not_found"), locale);
            return;
        }
        
        Dictionary dict = dictOpt.get();
        String publicStr = dict.isPublic() ? locale.get("dictionary.yes") : locale.get("dictionary.no");
        
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(locale.get("dictionary.view_title", dict.getName()))
            .setDescription(dict.getDescription())
            .setColor(PRIMARY_COLOR)
            .addField("📊 " + locale.get("info.statistics"), 
                locale.get("dictionary.stats_format", dict.getWordCount(), dict.getUsageCount(), publicStr), true);
                    
        if (dict.getWordCount() > 0) {
            String words = String.join(", ", dict.getWords().subList(0, Math.min(10, dict.getWordCount())));
            if (dict.getWordCount() > 10) words += "...";
            embed.addField(locale.get("dictionary.words_preview"), words, false);
        }
        
        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
    
    private void handleEdit(SlashCommandInteractionEvent event, String userId, LocaleManager locale) {
        String id = event.getOption("id").getAsString();
        Optional<Dictionary> dictOpt = dictionaryService.getDictionary(id);
        
        if (dictOpt.isEmpty() || !dictOpt.get().canBeEditedBy(userId)) {
            sendError(event, locale.get("error.dictionary_not_found"), locale);
            return;
        }
        
        Dictionary dict = dictOpt.get();
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(locale.get("dictionary.edit_title", dict.getName()))
            .setDescription(locale.get("dictionary.edit_description"))
            .setColor(PRIMARY_COLOR);
            
        List<Button> buttons = Arrays.asList(
            Button.primary("dict_edit_words:" + id, locale.get("dictionary.edit_words")),
            Button.secondary("dict_edit_info:" + id, locale.get("dictionary.edit_info")),
            Button.success("dict_toggle_public:" + id, dict.isPublic() ? locale.get("dictionary.make_private") : locale.get("dictionary.make_public"))
        );
        
        event.getHook().sendMessageEmbeds(embed.build())
            .addComponents(ActionRow.of(buttons)).queue();
    }
    
    private void handleDelete(SlashCommandInteractionEvent event, String userId, LocaleManager locale) {
        String id = event.getOption("id").getAsString();
        
        if (dictionaryService.deleteDictionary(id, userId)) {
            sendSuccess(event, locale.get("success.dictionary_deleted"), locale);
        } else {
            sendError(event, locale.get("dictionary.delete_failed"), locale);
        }
    }
    
    private void handlePublic(SlashCommandInteractionEvent event, String userId, LocaleManager locale) {
        List<Dictionary> publicDicts = dictionaryService.getAccessibleDictionaries(userId);
        
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(locale.get("dictionary.public_title"))
            .setColor(PRIMARY_COLOR);
            
        if (publicDicts.isEmpty()) {
            embed.setDescription(locale.get("dictionary.public_empty"));
        } else {
            StringBuilder sb = new StringBuilder();
            for (Dictionary dict : publicDicts.subList(0, Math.min(10, publicDicts.size()))) {
                sb.append(locale.get("dictionary.public_item", 
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
            LocaleManager locale = LocaleManager.getInstance(user.getLocale());

            String originalUserId = event.getMessage().getInteractionMetadata().getUser().getId();
            if (!event.getUser().getId().equals(originalUserId)) {
                event.reply(locale.get("error.not_your_menu")).setEphemeral(true).queue();
                return;
            }

            Optional<Dictionary> dictOpt = dictionaryService.getDictionary(dictId);
            if (dictOpt.isEmpty() || !dictOpt.get().canBeEditedBy(userId)) {
                event.reply(locale.get("dictionary.permission_error"))
                    .setEphemeral(true).queue();
                return;
            }
            
            switch (action) {
                case "dict_edit_words" -> showWordsEditModal(event, dictId, locale);
                case "dict_edit_info" -> showInfoEditModal(event, dictId, dictOpt.get(), locale);
                case "dict_toggle_public" -> handleTogglePublic(event, dictOpt.get(), locale);
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
            
            User user = Main.getUserService().getOrCreateUser(userId);
            LocaleManager locale = LocaleManager.getInstance(user.getLocale());
            
            Optional<Dictionary> dictOpt = dictionaryService.getDictionary(dictId);
            if (dictOpt.isEmpty() || !dictOpt.get().canBeEditedBy(userId)) {
                event.getHook().sendMessage(locale.get("error.dictionary_not_found")).queue();
                return;
            }
            
            Dictionary dict = dictOpt.get();
            
            switch (action) {
                case "dict_words_edit" -> handleWordsEdit(event, dict, locale);
                case "dict_info_edit" -> handleInfoEdit(event, dict, locale);
            }
        });
    }
    
    private void handleWordsEdit(ModalInteractionEvent event, Dictionary dict, LocaleManager locale) {
        String wordsInput = event.getValue("words").getAsString();
        
        try {
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
            
            dictionaryService.updateDictionary(dict);
            event.getHook().sendMessage(locale.get("dictionary.update_success", dict.getName(), dict.getWordCount())).queue();
                
        } catch (Exception e) {
            event.getHook().sendMessage(locale.get("dictionary.update_failed", e.getMessage())).queue();
        }
    }
    
    private void handleInfoEdit(ModalInteractionEvent event, Dictionary dict, LocaleManager locale) {
        String name = event.getValue("name").getAsString().trim();
        String description = event.getValue("description").getAsString().trim();
        
        try {
            dict.setName(name);
            dict.setDescription(description);
            
            dictionaryService.updateDictionary(dict);
            event.getHook().sendMessage(locale.get("dictionary.info_update_success", name)).queue();
            
        } catch (Exception e) {
            event.getHook().sendMessage(locale.get("dictionary.info_update_failed", e.getMessage())).queue();
        }
    }
    
    private void showWordsEditModal(ButtonInteractionEvent event, String dictId, LocaleManager locale) {
        Optional<Dictionary> dictOpt = dictionaryService.getDictionary(dictId);
        if (dictOpt.isEmpty()) return;
        
        Dictionary dict = dictOpt.get();
        String currentWords = String.join(", ", dict.getWords());
        
        TextInput.Builder wordsInputBuilder = TextInput.create("words", TextInputStyle.PARAGRAPH)
            .setPlaceholder(locale.get("dictionary.words_placeholder"))
            .setRequiredRange(0, 2000);
        
        if (!currentWords.trim().isEmpty()) {
            if (currentWords.length() > 2000) {
                currentWords = currentWords.substring(0, 2000);
            }
            wordsInputBuilder.setValue(currentWords);
        }
        
        TextInput wordsInput = wordsInputBuilder.build();
            
        Modal modal = Modal.create("dict_words_edit:" + dictId, locale.get("dictionary.modal_edit_words"))
            .addComponents(Label.of(locale.get("dictionary.words_label"), wordsInput))
            .build();
            
        event.replyModal(modal).queue();
    }
    
    private void showInfoEditModal(ButtonInteractionEvent event, String dictId, Dictionary dict, LocaleManager locale) {
        String name = dict.getName();
        if (name == null || name.trim().isEmpty()) {
            name = "Unnamed Dictionary";
        }
        
        TextInput nameInput = TextInput.create("name", TextInputStyle.SHORT)
            .setPlaceholder(locale.get("dictionary.name_placeholder"))
            .setValue(name)
            .setRequiredRange(1, 100)
            .build();
            
        TextInput.Builder descInputBuilder = TextInput.create("description", TextInputStyle.PARAGRAPH)
            .setPlaceholder(locale.get("dictionary.description_placeholder"))
            .setRequiredRange(0, 500);
            
        String description = dict.getDescription();
        if (description != null && !description.trim().isEmpty()) {
            descInputBuilder.setValue(description);
        }
            
        TextInput descInput = descInputBuilder.build();
            
        Modal modal = Modal.create("dict_info_edit:" + dictId, locale.get("dictionary.modal_edit_info"))
            .addComponents(Label.of(locale.get("dictionary.name_label"), nameInput), Label.of(locale.get("dictionary.description_label"), descInput))
            .build();
            
        event.replyModal(modal).queue();
    }
    
    private void handleTogglePublic(ButtonInteractionEvent event, Dictionary dict, LocaleManager locale) {
        event.deferEdit().queue();
        
        try {
            dict.setPublic(!dict.isPublic());
            dictionaryService.updateDictionary(dict);
            
            String status = dict.isPublic() ? locale.get("dictionary.public") : locale.get("dictionary.private");
            event.getHook().sendMessage(locale.get("dictionary.visibility_success", dict.getName(), status)).queue();
                
        } catch (Exception e) {
            event.getHook().sendMessage(locale.get("dictionary.visibility_failed")).queue();
        }
    }
    
    private void sendSuccess(SlashCommandInteractionEvent event, String message, LocaleManager locale) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("✅ " + locale.get("success.title"))
            .setDescription(message)
            .setColor(SUCCESS_COLOR);
        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
    
    private void sendError(SlashCommandInteractionEvent event, String message, LocaleManager locale) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("❌ " + locale.get("error.title"))
            .setDescription(message)
            .setColor(ERROR_COLOR);
        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
}