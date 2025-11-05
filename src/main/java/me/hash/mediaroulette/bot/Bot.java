package me.hash.mediaroulette.bot;

import me.hash.mediaroulette.bot.utils.AutoCompleteHandler;
import me.hash.mediaroulette.bot.utils.CooldownManager;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

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

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Bot extends ListenerAdapter {
    private static ShardManager shardManager = null;
    public static Config config = null;
    public static final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 4, // core
            1000,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(2000),
            new ThreadPoolExecutor.AbortPolicy()
    );
    private static final List<ListenerAdapter> listeners = new ArrayList<>();
    private static CooldownManager cooldownManager = null;

    public Bot(String token) {
        shardManager = DefaultShardManagerBuilder.createDefault(token)
                .setActivity(Activity.playing("Use /support for help! | Alpha :3"))
                .setStatus(OnlineStatus.ONLINE)
                .setShardsTotal(-1)
                .addEventListeners(
                        new AdminCommand(),
                        new AutoCompleteHandler(),
                        this
                )
                .build();

        cooldownManager = new CooldownManager();

        addCommands(
                new FavoritesCommand(),
                new getRandomImage(),
                new ChancesCommand(),
                new DictionaryCommand(Main.getDictionaryService()),
                new SettingsCommand(Main.getDictionaryService()),
                new GiveawayCommand(),
                new ChannelNuke(),
                new InfoCommand(),
                new SupportCommand()
        );

        config = new Config(Main.getDatabase());
    }

    public static void addCommands(ListenerAdapter... commands) {
        listeners.addAll(Arrays.asList(commands));
    }

    public static void registerCommands() {
        List<CommandData> commandData = listeners.stream()
                .map(cmd -> ((CommandHandler) cmd).getCommandData())
                .collect(Collectors.toList());

        listeners.forEach(cooldownManager::registerListener);

        Bot.getShardManager().getShards().forEach(jda ->
                jda.updateCommands().addCommands(commandData).queue());

        Bot.getShardManager().addEventListener(cooldownManager);

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
    @SuppressWarnings("ALL")
    public void onReady(@NotNull ReadyEvent event) {
        getShardManager().getGuildById(Main.getEnv("ADMIN_COMMAND_GUILD_ID")).updateCommands().addCommands(new AdminCommand().getCommandData()).queue();
    }
}