package me.hash.mediaroulette.bot.commands.images;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.commands.BaseCommand;
import me.hash.mediaroulette.bot.utils.CommandCooldown;
import me.hash.mediaroulette.bot.MediaContainerManager;
import me.hash.mediaroulette.bot.utils.ErrorHandler;
import me.hash.mediaroulette.model.MessageData;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.plugins.images.ImageSourceRegistry;
import me.hash.mediaroulette.service.ImageInteractionService;
import me.hash.mediaroulette.service.ImageRequestService;
import me.hash.mediaroulette.locale.LocaleManager;
import me.hash.mediaroulette.utils.MaintenanceChecker;
import me.hash.mediaroulette.service.QuestGenerator;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.Map;

public class getRandomImage extends BaseCommand {

    private final ImageRequestService requestService;
    private final ImageInteractionService interactionService;

    public getRandomImage() {
        this.requestService = ImageRequestService.getInstance();
        this.interactionService = ImageInteractionService.getInstance();
    }

    @Override
    public CommandData getCommandData() {
        var command = Commands.slash("random", "Sends a random image");

        command.addSubcommands(new SubcommandData("all", "Sends images from all sources")
                .addOption(OptionType.BOOLEAN, "shouldcontinue", "Should the image keep generating?"));

        ImageSourceRegistry.getInstance().getProvidersByPriority().forEach(provider -> {
            String name = provider.getName().toLowerCase();
            String desc = provider.getDescription();
            if (desc.length() > 100) desc = desc.substring(0, 97) + "...";

            SubcommandData sub = new SubcommandData(name, desc)
                    .addOption(OptionType.BOOLEAN, "shouldcontinue", "Should the image keep generating?");

            if (provider.supportsSearch()) {
                sub.addOption(OptionType.STRING, "query", "Search query for " + provider.getDisplayName(), false, true);
            }
            command.addSubcommands(sub);
        });

        return command.setIntegrationTypes(IntegrationType.ALL).setContexts(InteractionContextType.ALL);
    }

    @Override
    @CommandCooldown(value = 3, commands = {"random"})
    public void handleCommand(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("random")) return;

        if (MaintenanceChecker.isMaintenanceBlocked(event)) {
            MaintenanceChecker.sendMaintenanceMessage(event);
            return;
        }

        event.deferReply().queue();

        String userId = event.getUser().getId();
        String subcommand = event.getSubcommandName();
        String query = event.getOption("query") != null ? event.getOption("query").getAsString() : null;

        Main.getUserService().trackCommandUsage(userId, "random");
        requestService.trackSourceUsage(userId, subcommand, query);

        User user = Main.getUserService().getOrCreateUser(userId);

        if (!requestService.validateChannelAccess(event, user)) return;

        Main.getUserService().updateUser(user);

        event.getHook().sendMessageComponents(interactionService.createLoadingContainer(event.getUser().getEffectiveAvatarUrl()))
                .useComponentsV2()
                .queue(msg -> Main.getBot().getExecutor().execute(() -> processImageRequest(event, user, subcommand, query, event.getHook())),
                       err -> ErrorHandler.handleException(event, "Error", "Failed to send loading message", err));
    }

    private void processImageRequest(SlashCommandInteractionEvent event, User user, String subcommand, String query, InteractionHook hook) {
        LocaleManager locale = LocaleManager.getInstance(user.getLocale());
        boolean shouldContinue = event.getOption("shouldcontinue") != null && event.getOption("shouldcontinue").getAsBoolean();

        try {
            requestService.fetchImage(subcommand, event, query)
                    .thenAccept(image -> {
                        if (image == null || image.get("image") == null) {
                            ErrorHandler.editToErrorContainer(event, locale.get("error.no_images_title"), locale.get("error.no_images_description"));
                            return;
                        }

                        requestService.trackStats(user.getUserId(), subcommand, user);

                        MediaContainerManager.editLoadingToImageContainer(hook, image, shouldContinue)
                                .thenAccept(msg -> {
                                    interactionService.registerMessage(msg.getIdLong(),
                                            new MessageData(msg.getIdLong(), subcommand, query, shouldContinue, event.getUser().getIdLong(), event.getChannel().getIdLong()));
                                    QuestGenerator.onImageGenerated(user, subcommand);
                                    Main.getUserService().updateUser(user);
                                })
                                .exceptionally(ex -> {
                                    ErrorHandler.editToErrorContainer(event, locale.get("error.unexpected_error"), locale.get("error.failed_to_send_image"));
                                    return null;
                                });
                    })
                    .exceptionally(e -> {
                        ErrorHandler.editToErrorContainer(event, locale.get("error.generic_title"), e.getMessage());
                        return null;
                    });

            user.incrementImagesGenerated();
            Main.getUserService().updateUser(user);
        } catch (Exception e) {
            ErrorHandler.editToErrorContainer(event, locale.get("error.unexpected_error"), e.getMessage());
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        Main.getBot().getExecutor().execute(() -> interactionService.handleButtonInteraction(event));
    }
}
