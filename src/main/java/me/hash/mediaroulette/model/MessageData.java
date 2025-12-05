package me.hash.mediaroulette.model;

import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.Main;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.replacer.ComponentReplacer;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;

import java.time.Instant;

public class MessageData {
    private final long messageId;
    private final String subcommand, query;
    private final boolean shouldContinue;
    private final long userId;
    private final long channelId;
    private long lastInteractionTime;

    public MessageData(long messageId, String subcommand, String query, boolean shouldContinue, long userId, long channelId) {
        this.messageId = messageId;
        this.subcommand = subcommand;
        this.query = query;
        this.shouldContinue = shouldContinue;
        this.userId = userId;
        this.channelId = channelId;
        this.lastInteractionTime = Instant.now().toEpochMilli();
    }

    public String getQuery() {
        return query;
    }

    public String getSubcommand() {
        return subcommand;
    }

    public boolean isShouldContinue() {
        return shouldContinue;
    }

    public boolean isUserAllowed(long userId) {
        return this.userId == userId;
    }

    public long getLastInteractionTime() {
        return lastInteractionTime;
    }

    public void updateLastInteractionTime() {
        this.lastInteractionTime = Instant.now().toEpochMilli();
    }

    public void disableButtons() {
        if (Main.getBot() == null || Main.getBot().getShardManager() == null) return;
        
        var channel = Main.getBot().getShardManager().getTextChannelById(channelId);
        if (channel == null) return;

        channel.retrieveMessageById(messageId).queue(message -> {
            MessageComponentTree components = message.getComponentTree();
            if (components.getComponents().isEmpty()) return;
            ComponentReplacer replacer = ComponentReplacer.of(Button.class, button -> true, Button::asDisabled);
            MessageComponentTree updated = components.replace(replacer);
            message.editMessageComponents(updated).useComponentsV2().queue(
                success -> {}, 
                failure -> {} 
            );
        }, failure -> {}); 
    }
}
