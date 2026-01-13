package me.hash.mediaroulette.bot;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.utils.EmbedFactory;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.utils.ColorExtractor;
import me.hash.mediaroulette.utils.media.FFmpegDownloader;
import me.hash.mediaroulette.utils.media.ffmpeg.FFmpegService;
import me.hash.mediaroulette.utils.media.image_generation.ImageGenerator;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.replacer.ComponentReplacer;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Unified manager for Discord embeds and containers.
 * Optimized with timeout handling and delegation to helper classes.
 */
public class MediaContainerManager {
    private static final Logger logger = LoggerFactory.getLogger(MediaContainerManager.class);

    private static final FFmpegService ffmpegService = new FFmpegService();
    private static boolean ffmpegInitialized = false;

    // ===== DELEGATED EMBED BUILDERS =====

    public static EmbedBuilder createBase() {
        return EmbedFactory.createBase();
    }

    public static EmbedBuilder createSuccess(String title, String description) {
        return EmbedFactory.createSuccess(title, description);
    }

    public static EmbedBuilder createError(String title, String description) {
        return EmbedFactory.createError(title, description);
    }

    public static EmbedBuilder createWarning(String title, String description) {
        return EmbedFactory.createWarning(title, description);
    }

    public static EmbedBuilder createInfo(String title, String description) {
        return EmbedFactory.createInfo(title, description);
    }

    public static EmbedBuilder createCooldown(String duration) {
        return EmbedFactory.createCooldown(duration);
    }

    public static EmbedBuilder createLoading(String title, String description) {
        return EmbedFactory.createLoading(title, description);
    }

    public static EmbedBuilder createEconomy(String title, String description, boolean isPremium) {
        return EmbedFactory.createEconomy(title, description, isPremium);
    }

    public static EmbedBuilder createUserEmbed(String title, String description,
                                               net.dv8tion.jda.api.entities.User discordUser,
                                               User botUser) {
        return EmbedFactory.createUserEmbed(title, description, discordUser, botUser);
    }

    public static EmbedBuilder createWithAuthor(String title, String description, Color color,
                                                net.dv8tion.jda.api.entities.User user) {
        return EmbedFactory.createWithAuthor(title, description, color, user);
    }

    // ===== DELEGATED FIELD HELPERS =====

    public static EmbedBuilder addCodeField(EmbedBuilder embed, String name, String value, boolean inline) {
        return EmbedFactory.addCodeField(embed, name, value, inline);
    }

    public static EmbedBuilder addEmojiField(EmbedBuilder embed, String emoji, String name,
                                             String value, boolean inline) {
        return EmbedFactory.addEmojiField(embed, emoji, name, value, inline);
    }

    public static EmbedBuilder addCoinField(EmbedBuilder embed, String name, long amount, boolean inline) {
        return EmbedFactory.addCoinField(embed, name, amount, inline);
    }

    public static EmbedBuilder addCountField(EmbedBuilder embed, String name, long count,
                                             String unit, boolean inline) {
        return EmbedFactory.addCountField(embed, name, count, unit, inline);
    }

    // ===== DELEGATED PAGINATION HELPERS =====

    public static ActionRow createPaginationButtons(String baseId, int currentPage, int totalPages,
                                                    String additionalData) {
        return EmbedFactory.createPaginationButtons(baseId, currentPage, totalPages, additionalData);
    }

    public static EmbedBuilder addPaginationFooter(EmbedBuilder embed, int currentPage, int totalPages,
                                                   int totalItems) {
        return EmbedFactory.addPaginationFooter(embed, currentPage, totalPages, totalItems);
    }

    // ===== CONTAINER OPERATIONS =====

    public static CompletableFuture<Message> sendImageContainer(Interaction event, Map<String, String> map, boolean shouldContinue) {
        String imageUrl = map.get("image");
        
        if (ffmpegService.shouldConvertToGif(imageUrl) && isFFmpegReady()) {
            return sendVideoContainerWithGif(event, map, shouldContinue);
        }
        
        return processContainer(event, map, shouldContinue, false);
    }

