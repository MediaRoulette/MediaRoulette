package me.hash.mediaroulette.bot;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.utils.media.image_generation.ImageGenerator;
import me.hash.mediaroulette.utils.media.ffmpeg.FFmpegService;
import me.hash.mediaroulette.utils.media.FFmpegDownloader;
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

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Unified manager for Discord embeds and containers.
 * Replaces both Embeds.java and LoadingEmbeds.java with optimized, non-duplicated code.
 */
public class MediaContainerManager {
    private static final FFmpegService ffmpegService = new FFmpegService();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static boolean ffmpegInitialized = false;

    // ===== COLOR CONSTANTS =====
    public static final Color SUCCESS_COLOR = new Color(87, 242, 135);
    public static final Color ERROR_COLOR = new Color(255, 107, 107);
    public static final Color WARNING_COLOR = new Color(255, 193, 7);
    public static final Color INFO_COLOR = new Color(52, 152, 219);
    public static final Color PRIMARY_COLOR = new Color(114, 137, 218);
    public static final Color PREMIUM_COLOR = new Color(138, 43, 226);
    public static final Color COIN_COLOR = new Color(255, 215, 0);
    public static final Color COOLDOWN_COLOR = new Color(255, 107, 107);

    // ===== EMBED BUILDERS =====

    public static EmbedBuilder createBase() {
        return new EmbedBuilder().setTimestamp(Instant.now());
    }

    public static EmbedBuilder createSuccess(String title, String description) {
        return createBase()
                .setTitle("✅ " + title)
                .setDescription(description)
                .setColor(SUCCESS_COLOR);
    }

    public static EmbedBuilder createError(String title, String description) {
        return createBase()
                .setTitle("❌ " + title)
                .setDescription(description)
                .setColor(ERROR_COLOR);
    }

    public static EmbedBuilder createWarning(String title, String description) {
        return createBase()
                .setTitle("⚠️ " + title)
                .setDescription(description)
                .setColor(WARNING_COLOR);
    }

    public static EmbedBuilder createInfo(String title, String description) {
        return createBase()
                .setTitle("ℹ️ " + title)
                .setDescription(description)
                .setColor(INFO_COLOR);
    }

    public static EmbedBuilder createCooldown(String duration) {
        return createBase()
                .setTitle("⏰ Slow Down!")
                .setDescription("Please wait **" + duration + "** before using this command again.")
                .setColor(COOLDOWN_COLOR);
    }

    public static EmbedBuilder createLoading(String title, String description) {
        return createBase()
                .setTitle("⏳ " + title)
                .setDescription(description)
                .setColor(INFO_COLOR);
    }

    public static EmbedBuilder createEconomy(String title, String description, boolean isPremium) {
        return createBase()
                .setTitle("💰 " + title)
                .setDescription(description)
                .setColor(isPremium ? PREMIUM_COLOR : COIN_COLOR);
    }

    public static EmbedBuilder createUserEmbed(String title, String description,
                                               net.dv8tion.jda.api.entities.User discordUser,
                                               User botUser) {
        EmbedBuilder embed = createBase()
                .setTitle(title)
                .setDescription(description)
                .setColor(botUser != null && botUser.isPremium() ? PREMIUM_COLOR : PRIMARY_COLOR);

        if (discordUser.getAvatarUrl() != null) {
            embed.setThumbnail(discordUser.getAvatarUrl());
        }

        return embed;
    }

    public static EmbedBuilder createWithAuthor(String title, String description, Color color,
                                                net.dv8tion.jda.api.entities.User user) {
        return createBase()
                .setTitle(title)
                .setDescription(description)
                .setColor(color)
                .setAuthor(user.getName(), null, user.getEffectiveAvatarUrl());
    }

    // ===== EMBED FIELD HELPERS =====

    public static EmbedBuilder addCodeField(EmbedBuilder embed, String name, String value, boolean inline) {
        return embed.addField(name, "```" + value + "```", inline);
    }

