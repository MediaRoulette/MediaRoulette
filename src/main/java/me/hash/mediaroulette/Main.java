package me.hash.mediaroulette;

import java.io.File;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.client.MongoCollection;
import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import me.hash.mediaroulette.bot.Bot;
import me.hash.mediaroulette.plugins.PluginManager;
import me.hash.mediaroulette.utils.LocalConfig;
import me.hash.mediaroulette.repository.MongoUserRepository;
import me.hash.mediaroulette.repository.UserRepository;
import me.hash.mediaroulette.repository.DictionaryRepository;
import me.hash.mediaroulette.repository.MongoDictionaryRepository;
import me.hash.mediaroulette.service.DictionaryService;
import me.hash.mediaroulette.service.StatsTrackingService;
import me.hash.mediaroulette.utils.terminal.TerminalInterface;
import me.hash.mediaroulette.utils.Database;
import me.hash.mediaroulette.utils.user.UserService;
import me.hash.mediaroulette.utils.media.MediaInitializer;
import me.hash.mediaroulette.utils.GiveawayManager;
import org.bson.Document;

public class Main {
    // Initialize environment first to avoid logger initialization issues
    public static final Dotenv env = initializeEnv();
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    // Static services and components
    public static LocalConfig localConfig;
    public static Database database;
    public static Bot bot;
    public static UserService userService;
    public static DictionaryService dictionaryService;
    public static StatsTrackingService statsService;
    public static TerminalInterface terminal;
    public static final long startTime = System.currentTimeMillis();
    private static PluginManager pluginManager;

    private static Dotenv initializeEnv() {
        return Dotenv.configure()
                .directory("./")
                .filename(".env")
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();
    }

    public static PluginManager getPluginManager() {
        return pluginManager;
    }

    public static void main(String[] args) throws Exception {
        // Setup shutdown hook first for proper cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received...");
            shutdown();
        }));

        // Initialize local config
        localConfig = LocalConfig.getInstance();

        initializeServices();

        initializeMediaProcessing();

        bot = new Bot(getEnv("DISCORD_TOKEN"));

        pluginManager = new PluginManager();

        File pluginDir = new File("plugins");
        if (!pluginDir.exists()) {
            pluginDir.mkdirs();
        }

        pluginManager.loadPlugins(pluginDir);
        pluginManager.enablePlugins();

        logger.info("Application started with {} plugins", pluginManager.getPlugins().size());

        GiveawayManager.initialize();

        // Start terminal interface
        startTerminalInterface();
    }

    private static void initializeServices() {
        database = new Database(getEnv("MONGODB_CONNECTION"), "MediaRoulette");

        MongoCollection<Document> userCollection = database.getCollection("user");
        MongoCollection<Document> dictionaryCollection = database.getCollection("dictionary");
        MongoCollection<Document> assignmentCollection = database.getCollection("dictionary_assignment");

        UserRepository userRepository = new MongoUserRepository(userCollection);
        DictionaryRepository dictionaryRepository = new MongoDictionaryRepository(dictionaryCollection, assignmentCollection);

        // Service layer setup
        userService = new UserService(userRepository);
        dictionaryService = new DictionaryService(dictionaryRepository);
        statsService = new StatsTrackingService(userRepository);

        initializeDefaultDictionaries();
    }

    private static void initializeMediaProcessing() {
        try {
            MediaInitializer.initialize().get();
        } catch (Exception e) {
            logger.warn("Media processing initialization failed: {}", e.getMessage());
            logger.warn("Video processing features will be unavailable, but bot will continue normally.");
        }
    }

    private static void startTerminalInterface() {
        logger.info("==================================================");
        logger.info("MediaRoulette application started successfully!");
        logger.info("Bot Status: {}", (bot != null ? "Initialized" : "Failed to initialize"));
        logger.info("Database Status: {}", (database != null ? "Connected" : "Failed to connect"));
        logger.info("Media Processing: {}", (MediaInitializer.isInitialized() ? "Ready (FFmpeg available)" : "Limited (FFmpeg unavailable)"));
        logger.info("Maintenance mode: {}", localConfig.getMaintenanceMode());
        logger.info("==================================================");

        terminal = new TerminalInterface();
        Thread terminalThread = new Thread(terminal::start, "Terminal-Interface");
        terminalThread.setDaemon(true);
        terminalThread.start();
    }

    public static boolean containsKey(Set<DotenvEntry> entries, String key) {
        return entries.stream().anyMatch(entry -> entry.getKey().equals(key));
    }

    public static String getEnv(String key) {
        String value = env.get(key);
        return value != null ? value : System.getenv(key);
    }

    private static void initializeDefaultDictionaries() {
        try {
            if (dictionaryService.getAccessibleDictionaries("system").isEmpty()) {
                var basicDict = dictionaryService.createDictionary("Basic Dictionary", "Default words for general use", "system");
                basicDict.setDefault(true);
                basicDict.setPublic(true);

                var basicWords = java.util.Arrays.asList("funny", "cute", "happy", "random", "cool", "awesome", "nice", "good", "best", "amazing");
                basicDict.addWords(basicWords);

                logger.info("Default dictionary initialized with {} words", basicWords.size());
            }
        } catch (Exception e) {
            logger.error("Failed to initialize default dictionaries: {}", e.getMessage());
        }
    }

    public static void shutdown() {
        logger.info("Initiating graceful shutdown...");

        if (terminal != null) {
            try {
                terminal.stop();
            } catch (Exception e) {
                logger.error("Error stopping terminal: {}", e.getMessage());
            }
        }

        if (bot != null) {
            try {
                Bot.getShardManager().shutdown();
                Bot.executor.shutdown();
                if (!Bot.executor.awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    Bot.executor.shutdownNow();
                }
                logger.info("Bot shutdown complete.");
            } catch (Exception e) {
                logger.error("Error during bot shutdown: {}", e.getMessage());
                Bot.executor.shutdownNow();
            }
        }

        if (statsService != null) {
            try {
                statsService.shutdown();
                logger.info("Stats service shutdown complete.");
            } catch (Exception e) {
                logger.error("Error during stats shutdown: {}", e.getMessage());
            }
        }

        try {
            GiveawayManager.shutdown();
            logger.info("Giveaway service shutdown complete.");
        } catch (Exception e) {
            logger.error("Error during giveaway shutdown: {}", e.getMessage());
        }

        try {
            me.hash.mediaroulette.bot.commands.images.getRandomImage.shutdownCleanupExecutor();
            logger.info("Image command cleanup executor shutdown complete.");
        } catch (Exception e) {
            logger.error("Error during image command cleanup: {}", e.getMessage());
        }

        try {
            logger.info("Content provider cleanup complete.");
        } catch (Exception e) {
            logger.error("Error during content provider cleanup: {}", e.getMessage());
        }

        try {
            MediaInitializer.shutdown();
            logger.info("Media processing cleanup complete.");
        } catch (Exception e) {
            logger.error("Error during media processing cleanup: {}", e.getMessage());
        }

        if (database != null) {
            logger.info("Database connections closed.");
        }

        logger.info("Shutdown complete. Goodbye!");

        new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.exit(0);
        }).start();
    }
}