    public static void editImageContainer(ButtonInteractionEvent event, Map<String, String> map) {
        processContainer(event, map, true, true);
    }

    public static CompletableFuture<Message> sendImageContainer(InteractionHook hook, Map<String, String> map, boolean shouldContinue) {
        return processContainerFromHook(hook, map, shouldContinue, false);
    }

    public static CompletableFuture<Message> editLoadingToImageContainer(InteractionHook hook, Map<String, String> map, boolean shouldContinue) {
        String imageUrl = map.get("image");
        
        if (ffmpegService.shouldConvertToGif(imageUrl) && isFFmpegReady()) {
            return editLoadingToVideoContainerWithGif(hook, map, shouldContinue);
        }
        
        return processContainerFromHook(hook, map, shouldContinue, true);
    }

    public static void editLoadingToImageContainerFromHook(InteractionHook hook, Map<String, String> map, boolean shouldContinue) {
        processContainerFromHook(hook, map, shouldContinue, true);
    }

    // ===== BUTTON MANAGEMENT =====

    public static void disableButton(ButtonInteractionEvent event, String buttonId) {
        if (!event.isAcknowledged()) {
            event.deferEdit().queue(
                success -> updateButtonComponents(event, buttonId),
                failure -> logger.error("Failed to defer edit: {}", failure.getMessage())
            );
        } else {
            updateButtonComponents(event, buttonId);
        }
    }

    public static void disableAllButtons(ButtonInteractionEvent event) {
        if (!event.isAcknowledged()) {
            event.deferEdit().queue(
                success -> updateAllButtonComponents(event),
                failure -> logger.error("Failed to defer edit: {}", failure.getMessage())
            );
        } else {
            updateAllButtonComponents(event);
        }
    }

    // ===== CONTAINER EXTRACTION =====

    public static class ContainerData {
        public final String title;
        public final String description;
        public final String imageUrl;
        public final Integer accentColor;

        public ContainerData(String title, String description, String imageUrl, Integer accentColor) {
            this.title = title;
            this.description = description;
            this.imageUrl = imageUrl;
            this.accentColor = accentColor;
        }
    }

    public static ContainerData extractContainerData(net.dv8tion.jda.api.entities.Message message) {
        String title = null;
        String description = null;
        String imageUrl = null;
        Integer accentColor = null;

        try {
            var components = message.getComponents();
            if (components.isEmpty()) {
                return new ContainerData(null, null, null, null);
            }

            var container = components.getFirst().asContainer();
            var containerComponents = container.getComponents();

            try {
                java.awt.Color color = container.getAccentColor();
                if (color != null) {
                    accentColor = color.getRGB();
                }
            } catch (Exception ignored) { }

            for (var comp : containerComponents) {
                try {
                    if (comp.getType() == net.dv8tion.jda.api.components.Component.Type.SECTION) {
                        var section = comp.asSection();
                        var sectionComponents = section.getContentComponents();

                        for (var sectionComp : sectionComponents) {
                            try {
                                if (sectionComp.getType() == net.dv8tion.jda.api.components.Component.Type.TEXT_DISPLAY) {
                                    var textDisplay = sectionComp.asTextDisplay();
                                    String text = textDisplay.getContent();
                                    String trimmed = text.trim();

                                    if (trimmed.startsWith("## ")) {
                                        title = trimmed.substring(3).trim();
                                    } else if (trimmed.startsWith("**") && trimmed.endsWith("**") && trimmed.length() >= 4) {
                                        description = trimmed.substring(2, trimmed.length() - 2).trim();
                                    }
                                }
                            } catch (Exception ignored) { }
                        }
                    }
                    else if (comp.getType() == net.dv8tion.jda.api.components.Component.Type.MEDIA_GALLERY) {
                        var gallery = comp.asMediaGallery();
                        if (!gallery.getItems().isEmpty()) {
                            String url = gallery.getItems().getFirst().getUrl();
                            if (!url.isBlank() && !url.startsWith("attachment://")) {
                                imageUrl = url;
                            }
                        }
                    }
                } catch (Exception ignored) { }
            }

            if ((imageUrl == null || imageUrl.startsWith("attachment://")) && !message.getAttachments().isEmpty()) {
                String attachUrl = message.getAttachments().getFirst().getUrl();
                if (attachUrl != null && !attachUrl.isBlank()) {
                    imageUrl = attachUrl;
                }
            }

        } catch (Exception e) {
            logger.error("Error extracting container data: {}", e.getMessage());
        }

        return new ContainerData(title, description, imageUrl, accentColor);
    }

