package me.hash.mediaroulette.bot.commands.images;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.MediaContainerManager;
import me.hash.mediaroulette.bot.commands.BaseCommand;
import me.hash.mediaroulette.bot.utils.CommandCooldown;
import me.hash.mediaroulette.bot.utils.ErrorHandler;
import me.hash.mediaroulette.model.Favorite;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.locale.LocaleManager;
import me.hash.mediaroulette.service.ImageRequestService;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;

import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.InteractionHook;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class FavoritesCommand extends BaseCommand {
    private static final int ITEMS_PER_PAGE = 25;

    @Override
    public CommandData getCommandData() {
        return Commands.slash("favorites", "Shows your favorites")
                .setIntegrationTypes(IntegrationType.ALL)
                .setContexts(InteractionContextType.ALL);
    }

    @Override
    @CommandCooldown(value = 3, commands = {"favorites"})
    public void handleCommand(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("favorites"))
            return;

        event.deferReply().queue();

        User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
        LocaleManager locale = LocaleManager.getInstance(user.getLocale());

        if (!ImageRequestService.getInstance().validateChannelAccess(event, user, null))
            return;

        if (user.getFavorites() == null || user.getFavorites().isEmpty()) {
            event.getHook().sendMessage(locale.get("warn.no_favorites_yet")).queue();
            return;
        }

        sendFavoriteDetail(event.getHook(), user, 0, true);
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("favorite:"))
            return;

        User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
        LocaleManager locale = LocaleManager.getInstance(user.getLocale());

        String originalUserId = event.getMessage().getInteractionMetadata().getUser().getId();
        if (!event.getUser().getId().equals(originalUserId)) {
            event.reply(locale.get("error.not_your_menu")).setEphemeral(true).queue();
            return;
        }

        String[] parts = event.getComponentId().split(":");
        String action = parts[1];

        event.deferEdit().queue();

        List<Favorite> favorites = user.getFavorites();

        switch (action) {
            case "prev":
            case "next":
            case "refresh": {
                int pageVal = Integer.parseInt(parts[2]);
                int targetPage = Math.max(1, pageVal);
                int startIndex = (targetPage - 1) * ITEMS_PER_PAGE;
                sendFavoriteDetail(event.getHook(), user, startIndex, false);
                break;
            }
            case "delete": {
                int index = Integer.parseInt(parts[2]);
                if (index < 0 || index >= favorites.size()) {
                    event.getHook().sendMessage(locale.get("error.invalid_favorite_delete")).setEphemeral(true).queue();
                    return;
                }
                user.removeFavorite(index);
                Main.getUserService().updateUser(user);

                event.getHook().sendMessage(locale.get("success.favorite_deleted")).setEphemeral(true).queue();

                favorites = user.getFavorites();

                if (!favorites.isEmpty()) {
                    int newIndex = Math.min(index, favorites.size() - 1);
                    sendFavoriteDetail(event.getHook(), user, newIndex, false);
                } else {
                    Container empty = Container.of(
                            Section.of(
                                    Thumbnail.fromUrl(event.getUser().getEffectiveAvatarUrl()),
                                    TextDisplay.of(locale.get("warn.no_favorites_title")),
                                    TextDisplay.of(locale.get("warn.no_favorites_description"))
                            )
                    ).withAccentColor(Color.RED);
                    event.getHook().editOriginalComponents(empty)
                            .useComponentsV2()
                            .queue();
                }
                break;
            }
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals("favorite-select-menu"))
            return;

        User user = Main.getUserService().getOrCreateUser(event.getUser().getId());
        LocaleManager locale = LocaleManager.getInstance(user.getLocale());

        String originalUserId = event.getMessage().getInteractionMetadata().getUser().getId();
        if (!event.getUser().getId().equals(originalUserId)) {
            event.reply(locale.get("error.not_your_menu")).setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        int selectedIndex = Integer.parseInt(event.getValues().getFirst());

        sendFavoriteDetail(event.getHook(), user, selectedIndex, false);
    }

    // HTTP client for URL validation with short timeouts
    private static final OkHttpClient urlCheckClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .followRedirects(true)
            .build();

    /**
     * Validates if a URL is still accessible by making a HEAD request.
     * @return true if the URL is accessible (returns 2xx status), false otherwise
     */
    private boolean isUrlAccessible(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .head()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build();
            
            try (Response response = urlCheckClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            // network error, timeout, or invalid URL; consider it inaccessible
            return false;
        }
    }

    private void sendFavoriteDetail(InteractionHook hook, User user, int index, boolean isNewMessage) {
        List<Favorite> favorites = user.getFavorites();
        LocaleManager locale = LocaleManager.getInstance(user.getLocale());

        if (index < 0 || index >= favorites.size()) {
            hook.sendMessage(locale.get("error.invalid_favorite_selected")).setEphemeral(true).queue();
            return;
        }

        Favorite favorite = favorites.get(index);
        String avatarUrl = hook.getInteraction().getUser().getEffectiveAvatarUrl();
        String imageUrl = favorite.getImage();
        
        // Validate URL accessibility for external URLs
        boolean hasDisplayableImage = imageUrl != null && !imageUrl.isBlank() && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"));
        
        if (hasDisplayableImage && !isUrlAccessible(imageUrl)) {
            // Media source was deleted - remove the favorite and notify user
            user.removeFavorite(index);
            Main.getUserService().updateUser(user);
            
            hook.sendMessage(locale.get("warn.favorite_source_deleted")).setEphemeral(true).queue();
            
            // Refresh the container with remaining favorites
            favorites = user.getFavorites();
            if (!favorites.isEmpty()) {
                int newIndex = Math.min(index, favorites.size() - 1);
                sendFavoriteDetail(hook, user, newIndex, false);
            } else {
                // No favorites left - show empty state
                Container empty = Container.of(
                        Section.of(
                                Thumbnail.fromUrl(avatarUrl),
                                TextDisplay.of(locale.get("warn.no_favorites_title")),
                                TextDisplay.of(locale.get("warn.no_favorites_description"))
                        )
                ).withAccentColor(Color.RED);
                hook.editOriginalComponents(empty)
                        .useComponentsV2()
                        .queue();
            }
            return;
        }

        String title = favorite.getTitle() != null ? favorite.getTitle() : locale.get("favorites.title");
        Section headerSection = Section.of(
                Thumbnail.fromUrl(avatarUrl),
                TextDisplay.of("## " + title),
                TextDisplay.of("**" + favorite.getDescription() + "**"),
                TextDisplay.of("*" + locale.get("favorites.count", index + 1, favorites.size()) + "*")
        );

        int currentPage = index / ITEMS_PER_PAGE;

        List<ActionRow> paginatorComponents = buildPaginatorComponents(user, currentPage);

        Button deleteButton = Button.danger("favorite:delete:" + index, locale.get("ui.delete"))
                .withEmoji(Emoji.fromUnicode("❌"));
        ActionRow deleteButtonRow = ActionRow.of(deleteButton);

        Color accent = favorite.getAccentColor() != null ? new Color(favorite.getAccentColor()) : Color.CYAN;
        Container container = hasDisplayableImage
                ? Container.of(
                    headerSection,
                    Separator.createDivider(Separator.Spacing.SMALL),
                    MediaGallery.of(createSafeMediaGalleryItem(imageUrl)),
                    Separator.createDivider(Separator.Spacing.SMALL),
                    paginatorComponents.get(0),
                    paginatorComponents.size() > 1 ? paginatorComponents.get(1) : ActionRow.of(Button.secondary("favorite:none", " ").asDisabled()),
                    deleteButtonRow
                ).withAccentColor(accent)
                : Container.of(
                    headerSection,
                    Separator.createDivider(Separator.Spacing.SMALL),
                    paginatorComponents.get(0),
                    paginatorComponents.size() > 1 ? paginatorComponents.get(1) : ActionRow.of(Button.secondary("favorite:none", " ").asDisabled()),
                    deleteButtonRow
                ).withAccentColor(accent);

        if (isNewMessage) {
            hook.sendMessageComponents(container)
                    .useComponentsV2()
                    .queue();
        } else {
            hook.editOriginalComponents(container)
                    .useComponentsV2()
                    .queue();
        }
    }

    private List<ActionRow> buildPaginatorComponents(User user, int page) {
        List<Favorite> favorites = user.getFavorites();
        LocaleManager locale = LocaleManager.getInstance(user.getLocale());

        int totalPages = (int) Math.ceil((double) favorites.size() / ITEMS_PER_PAGE);
        page = Math.max(0, Math.min(page, totalPages - 1));

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, favorites.size());

        List<Favorite> favoritesOnPage = favorites.subList(start, end);

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("favorite-select-menu")
                .setPlaceholder(locale.get("info.select_favorite_to_view"))
                .setMinValues(1)
                .setMaxValues(1);

        int index = start;
        for (Favorite favorite : favoritesOnPage) {
            String description = favorite.getDescription();
            description = description.replaceAll("\\n", " ");
            description = (description.length() > 80) ? description.substring(0, 80) + "..." : description;

            String label = (index + 1) + ". " + description;
            if (label.length() > 100) {
                label = label.substring(0, 97) + "...";
            }
            String value = String.valueOf(index);

            menuBuilder.addOption(label, value);
            index++;
        }

        List<ActionRow> actionRows = new ArrayList<>();
        actionRows.add(ActionRow.of(menuBuilder.build()));

        int displayPage = page + 1;
        ActionRow pagination = MediaContainerManager.createPaginationButtons("favorite", displayPage, Math.max(1, totalPages), 
                null, locale.get("ui.previous"), locale.get("ui.next"), locale.get("ui.refresh"));
        actionRows.add(pagination);

        return actionRows;
    }
    
    private MediaGalleryItem createSafeMediaGalleryItem(String url) {
        if (url == null || url.isBlank()) {
            return MediaGalleryItem.fromUrl("https://via.placeholder.com/400x300.png");
        }
        
        String filename = extractFilenameFromUrl(url);
        
        if (filename != null && !filename.isBlank() && filename.contains(".")) {
            return MediaGalleryItem.fromUrl(url);
        }
        
        String extension = detectMediaExtension(url);
        String safeUrl = url.contains("?") 
            ? url + "&_fn=media." + extension 
            : url + "?_fn=media." + extension;
        
        return MediaGalleryItem.fromUrl(safeUrl);
    }
    
    private String extractFilenameFromUrl(String url) {
        try {
            int queryIndex = url.indexOf('?');
            int fragmentIndex = url.indexOf('#');
            int endIndex = url.length();
            
            if (queryIndex != -1) endIndex = Math.min(endIndex, queryIndex);
            if (fragmentIndex != -1) endIndex = Math.min(endIndex, fragmentIndex);
            
            String urlPath = url.substring(0, endIndex);
            int lastSlashIndex = urlPath.lastIndexOf('/');
            
            if (lastSlashIndex != -1 && lastSlashIndex < urlPath.length() - 1) {
                String filename = urlPath.substring(lastSlashIndex + 1);
                if (filename.contains(".") && filename.length() > 1) {
                    return filename;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
    
    private String detectMediaExtension(String url) {
        if (url == null) return "png";
        String lowerUrl = url.toLowerCase();
        
        if (lowerUrl.contains(".mp4") || lowerUrl.contains("video")) return "mp4";
        if (lowerUrl.contains(".webm")) return "webm";
        if (lowerUrl.contains(".gif")) return "gif";
        if (lowerUrl.contains(".webp")) return "webp";
        if (lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg")) return "jpg";
        if (lowerUrl.contains(".png")) return "png";
        if (lowerUrl.contains("redgifs") || lowerUrl.contains("gfycat")) return "mp4";
        
        return "png";
    }
}
