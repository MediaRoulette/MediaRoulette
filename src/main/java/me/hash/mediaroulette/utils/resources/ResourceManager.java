package me.hash.mediaroulette.utils.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.hash.mediaroulette.utils.download.DownloadManager;
import me.hash.mediaroulette.utils.download.DownloadManager.DownloadRequest;
import me.hash.mediaroulette.utils.terminal.ProgressBar;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static me.hash.mediaroulette.utils.terminal.TerminalColors.*;

/**
 * Manages external resources - downloads from GitHub at startup.
 * Resources include images, fonts, config files, and locales.
 */
public class ResourceManager {
    private static final Logger logger = LoggerFactory.getLogger(ResourceManager.class);
    
    private static final String GITHUB_BASE = 
        "https://raw.githubusercontent.com/MediaRoulette/MediaRoulette/main/resources/";
    private static final Path RESOURCES_DIR = Path.of("resources");
    private static final Path MANIFEST_FILE = RESOURCES_DIR.resolve("manifest.json");
    
    private static volatile ResourceManager instance;
    private final ObjectMapper objectMapper;
    private ResourceManifest manifest;
    private boolean initialized = false;
    
    public enum ResourceType {
        IMAGE("images"),
        FONT("fonts"),
        CONFIG("config"),
        LOCALE("locales"),
        DATA("data");
        
        private final String folder;
        
        ResourceType(String folder) {
            this.folder = folder;
        }
        
        public String getFolder() {
            return folder;
        }
    }
    
    private ResourceManager() {
        this.objectMapper = new ObjectMapper();
    }
    
    public static ResourceManager getInstance() {
        if (instance == null) {
            synchronized (ResourceManager.class) {
                if (instance == null) {
                    instance = new ResourceManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * Initialize resources - downloads missing resources from GitHub.
     */
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                ensureDirectories();
                downloadAndParseManifest();
                downloadMissingResources();
                initialized = true;
            } catch (Exception e) {
                logger.error("Resource initialization failed: {}", e.getMessage());
                logger.warn("Continuing with limited functionality - some resources may be unavailable");
            }
        });
    }
    