    public static EmbedBuilder addEmojiField(EmbedBuilder embed, String emoji, String name,
                                             String value, boolean inline) {
        return embed.addField(emoji + " " + name, value, inline);
    }

    public static EmbedBuilder addCoinField(EmbedBuilder embed, String name, long amount, boolean inline) {
        return addCodeField(embed, name, String.format("💰 %,d coins", amount), inline);
    }

    public static EmbedBuilder addCountField(EmbedBuilder embed, String name, long count,
                                             String unit, boolean inline) {
        return addCodeField(embed, name, String.format("%,d %s", count, unit), inline);
    }

    // ===== PAGINATION HELPERS =====

    public static ActionRow createPaginationButtons(String baseId, int currentPage, int totalPages,
                                                    String additionalData) {
        String data = additionalData != null ? ":" + additionalData : "";

        return ActionRow.of(
                Button.primary(baseId + ":prev:" + (currentPage - 1) + data, "◀ Previous")
                        .withDisabled(currentPage <= 1),
                Button.primary(baseId + ":next:" + (currentPage + 1) + data, "Next ▶")
                        .withDisabled(currentPage >= totalPages),
                Button.secondary(baseId + ":refresh:" + currentPage + data, "🔄 Refresh")
        );
    }

    public static EmbedBuilder addPaginationFooter(EmbedBuilder embed, int currentPage, int totalPages,
                                                   int totalItems) {
        return embed.setFooter(String.format("Page %d/%d • %d items total",
                currentPage, totalPages, totalItems), null);
    }

    // ===== CONTAINER OPERATIONS =====

    /**
     * Sends a new image container (with automatic GIF conversion for supported videos)
     */
    public static CompletableFuture<Message> sendImageContainer(Interaction event, Map<String, String> map, boolean shouldContinue) {
        String imageUrl = map.get("image");
        
        // Check if we should convert video to GIF for better display
        if (ffmpegService.shouldConvertToGif(imageUrl) && isFFmpegReady()) {
            return sendVideoContainerWithGif(event, map, shouldContinue);
        }
        
        return processContainer(event, map, shouldContinue, false);
    }

    /**
     * Edits an existing container from button interaction
     */
    public static void editImageContainer(ButtonInteractionEvent event, Map<String, String> map) {
        processContainer(event, map, true, true);
    }

    /**
     * Sends a new image container from hook
     */
    public static CompletableFuture<Message> sendImageContainer(InteractionHook hook, Map<String, String> map, boolean shouldContinue) {
        return processContainerFromHook(hook, map, shouldContinue, false);
    }

    /**
     * Edits loading message to show final image container
     */
    public static CompletableFuture<Message> editLoadingToImageContainer(InteractionHook hook, Map<String, String> map, boolean shouldContinue) {
        String imageUrl = map.get("image");
        
        // Check if we should convert video to GIF for better display
        if (ffmpegService.shouldConvertToGif(imageUrl) && isFFmpegReady()) {
            return editLoadingToVideoContainerWithGif(hook, map, shouldContinue);
        }
        
        return processContainerFromHook(hook, map, shouldContinue, true);
    }

    /**
     * Edits loading message to show final image container (void version)
     */
    public static void editLoadingToImageContainerFromHook(InteractionHook hook, Map<String, String> map, boolean shouldContinue) {
        processContainerFromHook(hook, map, shouldContinue, true);
    }

    // ===== BUTTON MANAGEMENT =====

    public static void disableButton(ButtonInteractionEvent event, String buttonId) {
        if (!event.isAcknowledged()) {
            event.deferEdit().queue(
                success -> updateButtonComponents(event, buttonId),
                failure -> System.err.println("Failed to defer edit: " + failure.getMessage())
            );
        } else {
            updateButtonComponents(event, buttonId);
        }
    }