    // ===== PRIVATE IMPLEMENTATION =====

    private static CompletableFuture<Message> processContainer(Interaction event, Map<String, String> map,
                                                               boolean shouldContinue, boolean isEdit) {
        CompletableFuture<Message> future = new CompletableFuture<>();

        ColorExtractor.extractDominantColor(map.get("image"))
            .orTimeout(30, TimeUnit.SECONDS)
            .thenAccept(color -> {
                Container container = createImageContainer(event.getUser(), map, color, shouldContinue);

                if ("create".equals(map.get("image_type"))) {
                    handleGeneratedImageContainer(event, container, map, isEdit, future);
                } else if (isEdit) {
                    editMessageContainer((ButtonInteractionEvent) event, container, future);
                } else {
                    sendMessageContainer(event, container, future);
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error or timeout processing container: {}", throwable.getMessage());
                Container container = createImageContainer(event.getUser(), map, Color.CYAN, shouldContinue);
                if (isEdit) {
                    editMessageContainer((ButtonInteractionEvent) event, container, future);
                } else {
                    sendMessageContainer(event, container, future);
                }
                return null;
            });

        return future.orTimeout(45, TimeUnit.SECONDS);
    }

    private static CompletableFuture<Message> processContainerFromHook(InteractionHook hook, Map<String, String> map,
                                                                       boolean shouldContinue, boolean isEdit) {
        CompletableFuture<Message> future = new CompletableFuture<>();

        ColorExtractor.extractDominantColor(map.get("image"))
            .orTimeout(30, TimeUnit.SECONDS)
            .thenAccept(color -> {
                Container container = createImageContainer(hook.getInteraction().getUser(), map, color, shouldContinue);

                if ("create".equals(map.get("image_type"))) {
                    handleGeneratedImageContainerFromHook(hook, container, map, isEdit, future);
                } else if (isEdit) {
                    editMessageContainerFromHook(hook, container, future);
                } else {
                    sendMessageContainerFromHook(hook, container, future);
                }
            })
            .exceptionally(throwable -> {
                logger.error("Error or timeout processing container from hook: {}", throwable.getMessage());
                Container container = createImageContainer(hook.getInteraction().getUser(), map, Color.CYAN, shouldContinue);
                if (isEdit) {
                    editMessageContainerFromHook(hook, container, future);
                } else {
                    sendMessageContainerFromHook(hook, container, future);
                }
                return null;
            });

        return future.orTimeout(45, TimeUnit.SECONDS);
    }

    private static Container createImageContainer(net.dv8tion.jda.api.entities.User user, Map<String, String> map, 
                                                  Color color, boolean shouldContinue) {
        String userAvatarUrl = user.getEffectiveAvatarUrl();
        String title = map.get("title");
        String description = map.get("description");
        String imageUrl = map.get("image");
        String galleryUrlsStr = map.get("gallery_urls");
        
        Section headerSection = Section.of(
                Thumbnail.fromUrl(userAvatarUrl),
                TextDisplay.of("## " + title),
                TextDisplay.of("**" + description + "**")
        );

        List<Button> buttons = createImageButtons(shouldContinue);

        if (!"none".equals(imageUrl)) {
            // Create gallery with multiple items if gallery_urls is present
            MediaGallery gallery;
            if (galleryUrlsStr != null && !galleryUrlsStr.isEmpty()) {
                String[] urls = galleryUrlsStr.split("\\|");
                List<MediaGalleryItem> galleryItems = new java.util.ArrayList<>();
                int maxItems = Math.min(urls.length, 10); // Discord limit
                for (int i = 0; i < maxItems; i++) {
                    if (!urls[i].trim().isEmpty()) {
                        galleryItems.add(createSafeMediaGalleryItem(urls[i].trim()));
                    }
                }
                gallery = MediaGallery.of(galleryItems);
            } else {
                gallery = MediaGallery.of(createSafeMediaGalleryItem(imageUrl));
            }

            return Container.of(
                    headerSection,
                    Separator.createDivider(Separator.Spacing.SMALL),
                    gallery,
                    Separator.createDivider(Separator.Spacing.SMALL),
                    ActionRow.of(buttons)
            ).withAccentColor(color);
        } else {
            return Container.of(
                    headerSection,
                    Separator.createDivider(Separator.Spacing.SMALL),
                    ActionRow.of(buttons)
            ).withAccentColor(color);
        }
    }

    private static List<Button> createImageButtons(boolean shouldContinue) {
        String suffix = shouldContinue ? ":continue" : "";
        List<Button> buttons = List.of(
                Button.success("safe" + suffix, "Safe").withEmoji(Emoji.fromUnicode("✔️")),
                Button.primary("favorite", "Favorite").withEmoji(Emoji.fromUnicode("⭐")),
                Button.danger("nsfw" + suffix, "NSFW").withEmoji(Emoji.fromUnicode("🔞"))
        );

        return shouldContinue ?
                List.of(buttons.get(0), buttons.get(1), buttons.get(2),
                        Button.secondary("exit", "Exit").withEmoji(Emoji.fromUnicode("❌"))) :
                buttons;
    }

    private static void handleGeneratedImageContainer(Interaction event, Container container, Map<String, String> map,
                                                      boolean isEdit, CompletableFuture<Message> future) {
        try {
            User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
            byte[] imageBytes = new ImageGenerator().generateImage(map.get("image_content"), user.getTheme());
            FileUpload file = FileUpload.fromData(imageBytes, "image.png");

            if (isEdit) {
                ((ButtonInteractionEvent) event).getHook().editOriginalComponents(container)
                        .setFiles(file)
                        .useComponentsV2()
                        .queue(future::complete, future::completeExceptionally);
            } else {
                sendMessageContainerWithFile(event, container, file, future);
            }
        } catch (Exception e) {
            logger.error("Failed to handle generated image container: {}", e.getMessage(), e);
            future.completeExceptionally(e);
        }
    }

    private static void handleGeneratedImageContainerFromHook(InteractionHook hook, Container container, Map<String, String> map,
                                                              boolean isEdit, CompletableFuture<Message> future) {
        try {
            User user = Main.getUserService().getOrCreateUser(hook.getInteraction().getUser().getId());
            byte[] imageBytes = new ImageGenerator().generateImage(map.get("image_content"), user.getTheme());
            FileUpload file = FileUpload.fromData(imageBytes, "image.png");

            if (isEdit) {
                hook.editOriginalComponents(container)
                        .setFiles(file)
                        .useComponentsV2()
                        .queue(future::complete, future::completeExceptionally);
            } else {
                sendMessageContainerWithFileFromHook(hook, container, file, future);
            }
        } catch (Exception e) {
            logger.error("Failed to handle generated image container from hook: {}", e.getMessage(), e);
            future.completeExceptionally(e);
        }
    }

    private static void sendMessageContainer(Interaction event, Container container, CompletableFuture<Message> future) {
        if (event instanceof net.dv8tion.jda.api.interactions.callbacks.IReplyCallback replyCallback) {
            if (replyCallback.isAcknowledged()) {
                replyCallback.getHook().sendMessageComponents(container)
                        .useComponentsV2()
                        .queue(future::complete, future::completeExceptionally);
            } else {
                replyCallback.replyComponents(container)
                        .useComponentsV2()
                        .queue(hook -> hook.retrieveOriginal().queue(future::complete), future::completeExceptionally);
            }
        }
    }

    private static void sendMessageContainerFromHook(InteractionHook hook, Container container, CompletableFuture<Message> future) {
        hook.sendMessageComponents(container)
                .useComponentsV2()
                .queue(future::complete, future::completeExceptionally);
    }

    private static void sendMessageContainerWithFile(Interaction event, Container container, FileUpload file,
                                                      CompletableFuture<Message> future) {
        if (event instanceof net.dv8tion.jda.api.interactions.callbacks.IReplyCallback replyCallback) {
            if (replyCallback.isAcknowledged()) {
                replyCallback.getHook().sendMessageComponents(container)
                        .addFiles(file)
                        .useComponentsV2()
                        .queue(future::complete, future::completeExceptionally);
            } else {
                replyCallback.replyComponents(container)
                        .addFiles(file)
                        .useComponentsV2()
                        .queue(hook -> hook.retrieveOriginal().queue(future::complete), future::completeExceptionally);
            }
        }
    }

    private static void sendMessageContainerWithFileFromHook(InteractionHook hook, Container container, FileUpload file,
                                                              CompletableFuture<Message> future) {
        hook.sendMessageComponents(container)
                .addFiles(file)
                .useComponentsV2()
                .queue(future::complete, future::completeExceptionally);
    }

    private static void editMessageContainer(ButtonInteractionEvent event, Container container, CompletableFuture<Message> future) {
        event.getHook().editOriginalComponents(container)
                .useComponentsV2()
                .queue(future::complete, future::completeExceptionally);
    }

    private static void editMessageContainerFromHook(InteractionHook hook, Container container, CompletableFuture<Message> future) {
        hook.editOriginalComponents(container)
                .useComponentsV2()
                .queue(future::complete, future::completeExceptionally);
    }

    private static void updateButtonComponents(ButtonInteractionEvent event, String buttonId) {
        MessageComponentTree components = event.getMessage().getComponentTree();
        ComponentReplacer replacer = ComponentReplacer.of(
                Button.class,
                button -> buttonId.equals(button.getCustomId()),
                Button::asDisabled
        );
        MessageComponentTree updated = components.replace(replacer);
        
        if (event.isAcknowledged()) {
            event.getHook().editOriginalComponents(updated).queue();
        } else {
            event.editComponents(updated).queue();
        }
    }

    private static void updateAllButtonComponents(ButtonInteractionEvent event) {
        MessageComponentTree components = event.getMessage().getComponentTree();
        ComponentReplacer replacer = ComponentReplacer.of(
                Button.class,
                button -> true,
                Button::asDisabled
        );
        MessageComponentTree updated = components.replace(replacer);
        
        if (event.isAcknowledged()) {
            event.getHook().editOriginalComponents(updated).queue();
        } else {
            event.editComponents(updated).queue();
        }
    }

    // ===== FFMPEG INITIALIZATION =====

    public static CompletableFuture<Void> initializeFFmpeg() {
        if (ffmpegInitialized) {
            return CompletableFuture.completedFuture(null);
        }

        return FFmpegDownloader.getFFmpegPath().thenCompose(path -> FFmpegDownloader.getFFmpegVersion().thenAccept(version -> {
            logger.info("FFmpeg initialized: {}", version);
            ffmpegInitialized = true;
        })).exceptionally(throwable -> {
            logger.error("Failed to initialize FFmpeg: {}", throwable.getMessage());
            return null;
        });
    }

    public static boolean isFFmpegReady() {
        return ffmpegInitialized && FFmpegDownloader.isFFmpegAvailable();
    }
    
    public static CompletableFuture<Boolean> isVideoProcessingReady() {
        if (!isFFmpegReady()) {
            return CompletableFuture.completedFuture(false);
        }
        return FFmpegDownloader.isFFprobeAvailable();
    }

    public static CompletableFuture<String> getVideoInfoText(String videoUrl) {
        return isVideoProcessingReady().thenCompose(ready -> {
            if (!ready) {
                return CompletableFuture.completedFuture("Video processing not available");
            }

            return ffmpegService.getVideoInfo(videoUrl).thenApply(info -> String.format("📹 **%s** • ⏱️ **%s** • 🎬 **%s**",
                    info.getResolution(),
                    info.getFormattedDuration(),
                    info.getCodec())).exceptionally(throwable -> {
                logger.error("Failed to get video info: {}", throwable.getMessage());
                return "📹 Video information unavailable";
            });
        });
    }

    public static CompletableFuture<byte[]> createVideoThumbnail(String videoUrl) {
        return isVideoProcessingReady().thenCompose(ready -> {
            if (!ready) {
                return CompletableFuture.completedFuture(null);
            }

            return ffmpegService.getVideoInfo(videoUrl).thenCompose(info -> {
                double thumbnailTime = info.getDuration() * 0.25;
                return ffmpegService.extractThumbnail(videoUrl, thumbnailTime);
            }).thenApply(thumbnail -> {
                try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                    javax.imageio.ImageIO.write(thumbnail, "jpg", baos);
                    baos.flush();
                    byte[] result = baos.toByteArray();
                    if (thumbnail != null) {
                        thumbnail.flush();
                    }
                    return result;
                } catch (Exception e) {
                    logger.error("Failed to convert thumbnail to bytes: {}", e.getMessage());
                    if (thumbnail != null) {
                        thumbnail.flush();
                    }
                    return null;
                }
            }).exceptionally(throwable -> {
                logger.error("Failed to create video thumbnail: {}", throwable.getMessage());
                return null;
            });
        });
    }

