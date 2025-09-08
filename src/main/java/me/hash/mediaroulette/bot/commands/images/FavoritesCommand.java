package me.hash.mediaroulette.bot.commands.images;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.bot.MediaContainerManager;
import me.hash.mediaroulette.bot.errorHandler;
import me.hash.mediaroulette.bot.commands.CommandHandler;
import me.hash.mediaroulette.model.Favorite;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.utils.LocaleManager;
import me.hash.mediaroulette.utils.MaintenanceChecker;
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

import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.InteractionHook;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class FavoritesCommand extends ListenerAdapter implements CommandHandler {
    private static final int ITEMS_PER_PAGE = 25;

    @Override
    public CommandData getCommandData() {
        return Commands.slash("favorites", "Shows your favorites")
                .setIntegrationTypes(IntegrationType.ALL)
                .setContexts(InteractionContextType.ALL);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("favorites"))
            return;

        if (MaintenanceChecker.isMaintenanceBlocked(event)) {
            MaintenanceChecker.sendMaintenanceMessage(event);
            return;
        }

        event.deferReply().queue();

        long now = System.currentTimeMillis();
        long userId = event.getUser().getIdLong();
        User user = Main.userService.getOrCreateUser(event.getUser().getId());

        if (Bot.COOLDOWNS.containsKey(userId) && now - Bot.COOLDOWNS.get(userId) < Bot.COOLDOWN_DURATION) {
            errorHandler.sendErrorEmbed(event, "Slow down!", "Please wait for 2 seconds before using this command again.");
            return;
        }

        Bot.COOLDOWNS.put(userId, now);

        if (!validateChannelAccess(event, user))
            return;

        if (user.getFavorites() == null || user.getFavorites().isEmpty()) {
            event.getHook().sendMessage("You do not have any favorites yet!").queue();
            return;
        }

        // Show the first favorite by default
        sendFavoriteDetail(event.getHook(), user, 0, true);
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("favorite:"))
            return;

        User user = Main.userService.getOrCreateUser(event.getUser().getId());
        LocaleManager localeManager = LocaleManager.getInstance(user.getLocale()); // Get once, use multiple times

        // Check if the user is the same as the one who initiated the interaction
        String originalUserId = event.getMessage().getInteractionMetadata().getUser().getId();
        if (!event.getUser().getId().equals(originalUserId)) {
            event.reply(localeManager.get("error.not_your_menu")).setEphemeral(true).queue();
            return;
        }

        // Split the button ID
        String[] parts = event.getComponentId().split(":");
        String action = parts[1];

        event.deferEdit().queue();

        List<Favorite> favorites = user.getFavorites();

        switch (action) {
            case "prev":
            case "next":
            case "refresh": {
                int pageVal = Integer.parseInt(parts[2]); // 1-based page value from container pagination
                int targetPage = Math.max(1, pageVal);
                int startIndex = (targetPage - 1) * ITEMS_PER_PAGE;
                sendFavoriteDetail(event.getHook(), user, startIndex, false);
                break;
            }
            case "delete": {
                int index = Integer.parseInt(parts[2]);
                if (index < 0 || index >= favorites.size()) {
                    event.getHook().sendMessage(localeManager.get("error.invalid_favorite_delete")).setEphemeral(true).queue();
                    return;
                }
                user.removeFavorite(index);
                Main.userService.updateUser(user);

                event.getHook().sendMessage(localeManager.get("success.favorite_deleted")).setEphemeral(true).queue();

                favorites = user.getFavorites();

                if (!favorites.isEmpty()) {
                    int newIndex = Math.min(index, favorites.size() - 1);
                    sendFavoriteDetail(event.getHook(), user, newIndex, false);
                } else {
                    Container empty = Container.of(
                            Section.of(
                                    Thumbnail.fromUrl(event.getUser().getEffectiveAvatarUrl()),
                                    TextDisplay.of(localeManager.get("warn.no_favorites_title")),
                                    TextDisplay.of(localeManager.get("warn.no_favorites_description"))
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
        User user = Main.userService.getOrCreateUser(event.getUser().getId());
        LocaleManager localeManager = LocaleManager.getInstance(user.getLocale()); // Get once, use multiple times

        String originalUserId = event.getMessage().getInteractionMetadata().getUser().getId();
        if (!event.getUser().getId().equals(originalUserId)) {
            event.reply(localeManager.get("error.not_your_menu")).setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        int selectedIndex = Integer.parseInt(event.getValues().getFirst());

        sendFavoriteDetail(event.getHook(), user, selectedIndex, false);
    }

    private void sendFavoriteDetail(InteractionHook hook, User user, int index, boolean isNewMessage) {
        List<Favorite> favorites = user.getFavorites();
        LocaleManager localeManager = LocaleManager.getInstance(user.getLocale()); // Get once, use multiple times

        if (index < 0 || index >= favorites.size()) {
            hook.sendMessage(localeManager.get("error.invalid_favorite_selected")).setEphemeral(true).queue();
            return;
        }

        Favorite favorite = favorites.get(index);
        String avatarUrl = hook.getInteraction().getUser().getEffectiveAvatarUrl();

        Section headerSection = Section.of(
                Thumbnail.fromUrl(avatarUrl),
                TextDisplay.of("## " + (favorite.getTitle() != null ? favorite.getTitle() : "⭐ Favorite")),
                TextDisplay.of("**" + favorite.getDescription() + "**"),
                TextDisplay.of("*Favorite " + (index + 1) + "/" + favorites.size() + "*")
        );

        int currentPage = index / ITEMS_PER_PAGE;

        List<ActionRow> paginatorComponents = buildPaginatorComponents(user, currentPage);

        Button deleteButton = Button.danger("favorite:delete:" + index, "Delete")
                .withEmoji(Emoji.fromUnicode("❌"));
        ActionRow deleteButtonRow = ActionRow.of(deleteButton);

        String imageUrl = favorite.getImage();
        boolean hasDisplayableImage = imageUrl != null && !imageUrl.isBlank() && (imageUrl.startsWith("http://") || imageUrl.startsWith("https://"));

        Color accent = favorite.getAccentColor() != null ? new Color(favorite.getAccentColor()) : Color.CYAN;
        Container container = hasDisplayableImage
                ? Container.of(
                    headerSection,
                    Separator.createDivider(Separator.Spacing.SMALL),
                    MediaGallery.of(MediaGalleryItem.fromUrl(imageUrl)),
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
        LocaleManager localeManager = LocaleManager.getInstance(user.getLocale()); // Get once, use multiple times

        int totalPages = (int) Math.ceil((double) favorites.size() / ITEMS_PER_PAGE);
        page = Math.max(0, Math.min(page, totalPages - 1)); // Ensure page is within bounds

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, favorites.size());

        List<Favorite> favoritesOnPage = favorites.subList(start, end);

        // Build the selection menu with options from the current page
        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("favorite-select-menu")
                .setPlaceholder(localeManager.get("info.select_favorite_to_view"))
                .setMinValues(1)
                .setMaxValues(1);

        int index = start; // Zero-based index
        for (Favorite favorite : favoritesOnPage) {
            String description = favorite.getDescription();
            description = description.replaceAll("\\n", " ");
            description = (description.length() > 80) ? description.substring(0, 80) + "..." : description;

            String label = (index + 1) + ". " + description;
            if (label.length() > 100) {
                label = label.substring(0, 97) + "...";
            }
            String value = String.valueOf(index); // Use zero-based index for value

            menuBuilder.addOption(label, value);
            index++;
        }

        // Assemble the selection menu into an ActionRow and add pagination buttons
        List<ActionRow> actionRows = new ArrayList<>();
        actionRows.add(ActionRow.of(menuBuilder.build()));

        int displayPage = page + 1; // convert to 1-based page index for pagination component
        ActionRow pagination = MediaContainerManager.createPaginationButtons("favorite", displayPage, Math.max(1, totalPages), null);
        actionRows.add(pagination);

        return actionRows;
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
}