    public static void disableAllButtons(ButtonInteractionEvent event) {
        if (!event.isAcknowledged()) {
            event.deferEdit().queue(
                success -> updateAllButtonComponents(event),
                failure -> System.err.println("Failed to defer edit: " + failure.getMessage())
            );
        } else {
            updateAllButtonComponents(event);
        }
    }

    // ===== PRIVATE IMPLEMENTATION =====

    private static CompletableFuture<Message> processContainer(Interaction event, Map<String, String> map,
                                                               boolean shouldContinue, boolean isEdit) {
        CompletableFuture<Message> future = new CompletableFuture<>();

        extractDominantColor(map).thenAccept(color -> {
            Container container = createImageContainer(event.getUser(), map, color, shouldContinue);

            if ("create".equals(map.get("image_type"))) {
                handleGeneratedImageContainer(event, container, map, isEdit, future);
            } else if (isEdit) {
                editMessageContainer((ButtonInteractionEvent) event, container, future);
            } else {
                sendMessageContainer(event, container, future);
            }
        }).exceptionally(throwable -> {
            Container container = createImageContainer(event.getUser(), map, Color.CYAN, shouldContinue);
            if (isEdit) {
                editMessageContainer((ButtonInteractionEvent) event, container, future);
            } else {
                sendMessageContainer(event, container, future);
            }
            return null;
        });

        return future;
    }

    private static CompletableFuture<Message> processContainerFromHook(InteractionHook hook, Map<String, String> map,
                                                                       boolean shouldContinue, boolean isEdit) {
        CompletableFuture<Message> future = new CompletableFuture<>();

        extractDominantColor(map).thenAccept(color -> {
            Container container = createImageContainer(hook.getInteraction().getUser(), map, color, shouldContinue);

            if ("create".equals(map.get("image_type"))) {
                handleGeneratedImageContainerFromHook(hook, container, map, isEdit, future);
            } else if (isEdit) {
                editMessageContainerFromHook(hook, container, future);
            } else {
                sendMessageContainerFromHook(hook, container, future);
            }
        }).exceptionally(throwable -> {
            Container container = createImageContainer(hook.getInteraction().getUser(), map, Color.CYAN, shouldContinue);
            if (isEdit) {
                editMessageContainerFromHook(hook, container, future);
            } else {
                sendMessageContainerFromHook(hook, container, future);
            }
            return null;
        });

        return future;
    }