    public static CompletableFuture<Container> createEnhancedVideoContainer(net.dv8tion.jda.api.entities.User user, 
                                                                            Map<String, String> map, 
                                                                            boolean shouldContinue) {
        String imageUrl = map.get("image");
        
        if (!ffmpegService.isVideoUrl(imageUrl)) {
            return ColorExtractor.extractDominantColor(imageUrl)
                .orTimeout(30, TimeUnit.SECONDS)
                .thenApply(color -> createImageContainer(user, map, color, shouldContinue));
        }

        return getVideoInfoText(imageUrl).thenCombine(
            ColorExtractor.extractDominantColor(imageUrl).orTimeout(30, TimeUnit.SECONDS),
            (videoInfo, color) -> {
                String userAvatarUrl = user.getEffectiveAvatarUrl();
                String title = map.get("title");
                String description = map.get("description");
                
                Section headerSection = Section.of(
                        Thumbnail.fromUrl(userAvatarUrl),
                        TextDisplay.of("## " + title),
                        TextDisplay.of("**" + description + "**"),
                        TextDisplay.of(videoInfo),
                        TextDisplay.of("*Generated by " + user.getName() + "*")
                );

                List<Button> buttons = createImageButtons(shouldContinue);

                return Container.of(
                        headerSection,
                        Separator.createDivider(Separator.Spacing.SMALL),
                        MediaGallery.of(createSafeMediaGalleryItem(imageUrl)),
                        Separator.createDivider(Separator.Spacing.SMALL),
                        ActionRow.of(buttons)
                ).withAccentColor(color);
            }
        );
    }
    
