package me.hash.mediaroulette.bot.commands.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.bot.commands.CommandHandler;
import me.hash.mediaroulette.model.BotInventoryItem;
import me.hash.mediaroulette.model.Giveaway;
import me.hash.mediaroulette.model.User;
import me.hash.mediaroulette.service.BotInventoryService;
import me.hash.mediaroulette.service.GiveawayService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.awt.Color;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

public class GiveawayCommand extends ListenerAdapter implements CommandHandler {
    private static final Logger logger = LoggerFactory.getLogger(GiveawayCommand.class);

    private static final Color PRIMARY_COLOR = new Color(88, 101, 242);
    private static final Color SUCCESS_COLOR = new Color(87, 242, 135);
    private static final Color ERROR_COLOR = new Color(220, 53, 69);
    private static final Color GIVEAWAY_COLOR = new Color(255, 215, 0);

    private final GiveawayService giveawayService;
    private final BotInventoryService botInventoryService;

    public GiveawayCommand() {
        this.giveawayService = new GiveawayService();
        this.botInventoryService = new BotInventoryService();
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash("giveaway", "🎉 Manage giveaways")
                .addSubcommands(
                        new SubcommandData("create", "Create a new giveaway")
                                .addOption(OptionType.STRING, "title", "Giveaway title", true)
                                .addOption(OptionType.STRING, "description", "Giveaway description", true)
                                .addOption(OptionType.STRING, "item_id", "Bot inventory item ID to give away", true)
                                .addOption(OptionType.INTEGER, "duration_hours", "Duration in hours", true)
                                .addOption(OptionType.CHANNEL, "channel", "Channel to post giveaway (default: current)", false)
                                .addOption(OptionType.INTEGER, "max_entries", "Maximum entries (default: unlimited)", false),
                        new SubcommandData("end", "End a giveaway early")
                                .addOption(OptionType.STRING, "giveaway_id", "Giveaway ID", true),
                        new SubcommandData("reroll", "Reroll a giveaway winner")
                                .addOption(OptionType.STRING, "giveaway_id", "Giveaway ID", true),
                        new SubcommandData("list", "List active giveaways"),
                        new SubcommandData("cancel", "Cancel a giveaway")
                                .addOption(OptionType.STRING, "giveaway_id", "Giveaway ID", true)
                );
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("giveaway")) return;

        // Check if user is admin
        User user = Main.userService.getOrCreateUser(event.getUser().getId());
        if (!user.isAdmin()) {
            sendError(event, "❌ **Access Denied**\n\nYou don't have permission to manage giveaways.");
            return;
        }

        String subcommand = event.getSubcommandName();

        switch (subcommand) {
            case "create" -> handleCreate(event);
            case "end" -> handleEnd(event);
            case "reroll" -> handleReroll(event);
            case "list" -> handleList(event);
            case "cancel" -> handleCancel(event);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        
        if (componentId.startsWith("giveaway:enter:")) {
            handleGiveawayEntry(event, componentId);
        }
    }

    private void handleCreate(SlashCommandInteractionEvent event) {
        String title = event.getOption("title").getAsString();
        String description = event.getOption("description").getAsString();
        String itemId = event.getOption("item_id").getAsString();
        int durationHours = event.getOption("duration_hours").getAsInt();
        TextChannel channel = event.getOption("channel") != null ? 
            event.getOption("channel").getAsChannel().asTextChannel() : 
            event.getChannel().asTextChannel();
        Integer maxEntries = event.getOption("max_entries") != null ? 
            event.getOption("max_entries").getAsInt() : null;

        if (durationHours < 1 || durationHours > 168) { // Max 1 week
            sendError(event, "Duration must be between 1 and 168 hours (1 week)");
            return;
        }

        // Get the item from bot inventory
        Optional<BotInventoryItem> itemOpt = botInventoryService.getItem(itemId);
        if (itemOpt.isEmpty()) {
            sendError(event, "Item not found in bot inventory: " + itemId);
            return;
        }

        BotInventoryItem item = itemOpt.get();
        if (!item.canBeGivenAway()) {
            sendError(event, "This item cannot be given away (inactive or expired)");
            return;
        }

        // Create giveaway
        Instant endTime = Instant.now().plus(durationHours, ChronoUnit.HOURS);
        Giveaway giveaway = new Giveaway(title, description, channel.getId(), event.getUser().getId(), item, endTime);
        
        if (maxEntries != null) {
            giveaway.setMaxEntries(maxEntries);
        }

        // Create giveaway embed
        EmbedBuilder embed = createGiveawayEmbed(giveaway);
        
        Button enterButton = Button.primary("giveaway:enter:" + giveaway.getId(), "🎉 Enter Giveaway");
        ActionRow actionRow = ActionRow.of(enterButton);

        event.deferReply(true).queue();

        channel.sendMessageEmbeds(embed.build())
                .setComponents(actionRow)
                .queue(message -> {
                    giveaway.setMessageId(message.getId());
                    giveawayService.createGiveaway(giveaway);
                    
                    // Mark item as used in bot inventory
                    botInventoryService.markItemAsUsed(itemId);
                    
                    EmbedBuilder successEmbed = new EmbedBuilder()
                            .setTitle("✅ Giveaway Created")
                            .setDescription(String.format("Giveaway **%s** has been created in %s!", 
                                title, channel.getAsMention()))
                            .addField("Giveaway ID", "`" + giveaway.getId() + "`", false)
                            .addField("Duration", durationHours + " hours", true)
                            .addField("Prize", item.toString(), true)
                            .setColor(SUCCESS_COLOR)
                            .setTimestamp(Instant.now());
                    
                    event.getHook().sendMessageEmbeds(successEmbed.build()).queue();
                    
                    // Schedule giveaway end
                    scheduleGiveawayEnd(giveaway);
                }, 
                error -> {
                    sendError(event, "Failed to create giveaway message: " + error.getMessage());
                });
    }

