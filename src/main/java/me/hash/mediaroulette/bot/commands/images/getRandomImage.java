package me.hash.mediaroulette.bot.commands.images;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.bot.Emoji;
import me.hash.mediaroulette.bot.MediaContainerManager;
import me.hash.mediaroulette.bot.errorHandler;
import me.hash.mediaroulette.bot.commands.CommandHandler;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.utils.Locale;
import me.hash.mediaroulette.utils.MaintenanceChecker;
import me.hash.mediaroulette.utils.QuestGenerator;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.replacer.ComponentReplacer;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import java.awt.Color;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class getRandomImage extends ListenerAdapter implements CommandHandler {

    @Override
    public CommandData getCommandData() {
        return Commands.slash("random", "Sends a random image")
                .addSubcommands(
                        new SubcommandData("all", "Sends images from all sources")
                                .addOption(OptionType.BOOLEAN, "shouldcontinue",
                                        "Should the image keep generating?"),
                        new SubcommandData("picsum", "Sends a random image from picsum")
                                .addOption(OptionType.BOOLEAN, "shouldcontinue",
                                        "Should the image keep generating?"),
                        new SubcommandData("imgur", "Sends a random image from imgur")
                                .addOption(OptionType.BOOLEAN, "shouldcontinue",
                                        "Should the image keep generating?"),
                        new SubcommandData("rulee34xxx", "Sends a random image from rulee34 website")
                                .addOption(OptionType.BOOLEAN, "shouldcontinue",
                                        "Should the image keep generating?"),
                        new SubcommandData("google", "Sends a random image from google")
                                .addOption(OptionType.STRING, "query", "What image should be searched for?", false, true)
                                .addOption(OptionType.BOOLEAN, "shouldcontinue",
                                        "Should the image keep generating?"),
                        new SubcommandData("reddit", "Sends a random image from reddit")
                                .addOption(OptionType.STRING, "query", "Which subreddit should the image be retrieved from?", false, true)
                                .addOption(OptionType.BOOLEAN, "shouldcontinue",
                                        "Should the image keep generating?"),
                        new SubcommandData("tenor", "Sends a random gif from tenor")
                                .addOption(OptionType.STRING, "query", "What gif should be searched for?", false, true)
                                .addOption(OptionType.BOOLEAN, "shouldcontinue",
                                        "Should the gif keep generating?"),
                        new SubcommandData("4chan", "Sends a random image from 4chan")
                                .addOption(OptionType.STRING, "query", "Which board to retrieve image from?", false, true)
                                .addOption(OptionType.BOOLEAN, "shouldcontinue",
                                        "Should the image keep generating?"),
                        new SubcommandData("movie", "Sends a random movie from TMDB")
                                .addOption(OptionType.BOOLEAN, "shouldcontinue",
                                        "Should the image keep generating?"),
                        new SubcommandData("tvshow", "Sends a random TV Show from TMDB")
                                .addOption(OptionType.BOOLEAN, "shouldcontinue",
                                        "Should the image keep generating?"),
                        new SubcommandData("youtube", "Sends a random YouTube video")
                                .addOption(OptionType.BOOLEAN, "shouldcontinue",
                                        "Should the image keep generating?"),
                        new SubcommandData("short", "Sends a random Short from TMDB")
                                .addOption(OptionType.BOOLEAN, "shouldcontinue",
                                        "Should the image keep generating?"),
                        new SubcommandData("urban", "Sends a random word from The Urban Dictionary")
                                .addOption(OptionType.STRING, "query", "What word should be defined?")
                                .addOption(OptionType.BOOLEAN, "shouldcontinue",
                                        "Should the word keep generating?")
                ).setIntegrationTypes(IntegrationType.ALL)
                .setContexts(InteractionContextType.ALL);
    }

    private static final Map<Long, MessageData> ACTIVE_MESSAGES = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService CLEANUP_EXECUTOR = Executors.newSingleThreadScheduledExecutor();
    
    /**
     * Shutdown the cleanup executor service
     */
    public static void shutdownCleanupExecutor() {
        CLEANUP_EXECUTOR.shutdown();
        try {
            if (!CLEANUP_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                CLEANUP_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            CLEANUP_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    private static final long INACTIVITY_TIMEOUT = TimeUnit.MINUTES.toMillis(3);

    static {
        CLEANUP_EXECUTOR.scheduleAtFixedRate(() -> {
            long now = Instant.now().toEpochMilli();
            ACTIVE_MESSAGES.entrySet().removeIf(entry -> {
                MessageData data = entry.getValue();
                if (now - data.getLastInteractionTime() > INACTIVITY_TIMEOUT) {
                    data.disableButtons();
                    return true;
                }
                return false;
            });
        }, 1, 1, TimeUnit.MINUTES);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("random")) return;
        
        if (MaintenanceChecker.isMaintenanceBlocked(event)) {
            MaintenanceChecker.sendMaintenanceMessage(event);
            return;
        }

        event.deferReply().queue();
        
        String userId = event.getUser().getId();
        String subcommand = event.getSubcommandName();
        String query = event.getOption("query") != null ? event.getOption("query").getAsString() : null;
        
        Main.userService.trackCommandUsage(userId, "random");
        trackSourceUsage(userId, subcommand, query);

        User user = Main.userService.getOrCreateUser(userId);
        
        if (!validateChannelAccess(event, user)) return;
        
        Main.userService.updateUser(user);

        event.getHook().sendMessageComponents(createLoadingContainer(event.getUser().getEffectiveAvatarUrl())).useComponentsV2().queue(hook ->
            Bot.executor.execute(() -> processImageRequest(event, user, subcommand, query, event.getHook())));
    }

    private void processImageRequest(SlashCommandInteractionEvent event, User user, String subcommand, String query, net.dv8tion.jda.api.interactions.InteractionHook hook) {
        try {
            boolean shouldContinue = event.getOption("shouldcontinue") != null && event.getOption("shouldcontinue").getAsBoolean();
            
            ImageSource.fromName(subcommand.toUpperCase()).ifPresentOrElse(source -> {
                try {
                    Map<String, String> image = source.handle(event, query);
                    if (image == null || image.get("image") == null) {
                        errorHandler.sendErrorEmbed(event, new Locale(user.getLocale()).get("error.no_images_title"), new Locale(user.getLocale()).get("error.no_images_description"));
                        return;
                    }

                    trackStats(user.getUserId(), subcommand, user);
                    
                    MediaContainerManager.editLoadingToImageContainer(hook, image, shouldContinue)
                            .thenAccept(msg -> {
                                ACTIVE_MESSAGES.put(msg.getIdLong(), new MessageData(msg.getIdLong(), subcommand, query, shouldContinue, event.getUser().getIdLong(), event.getChannel().getIdLong()));
                                QuestGenerator.onImageGenerated(user, subcommand);
                                Main.userService.updateUser(user);
                            })
                            .exceptionally(ex -> {
                                errorHandler.handleException(event, new Locale(user.getLocale()).get("error.unexpected_error"), new Locale(user.getLocale()).get("error.failed_to_send_image"), ex);
                                return null;
                            });
                } catch (Exception e) {
                    errorHandler.handleException(event, new Locale(user.getLocale()).get("error.source_error_title"), new Locale(user.getLocale()).get("error.source_error_description"), e);
                }
            }, () -> errorHandler.sendErrorEmbed(event, new Locale(user.getLocale()).get("error.unknown_subcommand_title"), new Locale(user.getLocale()).get("error.unknown_subcommand_description")));
        } catch (Exception e) {
            errorHandler.handleException(event, new Locale(user.getLocale()).get("error.unexpected_error"), e.getMessage(), e);
        }
        user.incrementImagesGenerated();
        Main.userService.updateUser(user);
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        long messageId = event.getMessageIdLong();

        if (!ACTIVE_MESSAGES.containsKey(messageId)) {
            return;
        }
        
        // Check maintenance mode for button interactions too
        if (MaintenanceChecker.isMaintenanceBlocked(event)) {
            MaintenanceChecker.sendMaintenanceMessage(event);
            return;
        }

        event.deferEdit().queue();
        Bot.executor.execute(() -> {
            MessageData data = ACTIVE_MESSAGES.get(messageId);

            User user = Main.userService.getOrCreateUser(event.getUser().getId());


            data.updateLastInteractionTime();

            if (!data.isUserAllowed(event.getUser().getIdLong())) {
                // Show error container using hook since interaction is already acknowledged
                showErrorContainer(event, new Locale(user.getLocale()).get("error.unknown_button_title"), 
                                 new Locale(user.getLocale()).get("error.unknown_button_description"));
                return;
            }

            String buttonId = event.getButton().getCustomId();

            switch (buttonId) {
                case "nsfw:continue":
                case "safe:continue":
                    handleContinue(event, data);
                    break;
                case "favorite":
                    handleFavorite(event);
                    break;
                case "nsfw":
                case "safe":
                    // Update quest progress for rating images
                    QuestGenerator.onImageRated(user);
                    Main.userService.updateUser(user);
                    disableAllButtonsInContainer(event);
                    break;
                case "exit":
                    disableAllButtonsInContainer(event);
                    break;
                case null:
                default:
                    // Show error container using hook since interaction is already acknowledged
                    showErrorContainer(event, new Locale(user.getLocale()).get("error.unknown_button_title"), 
                                     new Locale(user.getLocale()).get("error.unknown_button_description"));
            }
        });
    }

    private void handleContinue(ButtonInteractionEvent event, MessageData data) {
        User user = Main.userService.getOrCreateUser(event.getUser().getId());
        
        event.getHook().editOriginalComponents(createLoadingContainer(event.getUser().getEffectiveAvatarUrl()))
                .useComponentsV2()
                .queue(success -> {
                    ImageSource.fromName(data.getSubcommand().toUpperCase()).ifPresentOrElse(source -> {
                        try {
                            Map<String, String> image = source.handle(event, data.getQuery());
                            if (image == null || image.get("image") == null) {
                                showErrorContainer(event, new Locale(user.getLocale()).get("error.no_more_images_title"), 
                                                 new Locale(user.getLocale()).get("error.no_more_images_description"));
                                return;
                            }
                            MediaContainerManager.editLoadingToImageContainerFromHook(event.getHook(), image, true);
                        } catch (Exception e) {
                            showErrorContainer(event, new Locale(user.getLocale()).get("error.title"), e.getMessage());
                        }
                    }, () -> showErrorContainer(event, new Locale(user.getLocale()).get("error.title"), 
                                         new Locale(user.getLocale()).get("error.invalid_subcommand_description")));
                    user.incrementImagesGenerated();
                    QuestGenerator.onImageGenerated(user, data.getSubcommand());
                    Main.userService.updateUser(user);
                });
    }

    private void handleFavorite(ButtonInteractionEvent event) {
        User user = Main.userService.getOrCreateUser(event.getUser().getId());
        try {
            MessageData data = ACTIVE_MESSAGES.get(event.getMessageIdLong());
            if (data == null) {
                showErrorContainer(event, new Locale(user.getLocale()).get("error.no_image_title"), 
                                 new Locale(user.getLocale()).get("error.no_image_description"));
                return;
            }

            String description = "Random " + data.getSubcommand() + " image";
            String imageUrl = extractImageUrlFromContainer(event.getMessage());

            user.addFavorite(description, imageUrl, "image");
            QuestGenerator.onImageFavorited(user);
            Main.userService.updateUser(user);
            disableButtonInContainer(event, "favorite");
        } catch (Exception e) {
            showErrorContainer(event, new Locale(user.getLocale()).get("error.title"), e.getMessage());
        }
    }

    private static class MessageData {
        private final long messageId;
        private final String subcommand, query;
        private final boolean shouldContinue;
        private final long userId;
        private final long channelId;
        private long lastInteractionTime;

        public MessageData(long messageId, String subcommand, String query, boolean shouldContinue, long userId, long channelId) {
            this.messageId = messageId;
            this.subcommand = subcommand;
            this.query = query;
            this.shouldContinue = shouldContinue;
            this.userId = userId;
            this.channelId = channelId;
            this.lastInteractionTime = Instant.now().toEpochMilli();
        }

        public String getQuery() {
            return query;
        }

        public String getSubcommand() {
            return subcommand;
        }

        public boolean isShouldContinue() {
            return shouldContinue;
        }

        public boolean isUserAllowed(long userId) {
            return this.userId == userId;
        }

        public long getLastInteractionTime() {
            return lastInteractionTime;
        }

        public void updateLastInteractionTime() {
            this.lastInteractionTime = Instant.now().toEpochMilli();
        }

        public void disableButtons() {
            Bot.getShardManager().getTextChannelById(channelId).retrieveMessageById(messageId).queue(message -> {
                if (message.getActionRows().isEmpty()) return;
                MessageComponentTree components = message.getComponentTree();
                ComponentReplacer replacer = ComponentReplacer.of(Button.class, button -> true, Button::asDisabled);
                MessageComponentTree updated = components.replace(replacer);
                message.editMessageComponents(updated).queue();
            });
        }
    }

    private static void disableButtonInContainer(ButtonInteractionEvent event, String buttonId) {
        updateButtonInContainer(event, buttonId);
    }

    private static void disableAllButtonsInContainer(ButtonInteractionEvent event) {
        updateAllButtonsInContainer(event);
    }

    private static void updateButtonInContainer(ButtonInteractionEvent event, String buttonId) {
        try {
            MessageComponentTree components = event.getMessage().getComponentTree();
            ComponentReplacer replacer = ComponentReplacer.of(Button.class, button -> buttonId.equals(button.getId()), Button::asDisabled);
            MessageComponentTree updated = components.replace(replacer);
            event.getHook().editOriginalComponents(updated).useComponentsV2().queue();
        } catch (Exception e) {
            System.err.println("Error updating button in container: " + e.getMessage());
        }
    }

    private static void updateAllButtonsInContainer(ButtonInteractionEvent event) {
        try {
            MessageComponentTree components = event.getMessage().getComponentTree();
            ComponentReplacer replacer = ComponentReplacer.of(Button.class, button -> true, Button::asDisabled);
            MessageComponentTree updated = components.replace(replacer);
            event.getHook().editOriginalComponents(updated).useComponentsV2().queue();
        } catch (Exception e) {
            System.err.println("Error updating all buttons in container: " + e.getMessage());
        }
    }

    private static void showErrorContainer(ButtonInteractionEvent event, String title, String description) {
        try {
            net.dv8tion.jda.api.components.container.Container errorContainer = net.dv8tion.jda.api.components.container.Container.of(
                    net.dv8tion.jda.api.components.section.Section.of(
                            net.dv8tion.jda.api.components.thumbnail.Thumbnail.fromUrl(event.getUser().getEffectiveAvatarUrl()),
                            net.dv8tion.jda.api.components.textdisplay.TextDisplay.of("## ❌ " + title),
                            net.dv8tion.jda.api.components.textdisplay.TextDisplay.of("**" + description + "**"),
                            net.dv8tion.jda.api.components.textdisplay.TextDisplay.of("*Please try again*")
                    )
            ).withAccentColor(Color.RED);
            event.getHook().editOriginalComponents(errorContainer).useComponentsV2().queue();
        } catch (Exception e) {
            System.err.println("Error creating error container: " + e.getMessage());
        }
    }
    private static String extractImageUrlFromContainer(net.dv8tion.jda.api.entities.Message message) {
        try {
            return message.getComponentTree().getComponents().getFirst()
                    .asMediaGallery().getItems().getFirst().getUrl();
        } catch (Exception e) {
            return null;
        }
    }

    private void trackSourceUsage(String userId, String subcommand, String query) {
        if (query != null) {
            switch (subcommand) {
                case "reddit" -> {
                    Main.userService.addCustomSubreddit(userId, query);
                    Main.userService.trackSourceUsage(userId, "reddit");
                }
                case "google" -> {
                    Main.userService.addCustomQuery(userId, "google", query);
                    Main.userService.trackSourceUsage(userId, "google");
                }
                case "tenor" -> {
                    Main.userService.addCustomQuery(userId, "tenor", query);
                    Main.userService.trackSourceUsage(userId, "tenor");
                }
                case "4chan" -> {
                    Main.userService.addCustomQuery(userId, "4chan", query);
                    Main.userService.trackSourceUsage(userId, "4chan");
                }
            }
        } else {
            switch (subcommand) {
                case "all" -> Main.userService.trackSourceUsage(userId, "all");
                case "picsum" -> Main.userService.trackSourceUsage(userId, "picsum");
                case "imgur" -> Main.userService.trackSourceUsage(userId, "imgur");
                case "rulee34xxx" -> Main.userService.trackSourceUsage(userId, "rule34");
                case "movie" -> Main.userService.trackSourceUsage(userId, "tmdb-movie");
                case "tvshow" -> Main.userService.trackSourceUsage(userId, "tmdb-tv");
                case "youtube" -> Main.userService.trackSourceUsage(userId, "youtube");
                case "short" -> Main.userService.trackSourceUsage(userId, "youtube-shorts");
                case "urban" -> Main.userService.trackSourceUsage(userId, "urban-dictionary");
            }
        }
    }

    private boolean validateChannelAccess(SlashCommandInteractionEvent event, User user) {
        boolean isPrivateChannel = event.getChannelType() == ChannelType.PRIVATE;
        boolean isTextChannel = event.getChannelType() == ChannelType.TEXT;
        boolean isNsfwChannel = isTextChannel && event.getChannel().asTextChannel().isNSFW();

        if (isPrivateChannel && !user.isNsfw()) {
            errorHandler.sendErrorEmbed(event, "NSFW not enabled", "Please use the bot in an NSFW channel first");
            return false;
        }

        if (isTextChannel) {
            if (!user.isNsfw() && isNsfwChannel) {
                user.setNsfw(true);
            } else if (user.isNsfw() && !isNsfwChannel) {
                errorHandler.sendErrorEmbed(event, "Use in NSFW channel/DMs!", "Please use the bot in an NSFW channel or DMs!");
                return false;
            }
        }
        return true;
    }

    private void trackStats(String userId, String subcommand, User user) {
        if (Main.statsService != null) {
            Main.statsService.trackImageGenerated(userId, subcommand, user.isNsfw(), user.isPremium());
            Main.statsService.trackCommandUsed(userId, "random", user.isPremium());
            Main.statsService.trackUserActivity(userId, user.isPremium());
        }
    }

    private net.dv8tion.jda.api.components.container.Container createLoadingContainer(String avatarUrl) {
        return net.dv8tion.jda.api.components.container.Container.of(
                net.dv8tion.jda.api.components.section.Section.of(
                        net.dv8tion.jda.api.components.thumbnail.Thumbnail.fromUrl(avatarUrl),
                        net.dv8tion.jda.api.components.textdisplay.TextDisplay.of("## <a:loading:1350829863157891094> Generating Image..."),
                        net.dv8tion.jda.api.components.textdisplay.TextDisplay.of("**Please wait while we fetch your random image...**"),
                        net.dv8tion.jda.api.components.textdisplay.TextDisplay.of("*This may take a few seconds*")
                )
        ).withAccentColor(new Color(88, 101, 242));
    }
}