    public static CompletableFuture<Message> sendVideoContainerWithGif(Interaction event, Map<String, String> map, boolean shouldContinue) {
        String imageUrl = map.get("image");
        
        if (!ffmpegService.shouldConvertToGif(imageUrl)) {
            return sendImageContainer(event, map, shouldContinue);
        }
        
        return ffmpegService.resolveVideoUrl(imageUrl)
            .orTimeout(30, TimeUnit.SECONDS)
            .thenCompose(resolvedUrl -> {
                if (resolvedUrl.equals(imageUrl) || !isDirectVideoUrl(resolvedUrl)) {
                    return processContainer(event, map, shouldContinue, false);
                }
                
                Map<String, String> updatedMap = new java.util.HashMap<>(map);
                updatedMap.put("image", resolvedUrl);
                updatedMap.put("original_video_url", imageUrl);

                logger.info("Using direct video URL: {}", resolvedUrl);
                
                return ColorExtractor.extractDominantColor(map.get("image"))
                    .orTimeout(30, TimeUnit.SECONDS)
                    .thenApply(color -> {
                        Container container = createImageContainer(event.getUser(), updatedMap, color, shouldContinue);
                        
                        CompletableFuture<Message> future = new CompletableFuture<>();
                        
                        if (event instanceof net.dv8tion.jda.api.interactions.callbacks.IReplyCallback replyCallback) {
                            if (replyCallback.isAcknowledged()) {
                                replyCallback.getHook().sendMessageComponents(container)
                                        .useComponentsV2()
                                        .queue(future::complete, future::completeExceptionally);
                            } else {
                                replyCallback.replyComponents(container)
                                        .useComponentsV2()
                                        .queue(hook -> hook.retrieveOriginal().queue(future::complete), future::completeExceptionally);
                            }
                        }
                        
                        return future;
                    }).thenCompose(f -> f);
            })
            .exceptionallyCompose(throwable -> {
                logger.error("Video resolution failed, falling back to regular container: {}", throwable.getMessage());
                return processContainer(event, map, shouldContinue, false);
            });
    }
    
