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
import me.hash.mediaroulette.config.LocalConfig;
import me.hash.mediaroulette.database.Database;
import me.hash.mediaroulette.service.GiveawayManager;
import me.hash.mediaroulette.utils.media.MediaInitializer;
import me.hash.mediaroulette.utils.resources.ResourceManager;
import me.hash.mediaroulette.utils.startup.StartupManager;
import me.hash.mediaroulette.utils.terminal.TerminalInterface;
import me.hash.mediaroulette.utils.user.UserService;
import me.hash.mediaroulette.utils.vault.VaultConfig;
import me.hash.mediaroulette.utils.vault.VaultSecretManager;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 3;
    private static final int SHUTDOWN_DELAY_MS = 500;

    // Environment and Configuration
    private static final Dotenv env = initializeEnvironment();
    private static LocalConfig localConfig;
    private static VaultSecretManager vaultSecretManager;

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
        localConfig = LocalConfig.getInstance();
        
        // Use StartupManager for clean, orchestrated startup
        StartupManager startup = new StartupManager()
                .addTask("Configuration", Main::initializeConfig)
                .addTask("Vault", Main::initializeVaultTask)
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

    // ==================== Vault Setup ====================

    private static String initializeVaultTask() {
        try {
            VaultConfig vaultConfig = VaultConfig.load();
            VaultSecretManager.initialize(vaultConfig);
            vaultSecretManager = VaultSecretManager.getInstance();
            
            return vaultSecretManager.isVaultEnabled() ? "Enabled" : "Using .env";
        } catch (Exception e) {
            logger.debug("Vault init failed: {}", e.getMessage());
            VaultSecretManager.initialize(new VaultConfig.Builder().enabled(false).build());
            vaultSecretManager = VaultSecretManager.getInstance();
            return "Disabled";
        }
    }
    
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

    // ==================== Infrastructure Setup ====================

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

                // logger.info("Default dictionary initialized with {} words", defaultWords.size());
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

    private static void initializeMediaProcessing() {
        try {
            MediaInitializer.initialize().get();
        } catch (Exception e) {
            logger.debug("Media processing init failed: {}", e.getMessage());
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
    
    private static void initializeBot() {
        String discordToken = getEnv("DISCORD_TOKEN");
        if (discordToken != null && !discordToken.isEmpty()) {
            bot = new Bot(discordToken);
        }
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
    
    private static void initializePlugins() {
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
    }

    private static String initializeGiveawaysTask() {
        GiveawayManager.initialize();
        return "Ready";
    }

    private static void initializeGiveaways() {
        GiveawayManager.initialize();
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

        safeShutdown("Giveaway Service", GiveawayManager::shutdown);

        safeShutdown("Image Interaction Service",
                me.hash.mediaroulette.service.ImageInteractionService.getInstance()::shutdown
        );
        
        safeShutdown("Image Memory Manager", 
                me.hash.mediaroulette.utils.media.ImageMemoryManager.getInstance()::shutdown
        );
    }

    private static void shutdownMediaProcessing() {
        safeShutdown("Media Processing", MediaInitializer::shutdown);
        
        safeShutdown("Media Container Cleanup", 
                me.hash.mediaroulette.bot.MediaContainerManager::cleanup
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
     * Priority: Vault -> .env file -> System environment variables
     */
    public static String getEnv(String key) {
        // Try Vault first if available
        if (vaultSecretManager != null && vaultSecretManager.isVaultEnabled()) {
            return vaultSecretManager.getSecret(key);
        }
        
        // Fallback to .env file or system environment
        String value = env.get(key);
        return value != null ? value : System.getenv(key);
    }
    
    /**
     * Get Vault secret manager instance.
     */
    public static VaultSecretManager getVaultSecretManager() {
        return vaultSecretManager;
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