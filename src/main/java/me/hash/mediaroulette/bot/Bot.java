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
import me.hash.mediaroulette.bot.commands.config.SettingsCommand;
import me.hash.mediaroulette.bot.commands.images.FavoritesCommand;
import me.hash.mediaroulette.bot.commands.images.getRandomImage;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Bot extends ListenerAdapter {
    private final ShardManager shardManager;
    private final ThreadPoolExecutor executor;
    private final List<ListenerAdapter> listeners = new ArrayList<>();
    private final CooldownManager cooldownManager;

    public Bot(String token) {
        this.executor = new ThreadPoolExecutor(
                Runtime.getRuntime().availableProcessors() * 4, // core
                1000,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(2000),
                new ThreadPoolExecutor.AbortPolicy()
        );

        // Check if maintenance mode is enabled at startup
        boolean maintenanceMode = Main.getLocalConfig().getMaintenanceMode();

        this.shardManager = DefaultShardManagerBuilder.createDefault(token)
                .setActivity(Activity.playing(maintenanceMode ? "🔧 Under Maintenance" : "Use /support for help! | Alpha :3"))
                .setStatus(maintenanceMode ? OnlineStatus.DO_NOT_DISTURB : OnlineStatus.ONLINE)
                .setShardsTotal(-1)
                .addEventListeners(
                        new AdminCommand(),
                        new AutoCompleteHandler(),
                        this
                )
                .build();

        this.cooldownManager = new CooldownManager();

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
    }

    public void addCommands(ListenerAdapter... commands) {
        listeners.addAll(Arrays.asList(commands));
    }

    public void registerCommands() {
        List<CommandData> commandData = listeners.stream()
                .map(cmd -> ((CommandHandler) cmd).getCommandData())
                .collect(Collectors.toList());

        listeners.forEach(cooldownManager::registerListener);

        shardManager.getShards().forEach(jda ->
                jda.updateCommands().addCommands(commandData).queue());

        shardManager.addEventListener(cooldownManager);

        listeners.forEach(shardManager::addEventListener);
    }

    public ShardManager getShardManager() {
        return shardManager;
    }

    public ThreadPoolExecutor getExecutor() {
        return executor;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    @Override
    @SuppressWarnings("ALL")
    public void onReady(@NotNull ReadyEvent event) {
        getShardManager().getGuildById(Main.getEnv("ADMIN_COMMAND_GUILD_ID")).updateCommands().addCommands(new AdminCommand().getCommandData()).queue();
    }
}