    private void handleEnd(SlashCommandInteractionEvent event) {
        String giveawayId = event.getOption("giveaway_id").getAsString();
        
        Optional<Giveaway> giveawayOpt = giveawayService.getGiveaway(giveawayId);
        if (giveawayOpt.isEmpty()) {
            sendError(event, "Giveaway not found: " + giveawayId);
            return;
        }

        Giveaway giveaway = giveawayOpt.get();
        if (giveaway.isCompleted()) {
            sendError(event, "This giveaway has already ended");
            return;
        }

        endGiveaway(giveaway);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("✅ Giveaway Ended")
                .setDescription(String.format("Giveaway **%s** has been ended early.", giveaway.getTitle()))
                .setColor(SUCCESS_COLOR)
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleReroll(SlashCommandInteractionEvent event) {
        String giveawayId = event.getOption("giveaway_id").getAsString();
        
        Optional<Giveaway> giveawayOpt = giveawayService.getGiveaway(giveawayId);
        if (giveawayOpt.isEmpty()) {
            sendError(event, "Giveaway not found: " + giveawayId);
            return;
        }

        Giveaway giveaway = giveawayOpt.get();
        if (!giveaway.isCompleted()) {
            sendError(event, "This giveaway hasn't ended yet");
            return;
        }

        if (giveaway.getEntries().isEmpty()) {
            sendError(event, "No entries to reroll");
            return;
        }

        // Select new winner
        String newWinner = giveaway.selectRandomWinner();
        giveawayService.updateGiveaway(giveaway);

        // Update the giveaway message
        updateGiveawayMessage(giveaway, true);

        // Send winner notification
        sendWinnerNotification(giveaway, newWinner, true);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔄 Giveaway Rerolled")
                .setDescription(String.format("New winner selected for **%s**: <@%s>", 
                    giveaway.getTitle(), newWinner))
                .setColor(SUCCESS_COLOR)
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleList(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        
        Bot.executor.execute(() -> {
            List<Giveaway> activeGiveaways = giveawayService.getActiveGiveaways();
            
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🎉 Active Giveaways")
                    .setColor(GIVEAWAY_COLOR)
                    .setTimestamp(Instant.now());

            if (activeGiveaways.isEmpty()) {
                embed.setDescription("No active giveaways");
            } else {
                StringBuilder description = new StringBuilder();
                for (Giveaway giveaway : activeGiveaways) {
                    description.append(String.format("**%s**\n", giveaway.getTitle()));
                    description.append(String.format("ID: `%s` | Entries: %d | Ends: <t:%d:R>\n", 
                        giveaway.getId(), giveaway.getEntryCount(), giveaway.getEndTime().getEpochSecond()));
                    description.append(String.format("Prize: %s\n\n", giveaway.getPrize().toString()));
                }
                embed.setDescription(description.toString());
            }

            event.getHook().sendMessageEmbeds(embed.build()).queue();
        });
    }

    private void handleCancel(SlashCommandInteractionEvent event) {
        String giveawayId = event.getOption("giveaway_id").getAsString();
        
        Optional<Giveaway> giveawayOpt = giveawayService.getGiveaway(giveawayId);
        if (giveawayOpt.isEmpty()) {
            sendError(event, "Giveaway not found: " + giveawayId);
            return;
        }

        Giveaway giveaway = giveawayOpt.get();
        if (giveaway.isCompleted()) {
            sendError(event, "Cannot cancel a completed giveaway");
            return;
        }

        // Cancel the giveaway
        giveaway.setActive(false);
        giveaway.setCompleted(true);
        giveawayService.updateGiveaway(giveaway);

        // Update the message
        updateGiveawayMessage(giveaway, false);

        // Return item to bot inventory
        giveaway.getPrize().setActive(true);
        botInventoryService.updateItem(giveaway.getPrize());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("❌ Giveaway Cancelled")
                .setDescription(String.format("Giveaway **%s** has been cancelled.", giveaway.getTitle()))
                .setColor(ERROR_COLOR)
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleGiveawayEntry(ButtonInteractionEvent event, String componentId) {
        String giveawayId = componentId.substring("giveaway:enter:".length());
        String userId = event.getUser().getId();

        Optional<Giveaway> giveawayOpt = giveawayService.getGiveaway(giveawayId);
        if (giveawayOpt.isEmpty()) {
            event.reply("❌ This giveaway no longer exists.").setEphemeral(true).queue();
            return;
        }

        Giveaway giveaway = giveawayOpt.get();

        if (!giveaway.canEnter(userId)) {
            String reason;
            if (giveaway.getEntries().contains(userId)) {
                reason = "You have already entered this giveaway!";
            } else if (giveaway.isExpired()) {
                reason = "This giveaway has ended!";
            } else if (giveaway.getMaxEntries() > 0 && giveaway.getEntries().size() >= giveaway.getMaxEntries()) {
                reason = "This giveaway has reached maximum entries!";
            } else {
                reason = "You cannot enter this giveaway.";
            }
            
            event.reply("❌ " + reason).setEphemeral(true).queue();
            return;
        }

        // Add entry
        boolean added = giveaway.addEntry(userId);
        if (added) {
            giveawayService.updateGiveaway(giveaway);
            
            // Update the giveaway message with new entry count
            updateGiveawayMessage(giveaway, false);
            
            event.reply("✅ You have successfully entered the giveaway! Good luck! 🍀").setEphemeral(true).queue();
        } else {
            event.reply("❌ Failed to enter the giveaway. Please try again.").setEphemeral(true).queue();
        }
    }

    private EmbedBuilder createGiveawayEmbed(Giveaway giveaway) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎉 " + giveaway.getTitle())
                .setDescription(giveaway.getDescription())
                .setColor(GIVEAWAY_COLOR)
                .setTimestamp(Instant.now());

        embed.addField("🏆 Prize", giveaway.getPrize().toString(), false);
        embed.addField("⏰ Ends", String.format("<t:%d:F> (<t:%d:R>)", 
            giveaway.getEndTime().getEpochSecond(), giveaway.getEndTime().getEpochSecond()), true);
        embed.addField("👥 Entries", String.valueOf(giveaway.getEntryCount()), true);
        
        if (giveaway.getMaxEntries() > 0) {
            embed.addField("📊 Max Entries", String.valueOf(giveaway.getMaxEntries()), true);
        }

        embed.addField("🎯 How to Enter", "Click the button below to enter!", false);
        embed.setFooter("Giveaway ID: " + giveaway.getId(), null);

        return embed;
    }

    private void updateGiveawayMessage(Giveaway giveaway, boolean isEnded) {
        try {
            TextChannel channel = Bot.getShardManager().getTextChannelById(giveaway.getChannelId());
            if (channel == null) return;

            channel.retrieveMessageById(giveaway.getMessageId()).queue(message -> {
                EmbedBuilder embed;
                
                if (isEnded) {
                    embed = new EmbedBuilder()
                            .setTitle("🎉 " + giveaway.getTitle() + " - ENDED")
                            .setDescription(giveaway.getDescription())
                            .setColor(SUCCESS_COLOR);
                    
                    if (giveaway.getWinnerId() != null) {
                        embed.addField("🏆 Winner", "<@" + giveaway.getWinnerId() + ">", false);
                    } else {
                        embed.addField("🏆 Winner", "No valid entries", false);
                    }
                } else {
                    embed = createGiveawayEmbed(giveaway);
                }

                embed.addField("👥 Total Entries", String.valueOf(giveaway.getEntryCount()), true);
                embed.setFooter("Giveaway ID: " + giveaway.getId(), null);

                if (isEnded || !giveaway.isActive()) {
                    message.editMessageEmbeds(embed.build()).setComponents().queue();
                } else {
                    Button enterButton = Button.primary("giveaway:enter:" + giveaway.getId(), "🎉 Enter Giveaway");
                    message.editMessageEmbeds(embed.build()).setComponents(ActionRow.of(enterButton)).queue();
                }
            });
        } catch (Exception e) {
            logger.error("Failed to update giveaway message: {}", e.getMessage());
        }
    }

    private void endGiveaway(Giveaway giveaway) {
        if (giveaway.getEntries().isEmpty()) {
            giveaway.setCompleted(true);
            giveaway.setActive(false);
            giveawayService.updateGiveaway(giveaway);
            updateGiveawayMessage(giveaway, true);
            return;
        }

        // Select winner
        String winnerId = giveaway.selectRandomWinner();
        giveawayService.updateGiveaway(giveaway);

        // Update message
        updateGiveawayMessage(giveaway, true);

        // Send winner notification
        sendWinnerNotification(giveaway, winnerId, false);
    }

    private void sendWinnerNotification(Giveaway giveaway, String winnerId, boolean isReroll) {
        try {
            TextChannel channel = Bot.getShardManager().getTextChannelById(giveaway.getChannelId());
            if (channel == null) return;

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🎊 Giveaway Winner" + (isReroll ? " (Rerolled)" : ""))
                    .setDescription(String.format("Congratulations <@%s>! You won **%s**!", 
                        winnerId, giveaway.getTitle()))
                    .addField("🏆 Prize", giveaway.getPrize().toString(), false)
                    .setColor(SUCCESS_COLOR)
                    .setTimestamp(Instant.now());

            // For Discord Nitro, send the gift link privately
            if ("discord_nitro".equals(giveaway.getPrize().getType())) {
                embed.addField("🎁 Your Prize", "Check your DMs for your Discord Nitro gift link!", false);
                
                // Send DM with nitro link
                Bot.getShardManager().retrieveUserById(winnerId).queue(user -> {
                    user.openPrivateChannel().queue(dm -> {
                        EmbedBuilder dmEmbed = new EmbedBuilder()
                                .setTitle("🎉 Congratulations! You Won Discord Nitro!")
                                .setDescription(String.format("You won the giveaway: **%s**", giveaway.getTitle()))
                                .addField("🎁 Your Discord Nitro Gift Link", giveaway.getPrize().getValue(), false)
                                .addField("⚠️ Important", "This link is for you only. Do not share it with anyone!", false)
                                .setColor(SUCCESS_COLOR)
                                .setTimestamp(Instant.now());
                        
                        dm.sendMessageEmbeds(dmEmbed.build()).queue();
                    });
                });
            } else {
                // For other prizes, show the value publicly or handle accordingly
                embed.addField("🎁 Prize Details", giveaway.getPrize().getDisplayValue(), false);
                
                // If it's coins, add them to user's account
                if ("coins".equals(giveaway.getPrize().getType())) {
                    try {
                        int coinAmount = Integer.parseInt(giveaway.getPrize().getValue());
                        User user = Main.userService.getOrCreateUser(winnerId);
                        user.addCoins(coinAmount, me.hash.mediaroulette.model.Transaction.TransactionType.GIVEAWAY_WIN, 
                            "Won giveaway: " + giveaway.getTitle());
                        Main.userService.updateUser(user);
                        
                        embed.addField("💰 Coins Added", String.format("%,d coins have been added to your account!", coinAmount), false);
                    } catch (NumberFormatException e) {
                        logger.error("Invalid coin amount in giveaway prize: {}", giveaway.getPrize().getValue());
                    }
                }
            }

            channel.sendMessageEmbeds(embed.build()).queue();
        } catch (Exception e) {
            logger.error("Failed to send winner notification: {}", e.getMessage());
        }
    }

    private void scheduleGiveawayEnd(Giveaway giveaway) {
        // Note: Giveaway ending is handled by the GiveawayService scheduler
        // which checks for expired giveaways every minute automatically
        logger.info("Giveaway {} scheduled to end at {}", giveaway.getId(), giveaway.getEndTime());
    }

    private void sendError(SlashCommandInteractionEvent event, String message) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("❌ Error")
                .setDescription(message)
                .setColor(ERROR_COLOR)
                .setTimestamp(Instant.now());
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
}