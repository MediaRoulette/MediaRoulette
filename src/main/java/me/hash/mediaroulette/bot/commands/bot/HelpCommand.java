package me.hash.mediaroulette.bot.commands.bot;

import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.bot.commands.CommandHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.Instant;

public class HelpCommand extends ListenerAdapter implements CommandHandler {
    private static final Color PRIMARY_COLOR = new Color(88, 101, 242);

    @Override
    public CommandData getCommandData() {
        return Commands.slash("help", "❓ Get info about the bot usage!")
                .setIntegrationTypes(IntegrationType.ALL)
                .setContexts(InteractionContextType.ALL);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("help")) return;

    }
}