package dev.mediaroulette;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bson.Document;

import com.mongodb.client.MongoCollection;
import io.github.cdimascio.dotenv.Dotenv;

import dev.mediaroulette.bot.Bot;
import dev.mediaroulette.plugins.PluginManager;
import dev.mediaroulette.repository.*;
import dev.mediaroulette.service.DictionaryService;
import dev.mediaroulette.service.StatsTrackingService;
import dev.mediaroulette.config.LocalConfig;
import dev.mediaroulette.database.Database;
import dev.mediaroulette.service.GiveawayManager;
import dev.mediaroulette.utils.media.MediaInitializer;
import dev.mediaroulette.utils.resources.ResourceManager;
import dev.mediaroulette.utils.startup.StartupManager;
import dev.mediaroulette.utils.terminal.TerminalInterface;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 3;
    private static final int SHUTDOWN_DELAY_MS = 500;

    // Environment and Configuration
    private static final Dotenv env = initializeEnvironment();
    private static LocalConfig localConfig;

    // Core Components
    private static Database database;
    private static Bot bot;
    private static PluginManager pluginManager;
    private static TerminalInterface terminal;

    // Services
    private static dev.mediaroulette.service.domain.AccountService accountService;
    private static dev.mediaroulette.service.domain.WalletService walletService;
    private static dev.mediaroulette.service.domain.PreferencesService preferencesService;
    private static dev.mediaroulette.service.domain.UsageStatsService usageStatsService;
    private static dev.mediaroulette.service.domain.QuestProgressService questProgressService;
    private static dev.mediaroulette.service.domain.InventoryService inventoryService;
    private static DictionaryService dictionaryService;
    private static StatsTrackingService statsService;

    // Application Metadata
    public static final long START_TIME = System.currentTimeMillis();

    // ==================== Initialization ====================

    public static void main(String[] args) throws Exception {
        registerShutdownHook();
        localConfig = LocalConfig.getInstance();
        
        // Use StartupManager for clean, orchestrated startup
        StartupManager startup = new StartupManager()
                .addTask("Configuration", Main::initializeConfig)
                .addTask("Resources", Main::initializeResources)
                .addTask("Database", Main::initializeDatabaseTask)
                .addTask("Services", Main::initializeServicesTask)
                .addTask("FFmpeg", Main::initializeMediaTask)
                .addTask("Bot", Main::initializeBotTask)
                .addTask("Plugins", Main::initializePluginsTask)
                .addTask("Giveaways", Main::initializeGiveawaysTask);
        
        startup.execute();
        startTerminalInterface();
    }
    
    private static String initializeConfig() {
        return localConfig.getMaintenanceMode() ? "Maintenance Mode" : "Ready";
    }

    private static Dotenv initializeEnvironment() {
        return Dotenv.configure()
                .directory("./")
                .filename(".env")
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();
    }

    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received");
            shutdown();
        }, "Shutdown-Hook"));
    }

    // ==================== Infrastructure Setup ====================

    private static String initializeResources() {
        try {
            ResourceManager.getInstance().initialize().get(60, TimeUnit.SECONDS);
            int count = ResourceManager.getInstance().getResourceCount();
            return count > 0 ? count + " files" : "Ready";
        } catch (Exception e) {
            logger.debug("Resource init failed: {}", e.getMessage());
            return "Limited";
        }
    }

    private static String initializeDatabaseTask() {
        String connectionString = getEnv("MONGODB_CONNECTION");
        
        if (connectionString == null || connectionString.isEmpty()) {
            throw new IllegalStateException("MONGODB_CONNECTION not set");
        }
        
        database = new Database(connectionString, "MediaRoulette");
        return "Connected";
    }
    
    private static String initializeServicesTask() {
        initializeServices();
        return "Ready";
    }

    private static void initializeServices() {

        MongoCollection<Document> userCollection = database.getCollection("user");
        MongoCollection<Document> dictionaryCollection = database.getCollection("dictionary");
        MongoCollection<Document> assignmentCollection = database.getCollection("dictionary_assignment");

        DictionaryRepository dictionaryRepository = new MongoDictionaryRepository(
                dictionaryCollection,
                assignmentCollection
        );

        accountService = new dev.mediaroulette.service.domain.AccountService(new MongoAccountRepository(database.getCollection("accounts")));
        walletService = new dev.mediaroulette.service.domain.WalletService(new MongoWalletRepository(database.getCollection("wallets")));
        preferencesService = new dev.mediaroulette.service.domain.PreferencesService(new MongoPreferencesRepository(database.getCollection("preferences")));
        usageStatsService = new dev.mediaroulette.service.domain.UsageStatsService(new MongoUsageStatsRepository(database.getCollection("usage_stats")));
        questProgressService = new dev.mediaroulette.service.domain.QuestProgressService(new MongoQuestProgressRepository(database.getCollection("quest_progress")));
        inventoryService = new dev.mediaroulette.service.domain.InventoryService(new MongoInventoryRepository(database.getCollection("inventories")));
        dictionaryService = new DictionaryService(dictionaryRepository);
        statsService = new StatsTrackingService(new MongoAccountRepository(database.getCollection("accounts")));

        initializeDefaultDictionaries();
    }

    private static void initializeDefaultDictionaries() {
        try {
            if (dictionaryService.getAccessibleDictionaries("system").isEmpty()) {
                var basicDict = dictionaryService.createDictionary(
                        "Basic Dictionary",
                        "Default words for general use",
                        "system"
                );
                basicDict.setDefault(true);
                basicDict.setPublic(true);

                List<String> defaultWords = Arrays.asList(
                        "funny", "cute", "happy", "random", "cool",
                        "awesome", "nice", "good", "best", "amazing"
                );
                basicDict.addWords(defaultWords);
            }
        } catch (Exception e) {
            logger.error("Failed to initialize default dictionaries", e);
        }
    }

    private static String initializeMediaTask() {
        try {
            MediaInitializer.initialize().get();
            return MediaInitializer.isInitialized() ? "Available" : "Limited";
        } catch (Exception e) {
            logger.debug("Media init failed: {}", e.getMessage());
            return "Unavailable";
        }
    }

    // ==================== Bot and Plugin Setup ====================

    private static String initializeBotTask() {
        String discordToken = getEnv("DISCORD_TOKEN");
        
        if (discordToken == null || discordToken.isEmpty()) {
            throw new IllegalStateException("DISCORD_TOKEN not set");
        }
        
        bot = new Bot(discordToken);
        return "Ready";
    }
    
    private static String initializePluginsTask() {
        pluginManager = new PluginManager();

        File pluginDir = new File("plugins");
        if (!pluginDir.exists()) {
            pluginDir.mkdirs();
        }

        pluginManager.loadPlugins(pluginDir);
        pluginManager.enablePlugins();

        if (bot != null) {
        	bot.registerCommands();
        }
        
        int count = pluginManager.getPlugins().size();
        return count + " loaded";
    }
    
    private static String initializeGiveawaysTask() {
        GiveawayManager.initialize();
        return "Ready";
    }

    private static void startTerminalInterface() {
        terminal = new TerminalInterface();
        Thread terminalThread = new Thread(terminal::start, "Terminal-Interface");
        terminalThread.setDaemon(true);
        terminalThread.start();
    }

    // ==================== Shutdown Management ====================

    public static void shutdown() {
        logger.info("Initiating graceful shutdown...");

        shutdownTerminal();
        shutdownBot();
        shutdownServices();
        shutdownMediaProcessing();
        shutdownDatabase();

        logger.info("Shutdown complete. Goodbye!");
        scheduleExit();
    }

    private static void shutdownTerminal() {
        if (terminal != null) {
            safeShutdown("Terminal", terminal::stop);
        }
    }

    private static void shutdownBot() {
        if (bot != null) {
            safeShutdown("Bot", () -> {
                bot.getShardManager().shutdown();
                bot.getExecutor().shutdown();
                if (!bot.getExecutor().awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    bot.getExecutor().shutdownNow();
                }
            });
        }
    }

    private static void shutdownServices() {
        if (statsService != null) {
            safeShutdown("Stats Service", statsService::shutdown);
        }

        if (accountService != null) safeShutdown("Account Service", accountService::shutdown);
        if (walletService != null) safeShutdown("Wallet Service", walletService::shutdown);
        if (preferencesService != null) safeShutdown("Preferences Service", preferencesService::shutdown);
        if (usageStatsService != null) safeShutdown("UsageStats Service", usageStatsService::shutdown);
        if (questProgressService != null) safeShutdown("QuestProgress Service", questProgressService::shutdown);
        if (inventoryService != null) safeShutdown("Inventory Service", inventoryService::shutdown);

        safeShutdown("Giveaway Service", GiveawayManager::shutdown);

        safeShutdown("Image Interaction Service",
                dev.mediaroulette.service.ImageInteractionService.getInstance()::shutdown
        );
        
        safeShutdown("Image Memory Manager", 
                dev.mediaroulette.utils.media.ImageMemoryManager.getInstance()::shutdown
        );
    }

    private static void shutdownMediaProcessing() {
        safeShutdown("Media Processing", MediaInitializer::shutdown);
        
        safeShutdown("Media Container Cleanup", 
                dev.mediaroulette.bot.MediaContainerManager::cleanup
        );
    }

    private static void shutdownDatabase() {
        if (database != null) {
            safeShutdown("Database", database::close);
        }
    }

    private static void safeShutdown(String componentName, ShutdownTask task) {
        try {
            task.execute();
            logger.info("{} shutdown complete", componentName);
        } catch (Exception e) {
            logger.error("Error during {} shutdown", componentName, e);
        }
    }

    private static void scheduleExit() {
        new Thread(() -> {
            try {
                Thread.sleep(SHUTDOWN_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.exit(0);
        }, "Exit-Scheduler").start();
    }

    // ==================== Public Accessors ====================

    public static Dotenv getEnvironment() {
        return env;
    }

    /**
     * Get a secret/environment variable value.
     * Priority: .env file -> System environment variables
     */
    public static String getEnv(String key) {
        // Fallback to .env file or system environment
        String value = env.get(key);
        return value != null ? value : System.getenv(key);
    }
    
    public static LocalConfig getLocalConfig() {
        return localConfig;
    }

    public static Database getDatabase() {
        return database;
    }

    public static Bot getBot() {
        return bot;
    }

    public static PluginManager getPluginManager() {
        return pluginManager;
    }

    public static dev.mediaroulette.service.domain.AccountService getAccountService() { return accountService; }
    public static dev.mediaroulette.service.domain.WalletService getWalletService() { return walletService; }
    public static dev.mediaroulette.service.domain.PreferencesService getPreferencesService() { return preferencesService; }
    public static dev.mediaroulette.service.domain.UsageStatsService getUsageStatsService() { return usageStatsService; }
    public static dev.mediaroulette.service.domain.QuestProgressService getQuestProgressService() { return questProgressService; }
    public static dev.mediaroulette.service.domain.InventoryService getInventoryService() { return inventoryService; }
    public static DictionaryService getDictionaryService() {
        return dictionaryService;
    }

    public static StatsTrackingService getStatsService() {
        return statsService;
    }

    // ==================== Functional Interface ====================

    @FunctionalInterface
    private interface ShutdownTask {
        void execute() throws Exception;
    }
}