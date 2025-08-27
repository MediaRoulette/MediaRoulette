package me.hash.mediaroulette;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
import org.bson.Document;

public class Main {
    // Static services and components
    public static Dotenv env;
    public static LocalConfig localConfig;
    public static Database database;
    public static Bot bot;
    public static UserService userService;
    public static DictionaryService dictionaryService;
    public static StatsTrackingService statsService;
    public static TerminalInterface terminal;
    public static final long startTime = System.currentTimeMillis();
    private static PluginManager pluginManager;

    public static void main(String[] args) throws Exception {
        // Setup shutdown hook first for proper cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutdown signal received...");
            shutdown();
        }));

        // Initialize environment and configuration
        initializeEnvironment();
        
        // Initialize database and services
        initializeServices();
        
        // Initialize media processing
        initializeMediaProcessing();
        
        // Initialize bot and related components
        bot = new Bot(getEnv("DISCORD_TOKEN"));

        pluginManager = new PluginManager();

        // Load plugins from plugins directory
        File pluginDir = new File("plugins");
        if (!pluginDir.exists()) {
            pluginDir.mkdirs();
        }

        pluginManager.loadPlugins(pluginDir);
        pluginManager.enablePlugins();

        System.out.println("Application started with " + pluginManager.getPlugins().size() + " plugins");


        configureBot();
        me.hash.mediaroulette.utils.GiveawayManager.initialize();
        
        // Start terminal interface
        startTerminalInterface();
        
        System.out.println("MediaRoulette started successfully! Press Ctrl+C to stop.");
    }

    private static void initializeEnvironment() throws Exception {
        // Load environment variables from resources
        try (InputStream inputStream = Main.class.getClassLoader().getResourceAsStream(".env")) {
            if (inputStream == null) throw new RuntimeException(".env file not found in resources");
            
            Path tempFile = Files.createTempFile("dotenv", ".env");
            tempFile.toFile().deleteOnExit();
            Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            
            env = Dotenv.configure()
                    .directory(tempFile.getParent().toString())
                    .filename(tempFile.getFileName().toString())
                    .load();
            
            Files.delete(tempFile);
        }
        
        localConfig = LocalConfig.getInstance();
    }

    private static void initializeServices() {
        // Database setup
        database = new Database(getEnv("MONGODB_CONNECTION"), "MediaRoulette");
        
        // Repository setup
        MongoCollection<Document> userCollection = database.getCollection("user");
        MongoCollection<Document> dictionaryCollection = database.getCollection("dictionary");
        MongoCollection<Document> assignmentCollection = database.getCollection("dictionary_assignment");
        
        UserRepository userRepository = new MongoUserRepository(userCollection);
        DictionaryRepository dictionaryRepository = new MongoDictionaryRepository(dictionaryCollection, assignmentCollection);
        
        // Service layer setup
        userService = new UserService(userRepository);
        dictionaryService = new DictionaryService(dictionaryRepository);
        statsService = new StatsTrackingService(userRepository);
        
        // Initialize default dictionaries
        initializeDefaultDictionaries();
    }

    private static void initializeMediaProcessing() {
        try {
            MediaInitializer.initialize().get();
        } catch (Exception e) {
            System.err.println("⚠️ Media processing initialization failed: " + e.getMessage());
            System.err.println("   Video processing features will be unavailable, but bot will continue normally.");
        }
    }

    private static void startTerminalInterface() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("MediaRoulette application started successfully!");
        System.out.println("Bot Status: " + (bot != null ? "Initialized" : "Failed to initialize"));
        System.out.println("Database Status: " + (database != null ? "Connected" : "Failed to connect"));
        System.out.println("Media Processing: " + (MediaInitializer.isInitialized() ? "Ready (FFmpeg available)" : "Limited (FFmpeg unavailable)"));
        System.out.println("Maintenance mode: " + localConfig.getMaintenanceMode());
        System.out.println("=".repeat(50));

        terminal = new TerminalInterface();
        Thread terminalThread = new Thread(terminal::start, "Terminal-Interface");
        terminalThread.setDaemon(true); // Allow JVM to exit when main thread ends
        terminalThread.start();
    }

    private static void configureBot() {
        Set<DotenvEntry> entries = env.entries();
        Map<String, String> configMap = new HashMap<>();
        configMap.put("DISCORD_NSFW_WEBHOOK", "NSFW_WEBHOOK");
        configMap.put("DISCORD_SAFE_WEBHOOK", "SAFE_WEBHOOK");
        configMap.put("TENOR_API", "TENOR");
        configMap.put("TMDB_API", "TMDB");

        // Configure bot settings based on available environment variables
        for (Map.Entry<String, String> entry : configMap.entrySet()) {
            String envKey = entry.getKey();
            String configKey = entry.getValue();
            if (containsKey(entries, envKey)) {
                System.out.println(envKey + " Loaded!");
                Bot.config.set(configKey, Bot.config.getOrDefault(configKey, true, Boolean.class));
            } else {
                System.out.println(envKey + " Not found in .env");
                Bot.config.set(configKey, false);
            }
        }

        // Check for service credentials
        checkCredentials(entries, "REDDIT", "REDDIT_CLIENT_ID", "REDDIT_CLIENT_SECRET", "REDDIT_USERNAME", "REDDIT_PASSWORD");
        checkCredentials(entries, "GOOGLE", "GOOGLE_API_KEY", "GOOGLE_CX");
    }

    private static void checkCredentials(Set<DotenvEntry> entries, String configKey, String... keys) {
        boolean allKeysPresent = checkCredentialsBoolean(entries, configKey, keys);
        if (allKeysPresent) {
            System.out.println(configKey + " Loaded!");
            Bot.config.set(configKey, Bot.config.getOrDefault(configKey, true, Boolean.class));
        } else {
            System.out.println(configKey + " credentials are not set in .env");
            Bot.config.set(configKey, false);
        }
    }

    public static boolean checkCredentialsBoolean(Set<DotenvEntry> entries, String configKey, String... keys) {
        for (String key : keys) {
            if (!containsKey(entries, key)) {
                return false;
            }
        }
        return true;
    }

    public static boolean containsKey(Set<DotenvEntry> entries, String key) {
        return entries.stream().anyMatch(entry -> entry.getKey().equals(key));
    }

    public static String getEnv(String key) {
        return env.get(key);
    }

    private static void initializeDefaultDictionaries() {
        try {
            if (dictionaryService.getAccessibleDictionaries("system").isEmpty()) {
                var basicDict = dictionaryService.createDictionary("Basic Dictionary", "Default words for general use", "system");
                basicDict.setDefault(true);
                basicDict.setPublic(true);
                
                var basicWords = java.util.Arrays.asList("funny", "cute", "happy", "random", "cool", "awesome", "nice", "good", "best", "amazing");
                basicDict.addWords(basicWords);
                
                System.out.println("Default dictionary initialized with " + basicWords.size() + " words");
            }
        } catch (Exception e) {
            System.err.println("Failed to initialize default dictionaries: " + e.getMessage());
        }
    }

    public static void shutdown() {
        System.out.println("Initiating graceful shutdown...");

        // Stop terminal interface
        if (terminal != null) {
            terminal.stop();
            // Give the terminal thread a moment to clean up
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Shutdown services
        if (statsService != null) {
            statsService.shutdown();
            System.out.println("Stats service shutdown complete.");
        }

        // Shutdown bot
        if (bot != null) {
            System.out.println("Bot shutdown complete.");
        }

        // Cleanup media processing
        try {
            MediaInitializer.shutdown();
            System.out.println("Media processing cleanup complete.");
        } catch (Exception e) {
            System.err.println("Error during media processing cleanup: " + e.getMessage());
        }

        // Close database connections
        if (database != null) {
            System.out.println("Database connections closed.");
        }

        System.out.println("Shutdown complete. Goodbye!");
        System.exit(0);
    }
}