    public static CompletableFuture<Message> editLoadingToVideoContainerWithGif(InteractionHook hook, Map<String, String> map, boolean shouldContinue) {
        String imageUrl = map.get("image");
        
        if (!ffmpegService.shouldConvertToGif(imageUrl)) {
            return editLoadingToImageContainer(hook, map, shouldContinue);
        }
        
        return ffmpegService.resolveVideoUrl(imageUrl)
            .orTimeout(30, TimeUnit.SECONDS)
            .thenCompose(resolvedUrl -> {
                if (resolvedUrl.equals(imageUrl) || !isDirectVideoUrl(resolvedUrl)) {
                    return processContainerFromHook(hook, map, shouldContinue, true);
                }
                
                Map<String, String> updatedMap = new java.util.HashMap<>(map);
                updatedMap.put("image", resolvedUrl);
                updatedMap.put("original_video_url", imageUrl);

                logger.debug("Using direct video URL: {}", resolvedUrl);
                
                return ColorExtractor.extractDominantColor(map.get("image"))
                    .orTimeout(30, TimeUnit.SECONDS)
                    .thenApply(color -> {
                        Container container = createImageContainer(hook.getInteraction().getUser(), updatedMap, color, shouldContinue);
                        
                        CompletableFuture<Message> future = new CompletableFuture<>();
                        
                        hook.editOriginalComponents(container)
                                .useComponentsV2()
                                .queue(future::complete, future::completeExceptionally);
                        
                        return future;
                    }).thenCompose(f -> f);
            })
            .exceptionallyCompose(throwable -> {
                logger.error("Video resolution failed, falling back to regular container: {}", throwable.getMessage());
                return processContainerFromHook(hook, map, shouldContinue, true);
            });
    }

