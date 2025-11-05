package me.hash.mediaroulette;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bson.Document;

import com.mongodb.client.MongoCollection;
import io.github.cdimascio.dotenv.Dotenv;

import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.plugins.PluginManager;
import me.hash.mediaroulette.repository.*;
import me.hash.mediaroulette.service.DictionaryService;
import me.hash.mediaroulette.service.StatsTrackingService;
import me.hash.mediaroulette.utils.*;
import me.hash.mediaroulette.utils.media.MediaInitializer;
import me.hash.mediaroulette.utils.terminal.TerminalInterface;
import me.hash.mediaroulette.utils.user.UserService;

/**
 * Main application entry point for MediaRoulette Discord bot.
 * Handles initialization, configuration, and graceful shutdown of all system components.
 */
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
    private static UserService userService;
    private static DictionaryService dictionaryService;
    private static StatsTrackingService statsService;

    // Application Metadata
    public static final long START_TIME = System.currentTimeMillis();

    // ==================== Initialization ====================

    public static void main(String[] args) throws Exception {
        registerShutdownHook();

        logger.info("Starting MediaRoulette application...");

        localConfig = LocalConfig.getInstance();
        initializeInfrastructure();
        initializeBot();
        initializePlugins();
        initializeGiveaways();
        startTerminalInterface();

        logger.info("MediaRoulette application started successfully");
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

    private static void initializeInfrastructure() {
        initializeDatabase();
        initializeServices();
        initializeMediaProcessing();
    }

    private static void initializeDatabase() {
        logger.info("Initializing database connection...");
        database = new Database(getEnv("MONGODB_CONNECTION"), "MediaRoulette");
    }

    private static void initializeServices() {
        logger.info("Initializing services...");

        MongoCollection<Document> userCollection = database.getCollection("user");
        MongoCollection<Document> dictionaryCollection = database.getCollection("dictionary");
        MongoCollection<Document> assignmentCollection = database.getCollection("dictionary_assignment");

        UserRepository userRepository = new MongoUserRepository(userCollection);
        DictionaryRepository dictionaryRepository = new MongoDictionaryRepository(
                dictionaryCollection,
                assignmentCollection
        );

        userService = new UserService(userRepository);
        dictionaryService = new DictionaryService(dictionaryRepository);
        statsService = new StatsTrackingService(userRepository);

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

                logger.info("Default dictionary initialized with {} words", defaultWords.size());
            }
        } catch (Exception e) {
            logger.error("Failed to initialize default dictionaries", e);
        }
    }

    private static void initializeMediaProcessing() {
        logger.info("Initializing media processing...");
        try {
            MediaInitializer.initialize().get();
            logger.info("Media processing ready (FFmpeg available)");
        } catch (Exception e) {
            logger.warn("Media processing initialization failed: {}", e.getMessage());
            logger.warn("Video processing features unavailable, bot will continue with limited functionality");
        }
    }

    // ==================== Bot and Plugin Setup ====================

    private static void initializeBot() {
        logger.info("Initializing Discord bot...");
        bot = new Bot(getEnv("DISCORD_TOKEN"));
    }

    private static void initializePlugins() {
        logger.info("Initializing plugin system...");
        pluginManager = new PluginManager();

        File pluginDir = new File("plugins");
        if (!pluginDir.exists()) {
            pluginDir.mkdirs();
            logger.info("Created plugins directory");
        }

        pluginManager.loadPlugins(pluginDir);
        pluginManager.enablePlugins();

        logger.info("Loaded {} plugins", pluginManager.getPlugins().size());

        // Register commands after loading plugins to make sure plugin commands are loaded
        Bot.registerCommands();
    }

    private static void initializeGiveaways() {
        logger.info("Initializing giveaway system...");
        GiveawayManager.initialize();
    }

    private static void startTerminalInterface() {
        printStartupSummary();

        terminal = new TerminalInterface();
        Thread terminalThread = new Thread(terminal::start, "Terminal-Interface");
        terminalThread.setDaemon(true);
        terminalThread.start();
    }

    private static void printStartupSummary() {
        logger.info("==================================================");
        logger.info("MediaRoulette Bot Status Summary");
        logger.info("--------------------------------------------------");
        logger.info("Bot:              {}", getStatus(bot));
        logger.info("Database:         {}", getStatus(database));
        logger.info("Media Processing: {}", MediaInitializer.isInitialized() ? "Ready" : "Limited");
        logger.info("Maintenance Mode: {}", localConfig.getMaintenanceMode());
        logger.info("Plugins Loaded:   {}", pluginManager != null ? pluginManager.getPlugins().size() : 0);
        logger.info("==================================================");
    }

    private static String getStatus(Object component) {
        return component != null ? "Initialized" : "Failed";
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
                Bot.getShardManager().shutdown();
                Bot.executor.shutdown();
                if (!Bot.executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    Bot.executor.shutdownNow();
                }
            });
        }
    }

    private static void shutdownServices() {
        if (statsService != null) {
            safeShutdown("Stats Service", statsService::shutdown);
        }

        safeShutdown("Giveaway Service", GiveawayManager::shutdown);

        safeShutdown("Image Command Cleanup",
                me.hash.mediaroulette.bot.commands.images.getRandomImage::shutdownCleanupExecutor
        );
    }

    private static void shutdownMediaProcessing() {
        safeShutdown("Media Processing", MediaInitializer::shutdown);
    }

    private static void shutdownDatabase() {
        if (database != null) {
            logger.info("Database connections closed");
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

    public static String getEnv(String key) {
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

    public static UserService getUserService() {
        return userService;
    }

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