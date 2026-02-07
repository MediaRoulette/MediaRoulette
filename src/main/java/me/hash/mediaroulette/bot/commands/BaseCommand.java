package me.hash.mediaroulette.bot.commands;

import me.hash.mediaroulette.utils.MaintenanceChecker;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public abstract class BaseCommand extends ListenerAdapter implements CommandHandler {

    @Override
    public final void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.isAcknowledged()) {
            return;
        }

        if (!MaintenanceChecker.isExemptCommand(event.getName()) 
                && MaintenanceChecker.isMaintenanceBlocked(event)) {
            MaintenanceChecker.sendMaintenanceMessage(event);
            return;
        }

        handleCommand(event);
    }

    protected abstract void handleCommand(SlashCommandInteractionEvent event);
}