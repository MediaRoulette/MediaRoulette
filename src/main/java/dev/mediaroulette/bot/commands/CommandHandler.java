package dev.mediaroulette.bot.commands;

import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

public interface CommandHandler extends EventListener {
    CommandData getCommandData();
}
