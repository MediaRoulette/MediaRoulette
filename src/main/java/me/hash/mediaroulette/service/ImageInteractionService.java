package me.hash.mediaroulette.service;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.MediaContainerManager;
import me.hash.mediaroulette.model.MessageData;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.locale.LocaleManager;
import me.hash.mediaroulette.utils.MaintenanceChecker;
import me.hash.mediaroulette.plugins.images.ImageSource;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.replacer.ComponentReplacer;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;

public class ImageInteractionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageInteractionService.class);

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
        if (instance == null) instance = new ImageInteractionService();
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
        if (!activeMessages.containsKey(messageId)) return;

        if (MaintenanceChecker.isMaintenanceBlocked(event)) {
            MaintenanceChecker.sendMaintenanceMessage(event);
            return;
        }

        event.deferEdit().queue();

        MessageData data = activeMessages.get(messageId);
        User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
        LocaleManager locale = LocaleManager.getInstance(user.getLocale());

        data.updateLastInteractionTime();

        if (!data.isUserAllowed(event.getUser().getIdLong())) {
            showError(event, locale.get("error.not_your_menu_title"), locale.get("error.not_your_menu"));
            return;
        }

        String buttonId = event.getButton().getCustomId();
        if (buttonId == null) {
            showError(event, locale.get("error.unknown_button_title"), locale.get("error.unknown_button_description"));
            return;
        }

        switch (buttonId) {
            case "nsfw:continue", "safe:continue" -> handleContinue(event, data);
            case "favorite" -> handleFavorite(event, data);
            case "nsfw", "safe" -> {
                QuestGenerator.onImageRated(user);
                Main.getUserService().updateUser(user);
                disableAllButtons(event);
            }
            case "exit" -> disableAllButtons(event);
            default -> showError(event, locale.get("error.unknown_button_title"), locale.get("error.unknown_button_description"));
        }
    }

    private void handleContinue(ButtonInteractionEvent event, MessageData data) {
        User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
        LocaleManager locale = LocaleManager.getInstance(user.getLocale());

        event.getHook().editOriginalComponents(createLoadingContainer(user))
                .useComponentsV2()
                .queue(success -> {
                    try {
                        Map<String, String> image = ImageSource.handle(data.getSubcommand().toUpperCase(), event, data.getQuery());
                        if (image == null || image.get("image") == null) {
                            showError(event, locale.get("error.no_more_images_title"), locale.get("error.no_more_images_description"));
                            return;
                        }
                        MediaContainerManager.editLoadingToImageContainerFromHook(event.getHook(), image, true);
                        user.incrementImagesGenerated();
                        QuestGenerator.onImageGenerated(user, data.getSubcommand());
                        Main.getUserService().updateUser(user);
                    } catch (Exception e) {
                        showError(event, locale.get("error.title"), e.getMessage());
                    }
                }, failure -> showError(event, locale.get("error.title"), failure.getMessage()));
    }

    private void handleFavorite(ButtonInteractionEvent event, MessageData data) {
        User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
        LocaleManager locale = LocaleManager.getInstance(user.getLocale());

        try {
            MediaContainerManager.ContainerData containerData = MediaContainerManager.extractContainerData(event.getMessage());
            String title = containerData.title != null ? containerData.title : locale.get("image.random_title", data.getSubcommand());
            String description = containerData.description != null ? containerData.description : locale.get("image.random_description", data.getSubcommand());
            String imageUrl = containerData.imageUrl;

            if (imageUrl == null || imageUrl.isBlank() || "none".equals(imageUrl)) {
                showError(event, locale.get("error.no_image_title"), "Cannot favorite: No image found");
                return;
            }

            Main.getUserService().addFavorite(user.getUserId(), title, description, imageUrl, "image", containerData.accentColor);
            QuestGenerator.onImageFavorited(user);
            Main.getUserService().updateUser(user);
            disableButton(event, "favorite");
        } catch (Exception e) {
            showError(event, locale.get("error.title"), e.getMessage());
        }
    }

    public Container createLoadingContainer(User user) {
        LocaleManager locale = LocaleManager.getInstance(user.getLocale());
        return Container.of(
                Section.of(
                        Thumbnail.fromUrl(user.getEffectiveAvatarUrl()),
                        TextDisplay.of("## <a:loading:1350829863157891094> " + locale.get("info.generating_image")),
                        TextDisplay.of("**" + locale.get("info.please_wait") + "**"),
                        TextDisplay.of("*" + locale.get("info.may_take_seconds") + "*")
                )
        ).withAccentColor(new Color(88, 101, 242));
    }

    private void showError(ButtonInteractionEvent event, String title, String description) {
        try {
            Container errorContainer = Container.of(
                    Section.of(
                            Thumbnail.fromUrl(event.getUser().getEffectiveAvatarUrl()),
                            TextDisplay.of("## ❌ " + title),
                            TextDisplay.of("**" + (description != null ? description : "An error occurred") + "**"),
                            TextDisplay.of("*Please try again*")
                    )
            ).withAccentColor(new Color(255, 107, 107));
            event.getHook().editOriginalComponents(errorContainer).useComponentsV2().queue(s -> {}, f -> {});
        } catch (Exception ignored) {}
    }

    private void disableButton(ButtonInteractionEvent event, String buttonId) {
        try {
            MessageComponentTree tree = event.getMessage().getComponentTree();
            ComponentReplacer replacer = ComponentReplacer.of(Button.class, b -> buttonId.equals(b.getCustomId()), Button::asDisabled);
            event.getHook().editOriginalComponents(tree.replace(replacer)).useComponentsV2().queue(
                    success -> {},
                    failure -> {
                        if (failure.getMessage() != null && failure.getMessage().contains("40005")) {
                            LOGGER.debug("Could not disable button '{}' due to large media - action still completed", buttonId);
                        } else {
                            LOGGER.warn("Failed to disable button '{}': {}", buttonId, failure.getMessage());
                        }
                    }
            );
        } catch (Exception e) {
            LOGGER.debug("Exception in disableButton: {}", e.getMessage());
        }
    }

    private void disableAllButtons(ButtonInteractionEvent event) {
        try {
            MessageComponentTree tree = event.getMessage().getComponentTree();
            ComponentReplacer replacer = ComponentReplacer.of(Button.class, b -> true, Button::asDisabled);
            event.getHook().editOriginalComponents(tree.replace(replacer)).useComponentsV2().queue(
                    success -> {},
                    failure -> {
                        if (failure.getMessage() != null && failure.getMessage().contains("40005")) {
                            LOGGER.debug("Could not disable buttons due to large media - action still completed");
                        } else {
                            LOGGER.warn("Failed to disable all buttons: {}", failure.getMessage());
                        }
                    }
            );
        } catch (Exception e) {
            LOGGER.debug("Exception in disableAllButtons: {}", e.getMessage());
        }
    }
}