    /**
     * Check if resources are initialized.
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    private void ensureDirectories() throws IOException {
        Files.createDirectories(RESOURCES_DIR);
        for (ResourceType type : ResourceType.values()) {
            Files.createDirectories(RESOURCES_DIR.resolve(type.getFolder()));
        }
    }
    
    private void downloadAndParseManifest() {
        String manifestUrl = GITHUB_BASE + "manifest.json";
        
        try {
            Request request = new Request.Builder()
                    .url(manifestUrl)
                    .header("User-Agent", "MediaRoulette/1.0")
                    .build();
            
            try (Response response = DownloadManager.getHttpClient().newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    manifest = objectMapper.readValue(json, ResourceManifest.class);
                    
                    // Save manifest locally
                    Files.writeString(MANIFEST_FILE, json);
                    logger.debug("Downloaded resource manifest v{}", manifest.getVersion());
                } else {
                    loadLocalManifest();
                }
            }
        } catch (Exception e) {
            logger.warn("Could not download manifest: {}", e.getMessage());
            loadLocalManifest();
        }
    }
    
    private void loadLocalManifest() {
        try {
            if (Files.exists(MANIFEST_FILE)) {
                String json = Files.readString(MANIFEST_FILE);
                manifest = objectMapper.readValue(json, ResourceManifest.class);
                logger.debug("Using cached manifest v{}", manifest.getVersion());
            }
        } catch (Exception e) {
            logger.warn("Could not load cached manifest: {}", e.getMessage());
        }
    }
    
    private void downloadMissingResources() {
        if (manifest == null || manifest.getResources() == null) {
            logger.warn("No manifest available - cannot download resources");
            return;
        }
        
        List<ResourceManifest.ResourceEntry> missing = manifest.getResourceList().stream()
                .filter(this::needsDownload)
                .toList();
        
        if (missing.isEmpty()) {
            System.out.println(BRIGHT_GREEN + "✓ " + RESET + "Resources" + DIM + " — All " + 
                    manifest.getResources().size() + " files up to date" + RESET);
            return;
        }
        
        System.out.println(CYAN + "↓ " + RESET + "Downloading " + BOLD + missing.size() + 
                RESET + " resources...\n");
        
        int downloaded = 0;
        int failed = 0;
        
        for (ResourceManifest.ResourceEntry entry : missing) {
            try {
                downloadResource(entry);
                downloaded++;
            } catch (Exception e) {
                logger.error("Failed to download {}: {}", entry.getPath(), e.getMessage());
                failed++;
            }
        }
        
        if (failed > 0) {
            System.out.println(YELLOW + "⚠ " + RESET + "Resources" + DIM + " — " + 
                    downloaded + " downloaded, " + failed + " failed" + RESET);
        }
    }
    
    private boolean needsDownload(ResourceManifest.ResourceEntry entry) {
        Path localPath = RESOURCES_DIR.resolve(entry.getPath());
        
        if (!Files.exists(localPath)) {
            return true;
        }
        
        // Verify file integrity if hash is available
        if (entry.getSha256() != null && !entry.getSha256().isBlank()) {
            try {
                String localHash = computeSha256(localPath);
                return !localHash.equalsIgnoreCase(entry.getSha256());
            } catch (Exception e) {
                return true;
            }
        }
        
        // Verify size if available
        if (entry.getSize() > 0) {
            try {
                return Files.size(localPath) != entry.getSize();
            } catch (IOException e) {
                return true;
            }
        }
        
        return false;
    }
    
    private void downloadResource(ResourceManifest.ResourceEntry entry) throws IOException {
        String url = GITHUB_BASE + entry.getPath();
        Path target = RESOURCES_DIR.resolve(entry.getPath());
        
        // Extract filename for display
        String displayName = target.getFileName().toString();
        if (displayName.length() > 30) {
            displayName = displayName.substring(0, 27) + "...";
        }
        
        DownloadManager.download(DownloadRequest.builder()
                .url(url)
                .target(target)
                .taskName(displayName)
                .showProgress(entry.getSize() > 10240) // Show progress for files > 10KB
                .style(ProgressBar.Style.DOWNLOAD)
                .build());
    }
    
    private String computeSha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    
    /**
     * Get resource as InputStream.
     * Downloads on-demand if not available locally.
     */
    public InputStream getResourceAsStream(ResourceType type, String filename) throws IOException {
        Path path = RESOURCES_DIR.resolve(type.getFolder()).resolve(filename);
        
        if (!Files.exists(path)) {
            // Try to download on-demand
            String url = GITHUB_BASE + type.getFolder() + "/" + filename;
            try {
                DownloadManager.download(DownloadRequest.builder()
                        .url(url)
                        .target(path)
                        .taskName(filename)
                        .showProgress(true)
                        .build());
            } catch (Exception e) {
                throw new IOException("Resource not found and download failed: " + filename, e);
            }
        }
        
        return Files.newInputStream(path);
    }
    
    /**
     * Get resource as InputStream, falling back to classpath if not found externally.
     */
    public InputStream getResourceAsStreamWithFallback(ResourceType type, String filename, String classpathFallback) 
            throws IOException {
        Path path = RESOURCES_DIR.resolve(type.getFolder()).resolve(filename);
        
        if (Files.exists(path)) {
            return Files.newInputStream(path);
        }
        
        // Try classpath fallback
        InputStream classpathStream = getClass().getClassLoader().getResourceAsStream(classpathFallback);
        if (classpathStream != null) {
            return classpathStream;
        }
        
        // Try downloading
        return getResourceAsStream(type, filename);
    }
    
    /**
     * Get the path to a resource file.
     */
    public Path getResourcePath(ResourceType type, String filename) {
        return RESOURCES_DIR.resolve(type.getFolder()).resolve(filename);
    }
    
    /**
     * Check if a resource exists locally.
     */
    public boolean resourceExists(ResourceType type, String filename) {
        return Files.exists(getResourcePath(type, filename));
    }
    
    /**
     * Get the base resources directory.
     */
    public Path getResourcesDirectory() {
        return RESOURCES_DIR;
    }
    
    /**
     * Get the count of available resources.
     */
    public int getResourceCount() {
        if (manifest == null || manifest.getResources() == null) {
            return 0;
        }
        return manifest.getResources().size();
    }
}
