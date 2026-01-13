package me.hash.mediaroulette.bot.utils;

import io.github.cdimascio.dotenv.DotenvEntry;
import me.hash.mediaroulette.Main;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectInteraction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.PrintWriter;
import java.io.StringWriter;

public class ErrorHandler {
    private static final Logger logger = LoggerFactory.getLogger(ErrorHandler.class);

    public static Container createErrorContainer(String avatarUrl, String title, String description) {
        String safeTitle = (title != null && !title.isEmpty()) ? title : "Error";
        String safeDesc = (description != null && !description.isEmpty()) ? description : "An unexpected error occurred.";

        return Container.of(
                Section.of(
                        Thumbnail.fromUrl(avatarUrl),
                        TextDisplay.of("## ❌ " + safeTitle),
                        TextDisplay.of("**" + safeDesc + "**"),
                        TextDisplay.of("*Please try again later*")
                )
        ).withAccentColor(new Color(255, 107, 107));
    }

    public static void editToErrorContainer(Interaction event, String title, String description) {
        String avatarUrl = getAvatarUrl(event);
        Container errorContainer = createErrorContainer(avatarUrl, title, description);

        switch (event) {
            case SlashCommandInteractionEvent e -> e.getHook()
                    .editOriginalComponents(errorContainer)
                    .useComponentsV2()
                    .queue(s -> {}, f -> {});
            case ButtonInteractionEvent e -> e.getHook()
                    .editOriginalComponents(errorContainer)
                    .useComponentsV2()
                    .queue(s -> {}, f -> {});
            case StringSelectInteraction e -> e.getHook()
                    .editOriginalComponents(errorContainer)
                    .useComponentsV2()
                    .queue(s -> {}, f -> {});
            case null, default -> logger.error("Unsupported interaction type for error editing.");
        }
    }

    public static void sendErrorEmbed(Interaction event, String title, String description) {
        editToErrorContainer(event, title, description);
    }

    public static void editErrorEmbed(Interaction event, String title, String description) {
        editToErrorContainer(event, title, description);
    }

    public static void handleException(Interaction event, String title, String message, Throwable ex) {
        String censoredTrace = getCensoredStackTrace(ex);
        logger.error("Exception occurred: {}", censoredTrace);
        editToErrorContainer(event, title, buildErrorDescription(message, ex));
    }

    private static String buildErrorDescription(String userMessage, Throwable throwable) {
        String details = throwable.getMessage() != null ? throwable.getMessage() : "No additional details.";
        return userMessage + "\n\nDetails: " + details;
    }

    private static String getAvatarUrl(Interaction event) {
        try {
            if (event != null && event.getUser() != null) {
                return event.getUser().getEffectiveAvatarUrl();
            }
        } catch (Exception ignored) {}
        return "https://cdn.discordapp.com/embed/avatars/0.png";
    }

    private static String getCensoredStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        String trace = sw.toString();

        for (DotenvEntry entry : Main.getEnvironment().entries()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key != null && !key.isEmpty()) {
                trace = trace.replace(key, "[ENV_KEY_***]");
            }
            if (value != null && value.length() > 3) {
                trace = trace.replace(value, "[ENV_VALUE_***]");
            }
        }
        return trace;
    }
}