    private static Container createImageContainer(net.dv8tion.jda.api.entities.User user, Map<String, String> map, 
                                                  Color color, boolean shouldContinue) {
        String userAvatarUrl = user.getEffectiveAvatarUrl();
        String title = map.get("title");
        String description = map.get("description");
        String imageUrl = map.get("image");
        
        Section headerSection = Section.of(
                Thumbnail.fromUrl(userAvatarUrl),
                TextDisplay.of("## " + title),
                TextDisplay.of("**" + description + "**"),
                TextDisplay.of("*Generated by " + user.getName() + "*")
        );

        List<Button> buttons = createImageButtons(shouldContinue);

        if (!"none".equals(imageUrl) && !imageUrl.startsWith("attachment://")) {
            return Container.of(
                    headerSection,
                    Separator.createDivider(Separator.Spacing.SMALL),
                    MediaGallery.of(MediaGalleryItem.fromUrl(imageUrl)),
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
        User user = Main.userService.getOrCreateUser(event.getUser().getId());
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
    }

    private static void handleGeneratedImageContainerFromHook(InteractionHook hook, Container container, Map<String, String> map,
                                                              boolean isEdit, CompletableFuture<Message> future) {
        User user = Main.userService.getOrCreateUser(hook.getInteraction().getUser().getId());
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

    /**
     * Initializes FFmpeg by downloading it if necessary
     */
    public static CompletableFuture<Void> initializeFFmpeg() {
        if (ffmpegInitialized) {
            return CompletableFuture.completedFuture(null);
        }

        return FFmpegDownloader.getFFmpegPath().thenCompose(path -> FFmpegDownloader.getFFmpegVersion().thenAccept(version -> {
            System.out.println("FFmpeg initialized: " + version);
            ffmpegInitialized = true;
        })).exceptionally(throwable -> {
            System.err.println("Failed to initialize FFmpeg: " + throwable.getMessage());
            return null;
        });
    }

    /**
     * Checks if FFmpeg is available and ready to use
     */
    public static boolean isFFmpegReady() {
        return ffmpegInitialized && FFmpegDownloader.isFFmpegAvailable();
    }
    
    /**
     * Checks if both FFmpeg and FFprobe are available for full video processing
     */
    public static CompletableFuture<Boolean> isVideoProcessingReady() {
        if (!isFFmpegReady()) {
            return CompletableFuture.completedFuture(false);
        }
        return FFmpegDownloader.isFFprobeAvailable();
    }

    /**
     * Gets video information for display in containers
     */
    public static CompletableFuture<String> getVideoInfoText(String videoUrl) {
        return isVideoProcessingReady().thenCompose(ready -> {
            if (!ready) {
                return CompletableFuture.completedFuture("Video processing not available");
            }

            return ffmpegService.getVideoInfo(videoUrl).thenApply(info -> String.format("📹 **%s** • ⏱️ **%s** • 🎬 **%s**",
                    info.getResolution(),
                    info.getFormattedDuration(),
                    info.getCodec())).exceptionally(throwable -> {
                System.err.println("Failed to get video info: " + throwable.getMessage());
                return "📹 Video information unavailable";
            });
        });
    }

    /**
     * Creates a video thumbnail for use in containers
     */
    public static CompletableFuture<byte[]> createVideoThumbnail(String videoUrl) {
        return isVideoProcessingReady().thenCompose(ready -> {
            if (!ready) {
                return CompletableFuture.completedFuture(null);
            }

            return ffmpegService.getVideoInfo(videoUrl).thenCompose(info -> {
                // Extract thumbnail from 25% into the video
                double thumbnailTime = info.getDuration() * 0.25;
                return ffmpegService.extractThumbnail(videoUrl, thumbnailTime);
            }).thenApply(thumbnail -> {
                try {
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    javax.imageio.ImageIO.write(thumbnail, "jpg", baos);
                    return baos.toByteArray();
                } catch (Exception e) {
                    System.err.println("Failed to convert thumbnail to bytes: " + e.getMessage());
                    return null;
                }
            }).exceptionally(throwable -> {
                System.err.println("Failed to create video thumbnail: " + throwable.getMessage());
                return null;
            });
        });
    }

    /**
     * Enhanced container creation that includes video information and GIF conversion
     */
    public static CompletableFuture<Container> createEnhancedVideoContainer(net.dv8tion.jda.api.entities.User user, 
                                                                            Map<String, String> map, 
                                                                            boolean shouldContinue) {
        String imageUrl = map.get("image");
        
        if (!isVideoUrl(imageUrl)) {
            // Not a video, use regular container creation
            return extractDominantColor(map).thenApply(color -> 
                createImageContainer(user, map, color, shouldContinue));
        }

        // Enhanced video container with additional info
        return getVideoInfoText(imageUrl).thenCombine(
            extractDominantColor(map), 
            (videoInfo, color) -> {
                String userAvatarUrl = user.getEffectiveAvatarUrl();
                String title = map.get("title");
                String description = map.get("description");
                
                Section headerSection = Section.of(
                        Thumbnail.fromUrl(userAvatarUrl),
                        TextDisplay.of("## " + title),
                        TextDisplay.of("**" + description + "**"),
                        TextDisplay.of(videoInfo), // Add video info
                        TextDisplay.of("*Generated by " + user.getName() + "*")
                );

                List<Button> buttons = createImageButtons(shouldContinue);

                return Container.of(
                        headerSection,
                        Separator.createDivider(Separator.Spacing.SMALL),
                        MediaGallery.of(MediaGalleryItem.fromUrl(imageUrl)),
                        Separator.createDivider(Separator.Spacing.SMALL),
                        ActionRow.of(buttons)
                ).withAccentColor(color);
            }
        );
    }
    
    /**
     * Creates a container with direct video URL (MP4/M4S) instead of GIF conversion
     */
    public static CompletableFuture<Message> sendVideoContainerWithGif(Interaction event, Map<String, String> map, boolean shouldContinue) {
        String imageUrl = map.get("image");
        
        if (!ffmpegService.shouldConvertToGif(imageUrl)) {
            // Use regular container if not a convertible video
            return sendImageContainer(event, map, shouldContinue);
        }
        
        // Try to get direct video URL instead of creating GIF
        return ffmpegService.resolveVideoUrl(imageUrl)
            .thenCompose(resolvedUrl -> {
                if (resolvedUrl.equals(imageUrl) || !isDirectVideoUrl(resolvedUrl)) {
                    // No resolution happened or not a direct video URL, fallback to regular container
                    return sendImageContainer(event, map, shouldContinue);
                }
                
                // Use the direct video URL in the container
                Map<String, String> updatedMap = new java.util.HashMap<>(map);
                updatedMap.put("image", resolvedUrl); // Use direct MP4/M4S URL
                updatedMap.put("original_video_url", imageUrl); // Keep original for reference
                
                System.out.println("Using direct video URL: " + resolvedUrl);
                
                return extractDominantColor(map).thenApply(color -> {
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
            .exceptionally(throwable -> {
                // If video resolution fails, fallback to regular container
                System.err.println("Video resolution failed, falling back to regular container: " + throwable.getMessage());
                try {
                    return sendImageContainer(event, map, shouldContinue).get();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to send fallback container", e);
                }
            });
    }
    
    /**
     * Edits loading message to show video container with direct video URL
     */
    public static CompletableFuture<Message> editLoadingToVideoContainerWithGif(InteractionHook hook, Map<String, String> map, boolean shouldContinue) {
        String imageUrl = map.get("image");
        
        if (!ffmpegService.shouldConvertToGif(imageUrl)) {
            // Use regular container if not a convertible video
            return editLoadingToImageContainer(hook, map, shouldContinue);
        }
        
        // Try to get direct video URL instead of creating GIF
        return ffmpegService.resolveVideoUrl(imageUrl)
            .thenCompose(resolvedUrl -> {
                if (resolvedUrl.equals(imageUrl) || !isDirectVideoUrl(resolvedUrl)) {
                    // No resolution happened or not a direct video URL, fallback to regular container
                    return editLoadingToImageContainer(hook, map, shouldContinue);
                }
                
                // Use the direct video URL in the container
                Map<String, String> updatedMap = new java.util.HashMap<>(map);
                updatedMap.put("image", resolvedUrl); // Use direct MP4/M4S URL
                updatedMap.put("original_video_url", imageUrl); // Keep original for reference
                
                System.out.println("Using direct video URL: " + resolvedUrl);
                
                return extractDominantColor(map).thenApply(color -> {
                    Container container = createImageContainer(hook.getInteraction().getUser(), updatedMap, color, shouldContinue);
                    
                    CompletableFuture<Message> future = new CompletableFuture<>();
                    
                    hook.editOriginalComponents(container)
                            .useComponentsV2()
                            .queue(future::complete, future::completeExceptionally);
                    
                    return future;
                }).thenCompose(f -> f);
            })
            .exceptionally(throwable -> {
                // If video resolution fails, fallback to regular container
                System.err.println("Video resolution failed, falling back to regular container: " + throwable.getMessage());
                
                try {
                    // Create a fallback container with just the video URL (no direct video)
                    Color color = extractDominantColor(map).get();
                    Container container = createImageContainer(hook.getInteraction().getUser(), map, color, shouldContinue);
                    
                    CompletableFuture<Message> future = new CompletableFuture<>();
                    
                    hook.editOriginalComponents(container)
                            .useComponentsV2()
                            .queue(future::complete, future::completeExceptionally);
                    
                    return future.get();
                } catch (Exception e) {
                    throw new RuntimeException("Failed to send fallback container", e);
                }
            });
    }

    /**
     * Checks if a URL is a direct video file that Discord can display
     */
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
     * Cleanup method for FFmpeg temporary files
     */
    public static void cleanup() {
        ffmpegService.cleanupTempFiles();
    }

    // ===== COLOR EXTRACTION =====

    private static CompletableFuture<Color> extractDominantColor(Map<String, String> map) {
        String imageUrl = map.get("image");
        if ("none".equals(imageUrl) || imageUrl.startsWith("attachment://")) {
            return CompletableFuture.completedFuture(Color.CYAN);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                if (isVideoUrl(imageUrl)) {
                    return extractColorFromVideo(imageUrl);
                } else if (isGifUrl(imageUrl)) {
                    return extractColorFromGif(imageUrl);
                } else {
                    return extractColorFromImage(imageUrl);
                }
            } catch (Exception e) {
                System.err.println("Failed to extract color from: " + imageUrl + " - " + e.getMessage());
                return Color.CYAN;
            }
        });
    }

    private static boolean isVideoUrl(String url) {
        return ffmpegService.isVideoUrl(url);
    }

    private static boolean isGifUrl(String url) {
        return url.matches(".*\\.gif$");
    }

    private static Color extractColorFromVideo(String url) {
        // Try to get a preview image first (faster)
        String previewUrl = ffmpegService.getVideoPreviewUrl(url);
        if (previewUrl != null) {
            try {
                return extractColorFromImage(previewUrl);
            } catch (Exception e) {
                System.err.println("Failed to extract color from preview, falling back to FFmpeg: " + e.getMessage());
            }
        }
        
        // Fallback to FFmpeg extraction
        try {
            return ffmpegService.extractDominantColor(url).get();
        } catch (Exception e) {
            System.err.println("FFmpeg color extraction failed: " + e.getMessage());
            return Color.CYAN;
        }
    }

    private static Color extractColorFromGif(String url) throws Exception {
        return extractColorFromImage(url);
    }

    private static Color extractColorFromImage(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();

        HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch image: " + response.statusCode());
        }

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
        if (image == null) {
            throw new IOException("Could not decode image");
        }

        return getDominantColor(image);
    }

    private static Color getDominantColor(BufferedImage image) {
        int width = Math.min(image.getWidth(), 100);
        int height = Math.min(image.getHeight(), 100);

        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(image, 0, 0, width, height, null);
        g2d.dispose();

        long redSum = 0, greenSum = 0, blueSum = 0;
        int pixelCount = 0;

        for (int x = 0; x < width; x += 2) {
            for (int y = 0; y < height; y += 2) {
                int rgb = scaled.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                int brightness = (r + g + b) / 3;
                if (brightness > 30 && brightness < 225) {
                    redSum += r;
                    greenSum += g;
                    blueSum += b;
                    pixelCount++;
                }
            }
        }

        if (pixelCount == 0) {
            return Color.CYAN;
        }

        int avgRed = (int) (redSum / pixelCount);
        int avgGreen = (int) (greenSum / pixelCount);
        int avgBlue = (int) (blueSum / pixelCount);

        return enhanceSaturation(new Color(avgRed, avgGreen, avgBlue));
    }

    private static Color enhanceSaturation(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        float saturation = Math.min(1.0f, hsb[1] * 1.3f);
        float brightness = Math.min(1.0f, hsb[2] * 1.1f);
        return Color.getHSBColor(hsb[0], saturation, brightness);
    }
}