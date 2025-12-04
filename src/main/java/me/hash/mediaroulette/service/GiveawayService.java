package me.hash.mediaroulette.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.model.BotInventoryItem;
import me.hash.mediaroulette.model.Giveaway;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import net.dv8tion.jda.api.EmbedBuilder;
import org.bson.Document;

import java.awt.Color;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GiveawayService {
    private static final Logger logger = LoggerFactory.getLogger(GiveawayService.class);
    private final MongoCollection<Document> collection;
    private final BotInventoryService botInventoryService;
    private final ScheduledExecutorService scheduler;

    public GiveawayService() {
        this.collection = Main.getDatabase().getCollection("giveaways");
        this.botInventoryService = new BotInventoryService();
        this.scheduler = Executors.newScheduledThreadPool(2);
        
        // Check for ended giveaways every minute
        scheduler.scheduleAtFixedRate(this::checkEndedGiveaways, 1, 1, TimeUnit.MINUTES);
    }

    public void createGiveaway(Giveaway giveaway) {
        Document doc = giveawayToDocument(giveaway);
        collection.insertOne(doc);
    }

    public void saveGiveaway(Giveaway giveaway) {
        Document doc = giveawayToDocument(giveaway);
        collection.insertOne(doc);
    }

    public void updateGiveaway(Giveaway giveaway) {
        Document doc = giveawayToDocument(giveaway);
        collection.replaceOne(Filters.eq("_id", giveaway.getId()), doc);
    }

    public Optional<Giveaway> getGiveaway(String giveawayId) {
        Document doc = collection.find(Filters.eq("_id", giveawayId)).first();
        if (doc != null) {
            return Optional.of(documentToGiveaway(doc));
        }
        return Optional.empty();
    }

    public List<Giveaway> getActiveGiveaways() {
        List<Giveaway> giveaways = new ArrayList<>();
        for (Document doc : collection.find(Filters.eq("isActive", true))) {
            giveaways.add(documentToGiveaway(doc));
        }
        return giveaways;
    }

    public void endGiveaway(Giveaway giveaway) {
        if (giveaway.getEntries().isEmpty()) {
            giveaway.setActive(false);
            giveaway.setCompleted(true);
            updateGiveaway(giveaway);
            
            // Notify that no one entered
            notifyNoWinner(giveaway);
            return;
        }

        String winnerId = giveaway.selectRandomWinner();
        botInventoryService.markItemAsUsed(giveaway.getPrize().getId());
        updateGiveaway(giveaway);
        
        notifyWinner(giveaway, winnerId);
    }

    public void notifyWinner(Giveaway giveaway, String winnerId) {
        BotInventoryItem prize = giveaway.getPrize();
        
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Giveaway Winner!")
            .setColor(new Color(87, 242, 135))
            .setTimestamp(Instant.now());

        // Send message in the giveaway channel
        Main.getBot().getShardManager().getTextChannelById(giveaway.getChannelId())
            .sendMessage(String.format("<@%s>", winnerId))
            .setEmbeds(embed.build()).queue();

        // Send DM to winner
        Main.getBot().getShardManager().getUserById(winnerId).openPrivateChannel().queue(channel -> {
            EmbedBuilder dmEmbed = new EmbedBuilder()
                .setTitle("Congratulations! You Won!")
                .setDescription(String.format("Congratulations! You won **%s** from the giveaway: **%s**", 
                    prize.getName(), giveaway.getTitle()))
                .addField("Prize Details", prize.getDescription(), false)
                .setColor(new Color(87, 242, 135))
                .setTimestamp(Instant.now());

            if ("discord_nitro".equals(prize.getType())) {
                dmEmbed.addField("Your Gift Link", prize.getValue(), false);
                dmEmbed.setFooter("Redeem this link quickly as it may expire!");
            }

            channel.sendMessageEmbeds(dmEmbed.build()).queue();
        }, error -> {
            // If DM fails, send in channel
            Main.getBot().getShardManager().getTextChannelById(giveaway.getChannelId())
                .sendMessage(String.format("<@%s> I couldn't DM you your prize! Here it is: %s", 
                    winnerId, "discord_nitro".equals(prize.getType()) ? prize.getValue() : 
                    "Won giveaway: " + giveaway.getTitle()))
                .queue();
        });
    }

    private void notifyNoWinner(Giveaway giveaway) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Giveaway Ended")
            .setDescription(String.format("The giveaway **%s** ended with no entries.", giveaway.getTitle()))
            .setColor(new Color(220, 53, 69))
            .setTimestamp(Instant.now());

        Main.getBot().getShardManager().getTextChannelById(giveaway.getChannelId())
            .sendMessageEmbeds(embed.build()).queue();
    }

    private void checkEndedGiveaways() {
        List<Giveaway> activeGiveaways = getActiveGiveaways();
        for (Giveaway giveaway : activeGiveaways) {
            if (giveaway.isExpired()) {
                endGiveaway(giveaway);
            }
        }
    }

    public int cleanupOldGiveaways(int daysOld) {
        try {
            Instant cutoffDate = Instant.now().minus(daysOld, ChronoUnit.DAYS);
            
            // Find completed giveaways older than the cutoff date
            List<Document> oldGiveaways = collection.find(
                Filters.and(
                    Filters.eq("isCompleted", true),
                    Filters.lt("endTime", cutoffDate.toString())
                )
            ).into(new ArrayList<>());
            
            // Delete old giveaways
            if (!oldGiveaways.isEmpty()) {
                List<String> idsToDelete = oldGiveaways.stream()
                    .map(doc -> doc.getString("_id"))
                    .toList();
                
                collection.deleteMany(Filters.in("_id", idsToDelete));
                return idsToDelete.size();
            }
            
            return 0;
        } catch (Exception e) {
            logger.error("Error cleaning up old giveaways: {}", e.getMessage());
            return 0;
        }
    }

    private Document giveawayToDocument(Giveaway giveaway) {
        Document prizeDoc = new Document("id", giveaway.getPrize().getId())
            .append("name", giveaway.getPrize().getName())
            .append("description", giveaway.getPrize().getDescription())
            .append("type", giveaway.getPrize().getType())
            .append("rarity", giveaway.getPrize().getRarity())
            .append("value", giveaway.getPrize().getValue());

        return new Document("_id", giveaway.getId())
            .append("title", giveaway.getTitle())
            .append("description", giveaway.getDescription())
            .append("channelId", giveaway.getChannelId())
            .append("messageId", giveaway.getMessageId())
            .append("hostId", giveaway.getHostId())
            .append("prize", prizeDoc)
            .append("startTime", giveaway.getStartTime().toString())
            .append("endTime", giveaway.getEndTime().toString())
            .append("maxEntries", giveaway.getMaxEntries())
            .append("entries", giveaway.getEntries())
            .append("winnerId", giveaway.getWinnerId())
            .append("isActive", giveaway.isActive())
            .append("isCompleted", giveaway.isCompleted())
            .append("requirements", giveaway.getRequirements());
    }

    private Giveaway documentToGiveaway(Document doc) {
        Giveaway giveaway = new Giveaway();
        giveaway.setId(doc.getString("_id"));
        giveaway.setTitle(doc.getString("title"));
        giveaway.setDescription(doc.getString("description"));
        giveaway.setChannelId(doc.getString("channelId"));
        giveaway.setMessageId(doc.getString("messageId"));
        giveaway.setHostId(doc.getString("hostId"));
        giveaway.setStartTime(Instant.parse(doc.getString("startTime")));
        giveaway.setEndTime(Instant.parse(doc.getString("endTime")));
        giveaway.setMaxEntries(doc.getInteger("maxEntries", -1));
        giveaway.setEntries(doc.getList("entries", String.class, new ArrayList<>()));
        giveaway.setWinnerId(doc.getString("winnerId"));
        giveaway.setActive(doc.getBoolean("isActive", true));
        giveaway.setCompleted(doc.getBoolean("isCompleted", false));
        giveaway.setRequirements(doc.getString("requirements"));

        // Reconstruct prize
        Document prizeDoc = doc.get("prize", Document.class);
        if (prizeDoc != null) {
            BotInventoryItem prize = new BotInventoryItem();
            prize.setId(prizeDoc.getString("id"));
            prize.setName(prizeDoc.getString("name"));
            prize.setDescription(prizeDoc.getString("description"));
            prize.setType(prizeDoc.getString("type"));
            prize.setRarity(prizeDoc.getString("rarity"));
            prize.setValue(prizeDoc.getString("value"));
            giveaway.setPrize(prize);
        }

        return giveaway;
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}