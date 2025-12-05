package me.hash.mediaroulette.service;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.MediaContainerManager;
import me.hash.mediaroulette.model.MessageData;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.utils.LocaleManager;
import me.hash.mediaroulette.utils.MaintenanceChecker;
import me.hash.mediaroulette.utils.QuestGenerator;
import me.hash.mediaroulette.plugins.Images.ImageSource;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.replacer.ComponentReplacer;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.awt.Color;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

public class ImageInteractionService {
    
    private static ImageInteractionService instance;
    private final Map<Long, MessageData> activeMessages = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    private static final long INACTIVITY_TIMEOUT = TimeUnit.MINUTES.toMillis(3);

    private ImageInteractionService() {
        cleanupExecutor.scheduleAtFixedRate(() -> {
            long now = Instant.now().toEpochMilli();
            activeMessages.entrySet().removeIf(entry -> {
                MessageData data = entry.getValue();
                if (now - data.getLastInteractionTime() > INACTIVITY_TIMEOUT) {
                    data.disableButtons();
                    return true;
                }
                return false;
            });
        }, 1, 1, TimeUnit.MINUTES);
    }

    public static synchronized ImageInteractionService getInstance() {
        if (instance == null) {
            instance = new ImageInteractionService();
        }
        return instance;
    }
    
    public void registerMessage(long messageId, MessageData data) {
        activeMessages.put(messageId, data);
    }

    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void handleButtonInteraction(ButtonInteractionEvent event) {
        long messageId = event.getMessageIdLong();

        if (!activeMessages.containsKey(messageId)) {
            return;
        }

        if (MaintenanceChecker.isMaintenanceBlocked(event)) {
            MaintenanceChecker.sendMaintenanceMessage(event);
            return;
        }

        event.deferEdit().queue();
        
        // Note: We are assuming the caller (Bot listener) handles the threading, 
        // or we can run this async if needed. For now, running logic here.
        // The original code ran this inside Bot.executor.execute()
        
        MessageData data = activeMessages.get(messageId);
        User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
        LocaleManager localeManager = LocaleManager.getInstance(user.getLocale());

        data.updateLastInteractionTime();

        if (!data.isUserAllowed(event.getUser().getIdLong())) {
            event.getHook().sendMessage(localeManager.get("error.not_your_menu")).setEphemeral(true).queue();
            return;
        }

        String buttonId = event.getButton().getCustomId();

        if (buttonId == null) {
            showErrorContainer(event, localeManager.get("error.unknown_button_title"),
                    localeManager.get("error.unknown_button_description"));
            return;
        }

        switch (buttonId) {
            case "nsfw:continue":
            case "safe:continue":
                handleContinue(event, data);
                break;
            case "favorite":
                handleFavorite(event, data);
                break;
            case "nsfw":
            case "safe":
                QuestGenerator.onImageRated(user);
                Main.getUserService().updateUser(user);
                disableAllButtonsInContainer(event);
                break;
            case "exit":
                disableAllButtonsInContainer(event);
                break;
            default:
                showErrorContainer(event, localeManager.get("error.unknown_button_title"),
                        localeManager.get("error.unknown_button_description"));
        }
    }

    private void handleContinue(ButtonInteractionEvent event, MessageData data) {
        User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
        LocaleManager localeManager = LocaleManager.getInstance(user.getLocale());

        event.getHook().editOriginalComponents(createLoadingContainer(event.getUser().getEffectiveAvatarUrl()))
                .useComponentsV2()
                .queue(success -> {
                    try {
                        // Using ImageSource directly here as per original logic, but could use ImageRequestService if we want 
                        Map<String, String> image = ImageSource.handle(data.getSubcommand().toUpperCase(), event, data.getQuery());
                        if (image == null || image.get("image") == null) {
                            showErrorContainer(event, localeManager.get("error.no_more_images_title"),
                                    localeManager.get("error.no_more_images_description"));
                            return;
                        }
                        MediaContainerManager.editLoadingToImageContainerFromHook(event.getHook(), image, true);

                        user.incrementImagesGenerated();
                        QuestGenerator.onImageGenerated(user, data.getSubcommand());
                        Main.getUserService().updateUser(user);
                    } catch (Exception e) {
                        showErrorContainer(event, localeManager.get("error.title"), e.getMessage());
                    }
                });
    }

    private void handleFavorite(ButtonInteractionEvent event, MessageData data) {
        User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
        LocaleManager localeManager = LocaleManager.getInstance(user.getLocale());

        try {
            MediaContainerManager.ContainerData containerData = MediaContainerManager.extractContainerData(event.getMessage());
            String title = containerData.title != null ? containerData.title : ("Random " + data.getSubcommand());
            String description = containerData.description != null ? containerData.description : ("Random " + data.getSubcommand() + " image");
            String imageUrl = containerData.imageUrl;
            Integer accentColor = containerData.accentColor;

            if (imageUrl == null || imageUrl.isBlank() || "none".equals(imageUrl)) {
                showErrorContainer(event, localeManager.get("error.no_image_title"),
                        "Cannot favorite: No image found in this container");
                return;
            }

            Main.getUserService().addFavorite(user.getUserId(), title, description, imageUrl, "image", accentColor);
            QuestGenerator.onImageFavorited(user);
            Main.getUserService().updateUser(user);
            disableButtonInContainer(event, "favorite");
        } catch (Exception e) {
            showErrorContainer(event, localeManager.get("error.title"), e.getMessage());
        }
    }

    public Container createLoadingContainer(String avatarUrl) {
        return Container.of(
                Section.of(
                        Thumbnail.fromUrl(avatarUrl),
                        TextDisplay.of("## <a:loading:1350829863157891094> Generating Image..."),
                        TextDisplay.of("**Please wait while we fetch your random image...**"),
                        TextDisplay.of("*This may take a few seconds*")
                )
        ).withAccentColor(new Color(88, 101, 242));
    }

    public void showErrorContainer(ButtonInteractionEvent event, String title, String description) {
        try {
            Container errorContainer = Container.of(
                    Section.of(
                            Thumbnail.fromUrl(event.getUser().getEffectiveAvatarUrl()),
                            TextDisplay.of("## ❌ " + title),
                            TextDisplay.of("**" + description + "**"),
                            TextDisplay.of("*Please try again*")
                    )
            ).withAccentColor(Color.RED);
            event.getHook().editOriginalComponents(errorContainer).useComponentsV2().queue(
                success -> {}, 
                failure -> {} 
            );
        } catch (Exception e) {
            // Ignore
        }
    }

    private void disableButtonInContainer(ButtonInteractionEvent event, String buttonId) {
        try {
            MessageComponentTree components = event.getMessage().getComponentTree();
            ComponentReplacer replacer = ComponentReplacer.of(Button.class, button -> buttonId.equals(button.getCustomId()), Button::asDisabled);
            MessageComponentTree updated = components.replace(replacer);
            event.getHook().editOriginalComponents(updated).useComponentsV2().queue();
        } catch (Exception e) {
            // Ignore
        }
    }

    private void disableAllButtonsInContainer(ButtonInteractionEvent event) {
        try {
            MessageComponentTree components = event.getMessage().getComponentTree();
            ComponentReplacer replacer = ComponentReplacer.of(Button.class, button -> true, Button::asDisabled);
            MessageComponentTree updated = components.replace(replacer);
            event.getHook().editOriginalComponents(updated).useComponentsV2().queue();
        } catch (Exception e) {
            // Ignore
        }
    }
}