    private static boolean isDirectVideoUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        String lowerUrl = url.toLowerCase();
        return lowerUrl.contains(".mp4") || 
               lowerUrl.contains(".m4s") || 
               lowerUrl.contains(".m4v") || 
               lowerUrl.contains(".webm") || 
               lowerUrl.contains(".mov");
    }
    
    /**
     * Creates a MediaGalleryItem with a valid filename to avoid "Name may not be blank" errors.
     * JDA's FileProxy.downloadAsFileUpload requires a valid filename, which may not be present
     * in some URLs (e.g., API endpoints, URLs without extensions, etc.).
     * 
     * This method ensures the URL has a proper filename by appending one if needed.
     */
    private static MediaGalleryItem createSafeMediaGalleryItem(String url) {
        if (url == null || url.isBlank()) {
            // Use a simple placeholder URL that has a valid filename
            return MediaGalleryItem.fromUrl("https://via.placeholder.com/400x300.png");
        }
        
        String filename = extractFilenameFromUrl(url);
        
        // If filename is valid, use URL as-is
        if (filename != null && !filename.isBlank() && filename.contains(".")) {
            return MediaGalleryItem.fromUrl(url);
        }
        
        // URL doesn't have a valid filename - we need to append one
        // JDA will extract filename from URL path, so we add a fake filename via fragment
        // that gets ignored by the server but gives JDA a filename to use
        String extension = detectMediaExtension(url);
        String safeUrl = appendFilenameToUrl(url, "media." + extension);
        
        return MediaGalleryItem.fromUrl(safeUrl);
    }
    
    /**
     * Appends a filename to the URL using a query parameter or path trick.
     * Discord/JDA extracts the filename from the last path segment.
     */
    private static String appendFilenameToUrl(String url, String filename) {
        // Check if URL already has query params
        if (url.contains("?")) {
            // Add as additional query param - most servers ignore unknown params
            return url + "&_fn=" + filename;
        } else {
            // Add as query param with the filename
            return url + "?_fn=" + filename;
        }
    }
    
    /**
     * Extracts the filename from a URL, handling query parameters and fragments.
     */
    private static String extractFilenameFromUrl(String url) {
        try {
            // Strip query params and fragments
            int queryIndex = url.indexOf('?');
            int fragmentIndex = url.indexOf('#');
            int endIndex = url.length();
            
            if (queryIndex != -1) endIndex = Math.min(endIndex, queryIndex);
            if (fragmentIndex != -1) endIndex = Math.min(endIndex, fragmentIndex);
            
            String urlPath = url.substring(0, endIndex);
            
            // Get the last path segment
            int lastSlashIndex = urlPath.lastIndexOf('/');
            if (lastSlashIndex != -1 && lastSlashIndex < urlPath.length() - 1) {
                String filename = urlPath.substring(lastSlashIndex + 1);
                // Check if it looks like a valid filename (has an extension)
                if (filename.contains(".") && filename.length() > 1) {
                    return filename;
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to extract filename from URL: {}", url);
        }
        return null;
    }
    
    /**
     * Detects the appropriate media extension based on URL patterns.
     */
    private static String detectMediaExtension(String url) {
        if (url == null) return "png";
        
        String lowerUrl = url.toLowerCase();
        
        // Check for video extensions
        if (lowerUrl.contains(".mp4") || lowerUrl.contains("video") || lowerUrl.contains("mp4")) {
            return "mp4";
        }
        if (lowerUrl.contains(".webm")) return "webm";
        if (lowerUrl.contains(".mov")) return "mov";
        if (lowerUrl.contains(".gif") || lowerUrl.contains("gif")) return "gif";
        
        // Check for image extensions
        if (lowerUrl.contains(".webp") || lowerUrl.contains("webp")) return "webp";
        if (lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg") || lowerUrl.contains("jpeg")) return "jpg";
        if (lowerUrl.contains(".png") || lowerUrl.contains("png")) return "png";
        
        // Check for known video platforms
        if (lowerUrl.contains("redgifs") || lowerUrl.contains("gfycat")) return "mp4";
        
        // Default to png for images
        return "png";
    }

    public static void cleanup() {
        ffmpegService.cleanupTempFiles();
    }
}