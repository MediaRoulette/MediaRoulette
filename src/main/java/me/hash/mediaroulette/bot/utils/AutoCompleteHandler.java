package me.hash.mediaroulette.bot.utils;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.config.LocalConfig;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AutoCompleteHandler extends ListenerAdapter {
    
    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        String commandName = event.getName();
        String subcommandName = event.getSubcommandName();
        String focusedOption = event.getFocusedOption().getName();
        String currentInput = event.getFocusedOption().getValue().toLowerCase();
        
        if (commandName.equals("random") && focusedOption.equals("query")) {
            switch (subcommandName) {
                case "reddit" -> handleSubredditAutocomplete(event, currentInput);
                case "google" -> handleGoogleQueryAutocomplete(event, currentInput);
                case "tenor" -> handleTenorQueryAutocomplete(event, currentInput);
                case "4chan" -> handleFourChanBoardAutocomplete(event, currentInput);
                case "urban" -> handleUrbanAutocomplete(event, currentInput);
                case null -> {}
                default -> throw new IllegalStateException("Unexpected value: " + subcommandName);
            }
        }
        
        // Handle source autocomplete for admin commands
        else if (commandName.equals("admin") && "togglesource".equals(subcommandName) && focusedOption.equals("source")) {
            handleSourceAutocomplete(event, currentInput);
        }
        
        // Handle other subreddit autocomplete fields
        else if (focusedOption.equals("subreddit") || focusedOption.equals("custom-subreddit")) {
            handleSubredditAutocomplete(event, currentInput);
        }
        
        // Handle /chances search autocomplete
        else if (commandName.equals("chances") && focusedOption.equals("search")) {
            handleChancesSearchAutocomplete(event, currentInput);
        }
    }
    
    private void handleSubredditAutocomplete(CommandAutoCompleteInteractionEvent event, String currentInput) {
        User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
        
        // Get user's custom subreddits and filter by input
        List<String> userSubreddits = user.getCustomSubreddits().stream()
                .filter(subreddit -> subreddit.toLowerCase().startsWith(currentInput))
                .limit(25)
                .toList();
        
        // Convert to Command.Choice objects - only show user history
        List<Command.Choice> choices = userSubreddits.stream()
                .map(subreddit -> new Command.Choice("r/" + subreddit, subreddit))
                .collect(Collectors.toList());
        
        event.replyChoices(choices).queue();
    }
    
    private void handleGoogleQueryAutocomplete(CommandAutoCompleteInteractionEvent event, String currentInput) {
        User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
        
        // Get user's Google search history
        List<String> googleQueries = user.getCustomQueries("google").stream()
                .filter(query -> query.toLowerCase().startsWith(currentInput))
                .limit(25)
                .toList();
        
        List<Command.Choice> choices = googleQueries.stream()
                .map(query -> new Command.Choice(query, query))
                .collect(Collectors.toList());
        
        event.replyChoices(choices).queue();
    }
    
    private void handleTenorQueryAutocomplete(CommandAutoCompleteInteractionEvent event, String currentInput) {
        User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
        
        // Get user's tenor search history
        List<String> tenorQueries = user.getCustomQueries("tenor").stream()
                .filter(query -> query.toLowerCase().startsWith(currentInput))
                .limit(25)
                .toList();
        
        List<Command.Choice> choices = tenorQueries.stream()
                .map(query -> new Command.Choice(query, query))
                .collect(Collectors.toList());
        
        event.replyChoices(choices).queue();
    }
    
    private void handleFourChanBoardAutocomplete(CommandAutoCompleteInteractionEvent event, String currentInput) {
        User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
        
        // Get user's 4chan board history
        List<String> boards = user.getCustomQueries("4chan").stream()
                .filter(board -> board.toLowerCase().startsWith(currentInput))
                .limit(25)
                .toList();
        
        List<Command.Choice> choices = boards.stream()
                .map(board -> new Command.Choice("/" + board + "/", board))
                .collect(Collectors.toList());
        
        event.replyChoices(choices).queue();
    }

    private void handleUrbanAutocomplete(CommandAutoCompleteInteractionEvent event, String currentInput) {
        User user = Main.getUserService().getOrCreateUser(event.getUser().getId());

        // Get user's urban dictionary search history
        List<String> urbanQueries = user.getCustomQueries("urban").stream()
                .filter(query -> query.toLowerCase().startsWith(currentInput))
                .limit(25)
                .toList();

        List<Command.Choice> choices = urbanQueries.stream()
                .map(query -> new Command.Choice(query, query))
                .collect(Collectors.toList());

        event.replyChoices(choices).queue();
    }
    
    private void handleSourceAutocomplete(CommandAutoCompleteInteractionEvent event, String currentInput) {
        LocalConfig config = LocalConfig.getInstance();
        Map<String, Boolean> enabledSources = config.getEnabledSources();
        
        List<Command.Choice> choices = enabledSources.keySet().stream()
                .filter(source -> source.toLowerCase().startsWith(currentInput))
                .map(source -> new Command.Choice(source, source))
                .collect(Collectors.toList());
        
        // Always include "all" option if it matches the input
        if ("all".startsWith(currentInput.toLowerCase())) {
            choices.add(new Command.Choice("all", "all"));
        }
        
        if (choices.size() > 25) {
            choices = choices.subList(0, 25);
        }
        
        event.replyChoices(choices).queue();
    }
    
    /**
     * Handle autocomplete for /chances search option.
     * Suggests source names based on user input.
     */
    public void handleChancesSearchAutocomplete(CommandAutoCompleteInteractionEvent event, String currentInput) {
        // Get all available sources from ChancesCommand metadata
        Map<String, String> sourceNames = me.hash.mediaroulette.bot.commands.config.ChancesCommand.getSourceDisplayNames();
        
        List<Command.Choice> choices = sourceNames.entrySet().stream()
                .filter(entry -> entry.getValue().toLowerCase().contains(currentInput) 
                        || entry.getKey().toLowerCase().contains(currentInput))
                .map(entry -> new Command.Choice(entry.getValue(), entry.getKey()))
                .limit(25)
                .collect(Collectors.toList());
        
        event.replyChoices(choices).queue();
    }
    
}
