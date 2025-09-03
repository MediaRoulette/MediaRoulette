package me.hash.mediaroulette.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.cdimascio.dotenv.DotenvEntry;
import me.hash.mediaroulette.Main;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectInteraction;

import java.awt.Color;
import java.io.PrintWriter;
import java.io.StringWriter;

public class errorHandler {
    private static final Logger logger = LoggerFactory.getLogger(errorHandler.class);

    /**
     * Sends an error embed when an exception occurs.
     *
     * @param event       The interaction event (e.g., slash command or button interaction).
     * @param title       The title of the embed.
     * @param description The details about the error.
     */
    public static void sendErrorEmbed(Interaction event, String title, String description) {
        EmbedBuilder errorEmbed = new EmbedBuilder()
                .setTitle(title != null && !title.isEmpty() ? title : "Error")
                .setDescription(description != null ? description : "An unexpected error occurred.")
                .setColor(Color.RED);

        switch (event) {
            case SlashCommandInteractionEvent slashCommandInteractionEvent -> slashCommandInteractionEvent.getHook()
                    .sendMessageEmbeds(errorEmbed.build())
                    .setEphemeral(true)
                    .queue();
            case ButtonInteractionEvent buttonInteractionEvent -> buttonInteractionEvent.getHook()
                    .sendMessageEmbeds(errorEmbed.build())
                    .setEphemeral(true)
                    .queue();
            case StringSelectInteraction stringSelectInteraction -> stringSelectInteraction.getHook()
                    .sendMessageEmbeds(errorEmbed.build())
                    .setEphemeral(true)
                    .queue();
            case null, default -> logger.error("Unsupported interaction type for error embedding.");
        }
    }

    /**
     * Handles exceptions globally by logging the throwable and sending an error embed to the user. It also
     * ensures that sensitive information like environment keys or values is censored from the stack trace.
     *
     * @param event   The interaction where the throwable occurred.
     * @param title   The title of the error embed.
     * @param message A custom user-friendly error message.
     * @param ex      The exception or throwable that was thrown.
     */
    public static void handleException(Interaction event, String title, String message, Throwable ex) {
        // Censor the stack trace to prevent sensitive information exposure.
        String censoredStackTrace = getCensoredStackTrace(ex);

        // Log the censored stack trace for debugging.
        logger.error("Exception occurred: {}", censoredStackTrace);

        // Build the error message and send it to the user via embed.
        sendErrorEmbed(event, title, buildErrorDescription(message, ex));
    }

    /**
     * Constructs a detailed error message for embedding, combining a user-friendly message
     * with the exception's details.
     *
     * @param userMessage User-friendly error message.
     * @param throwable   The throwable/exception that occurred.
     * @return A detailed error message as a string.
     */
    private static String buildErrorDescription(String userMessage, Throwable throwable) {
        String throwableMessage = throwable.getMessage() != null ? throwable.getMessage() : "No additional details.";
        return userMessage + "\n\nDetails: " + throwableMessage;
    }

    /**
     * Censors sensitive data from a throwable's stack trace using the application's
     * environment variables. Replaces environment variable keys and values with masked versions.
     *
     * @param throwable The throwable whose stack trace needs to be censored.
     * @return A string containing the censored stack trace.
     */
    private static String getCensoredStackTrace(Throwable throwable) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        // Write the stack trace to a StringWriter.
        throwable.printStackTrace(printWriter);
        String stackTrace = stringWriter.toString();

        // Replace sensitive environment data in the stack trace
        for (DotenvEntry entry : Main.env.entries()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key != null && !key.isEmpty()) {
                // Replace environment variable keys
                stackTrace = stackTrace.replace(key, "[ENV_KEY_***]");
            }

            if (value != null && value.length() > 3) {
                // Replace environment variable values (only if they're longer than 3 chars to avoid false positives)
                stackTrace = stackTrace.replace(value, "[ENV_VALUE_" + value.substring(0, 3) + "***]");
            }
        }

        return stackTrace;
    }
}