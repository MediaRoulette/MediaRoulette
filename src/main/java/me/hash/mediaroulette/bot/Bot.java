package me.hash.mediaroulette.bot;

import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
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
import me.hash.mediaroulette.bot.commands.images.FavoritesCommand;
import me.hash.mediaroulette.bot.commands.images.getRandomImage;
import me.hash.mediaroulette.utils.Config;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;

import javax.print.DocFlavor;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Bot extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(Bot.class);

    private static ShardManager shardManager = null;
    public static final long COOLDOWN_DURATION = 2500;
    public static final Map<Long, Long> COOLDOWNS = new HashMap<>();
    public static Config config = null;
    public static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final List<ListenerAdapter> listeners = new ArrayList<>();

    public Bot(String token) {
        shardManager = DefaultShardManagerBuilder.createDefault(token)
                .setActivity(Activity.playing("Use /support for help! | Alpha :3"))
                .setStatus(OnlineStatus.ONLINE)
                .setShardsTotal(-1)
                .build();

        addCommands(
                new FavoritesCommand(),
                new getRandomImage(),
                new ChancesCommand(),
                new DictionaryCommand(Main.dictionaryService),
                new SettingsCommand(Main.dictionaryService),
                new AdminCommand(),
                new GiveawayCommand(),
                new ChannelNuke(),
                new InfoCommand(),
                new SupportCommand()
        );

        config = new Config(Main.database);
    }

    public static void addCommands(ListenerAdapter... commands) {
        listeners.addAll(Arrays.asList(commands));
    }

    public static void registerCommands() {
        List<CommandData> commandData = listeners.stream()
                .map(cmd -> ((CommandHandler) cmd).getCommandData())
                .collect(Collectors.toList());

        Bot.getShardManager().getShards().forEach(jda ->
                jda.updateCommands().addCommands(commandData).queue());

        listeners.forEach(Bot.getShardManager()::addEventListener);
    }

    /**
     * Public method to obtain the ShardManager instance.
     *
     * @return ShardManager instance.
     */
    public static ShardManager getShardManager() {
        return shardManager;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        getShardManager().getGuildById(Main.getEnv("ADMIN_COMMAND_GUILD_ID")).updateCommands().addCommands(new AdminCommand().getCommandData()).queue();
    }
}