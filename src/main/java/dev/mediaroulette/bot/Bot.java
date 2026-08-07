package dev.mediaroulette.bot;

import dev.mediaroulette.bot.utils.AutoCompleteHandler;
import dev.mediaroulette.bot.utils.CooldownManager;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import dev.mediaroulette.Main;
import dev.mediaroulette.bot.commands.CommandHandler;
import dev.mediaroulette.bot.commands.admin.AdminCommand;
import dev.mediaroulette.bot.commands.admin.ChannelNuke;
import dev.mediaroulette.bot.commands.admin.GiveawayCommand;
import dev.mediaroulette.bot.commands.bot.*;
import dev.mediaroulette.bot.commands.config.ChancesCommand;
import dev.mediaroulette.bot.commands.dictionary.DictionaryCommand;
import dev.mediaroulette.bot.commands.config.SettingsCommand;
import dev.mediaroulette.bot.commands.images.FavoritesCommand;
import dev.mediaroulette.bot.commands.images.GetRandomImageCommand;

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
    private final java.util.concurrent.ExecutorService executor;
    private final List<ListenerAdapter> listeners = new ArrayList<>();
    private final CooldownManager cooldownManager;

    public Bot(String token) {
        this.executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

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
                new GetRandomImageCommand(),
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

    public java.util.concurrent.ExecutorService getExecutor() {
        return executor;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        net.dv8tion.jda.api.entities.Guild guild = getShardManager().getGuildById(Main.getEnv("ADMIN_COMMAND_GUILD_ID"));
        if (guild != null) {
            guild.updateCommands().addCommands(new AdminCommand().getCommandData()).queue();
        } else {
            System.err.println("Admin command guild not found/cached yet on startup");
        }
    }
}