package me.hash.mediaroulette.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.bot.commands.CommandHandler;
import me.hash.mediaroulette.bot.commands.admin.AdminCommand;
import me.hash.mediaroulette.bot.commands.admin.ChannelNuke;
import me.hash.mediaroulette.bot.commands.admin.GiveawayCommand;
import me.hash.mediaroulette.bot.commands.bot.*;
import me.hash.mediaroulette.bot.commands.config.ChancesCommand;
import me.hash.mediaroulette.bot.commands.dictionary.DictionaryCommand;
import me.hash.mediaroulette.bot.commands.dictionary.SettingsCommand;
import me.hash.mediaroulette.bot.commands.economy.BalanceCommand;
import me.hash.mediaroulette.bot.commands.economy.QuestsCommand;
import me.hash.mediaroulette.bot.commands.images.FavoritesCommand;
import me.hash.mediaroulette.bot.commands.images.getRandomImage;
import me.hash.mediaroulette.utils.Config;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;

import java.util.*;
import java.util.concurrent.*;

public class Bot {
    private static final Logger logger = LoggerFactory.getLogger(Bot.class);
    private static ShardManager shardManager = null;
    public static final long COOLDOWN_DURATION = 2500;
    public static final Map<Long, Long> COOLDOWNS = new HashMap<>();
    public static Config config = null;
    public static final ExecutorService executor = Executors.newCachedThreadPool();

    public Bot(String token) {
        shardManager = DefaultShardManagerBuilder.createDefault(token)
                .setActivity(Activity.playing("Use /support for help! | Alpha :3"))
                .setStatus(OnlineStatus.ONLINE)
                .setShardsTotal(-1)
                .build();

        registerEventListeners();
        registerGlobalCommands();

        config = new Config(Main.database);
    }

    /**
     * Registers all commands and event listeners with the ShardManager and its shards.
     */
    private void registerEventListeners() {
        if (shardManager != null) {
            List<CommandHandler> commandHandlers = Arrays.asList(
                    new FavoritesCommand(),
                    new getRandomImage(),
                    new ChancesCommand(),
                    new DictionaryCommand(Main.dictionaryService),
                    new SettingsCommand(Main.dictionaryService),
                    new AdminCommand(),
                    new GiveawayCommand(),
                    new ChannelNuke(),
                    new InfoCommand(),
                    new SupportCommand(),
                    new ThemeCommand(),
                    new BalanceCommand(),
                    new QuestsCommand()
                    // new ShopCommand()
            );

            // Add all event listeners (global to the entire bot)
            commandHandlers.forEach(shardManager::addEventListener);
            
            // Add autocomplete handler
            shardManager.addEventListener(new AutoCompleteHandler());

            logger.info("Registered all event listeners.");
        }
    }

    /**
     * Registers global slash commands for all shards.
     */
    private void registerGlobalCommands() {
        if (shardManager != null) {
            // Collect all command data
            List<CommandData> commands = Arrays.asList(
                    new FavoritesCommand().getCommandData(),
                    new getRandomImage().getCommandData(),
                    new ChancesCommand().getCommandData(),
                    new DictionaryCommand(Main.dictionaryService).getCommandData(),
                    new SettingsCommand(Main.dictionaryService).getCommandData(),
                    new AdminCommand().getCommandData(),
                    new GiveawayCommand().getCommandData(),
                    new ChannelNuke().getCommandData(),
                    new InfoCommand().getCommandData(),
                    new SupportCommand().getCommandData(),
                    new ThemeCommand().getCommandData(),
                    new BalanceCommand().getCommandData(),
                    new QuestsCommand().getCommandData()
                    // new ShopCommand().getCommandData()
                    // new MediaHuntCommand().getCommandData() // Temporarily disabled
            );

            shardManager.getShards().forEach(jda -> jda.updateCommands().addCommands(commands).queue());

            logger.info("Registered all global slash commands.");
        }
    }

    /**
     * Public method to obtain the ShardManager instance.
     *
     * @return ShardManager instance.
     */
    public static ShardManager getShardManager() {
        return shardManager;
    }
}