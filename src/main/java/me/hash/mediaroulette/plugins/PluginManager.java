package me.hash.mediaroulette.plugins;

import me.hash.mediaroulette.plugins.Images.ImageSourcePlugin;
import me.hash.mediaroulette.plugins.Images.ImageSourceProvider;
import me.hash.mediaroulette.plugins.Images.ImageSourceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class PluginManager {
    private final Map<String, Plugin> plugins = new ConcurrentHashMap<>();
    private final Map<String, PluginClassLoader> classLoaders = new ConcurrentHashMap<>();
    private final Map<String, File> pluginJars = new ConcurrentHashMap<>();
    private final File pluginDirectory;
    private final File dataDirectory;
    private static final Logger logger = LoggerFactory.getLogger(PluginManager.class);

    public PluginManager() {
        this.pluginDirectory = new File("plugins");
        this.dataDirectory = new File("plugins");
    }

    public PluginManager(File pluginDirectory, File dataDirectory) {
        this.pluginDirectory = pluginDirectory;
        this.dataDirectory = dataDirectory;
    }

    public void loadPlugins() {
        loadPlugins(this.pluginDirectory);
    }

    public void loadPlugins(File pluginDirectory) {
        if (!pluginDirectory.exists() || !pluginDirectory.isDirectory()) {
            logger.warn("Plugin directory does not exist: {}", pluginDirectory);
            return;
        }

        // Ensure data directory exists
        if (!dataDirectory.exists()) {
            dataDirectory.mkdirs();
        }

        File[] files = pluginDirectory.listFiles((dir, name) -> name.endsWith(".jar"));
        if (files == null) return;

        // First pass: Load all plugin descriptions
        Map<String, PluginDescriptionFile> descriptions = new HashMap<>();
        Map<String, File> pluginFiles = new HashMap<>();

        for (File file : files) {
            try {
                PluginDescriptionFile desc = getPluginDescription(file);
                descriptions.put(desc.getName(), desc);
                pluginFiles.put(desc.getName(), file);
                pluginJars.put(desc.getName(), file);
            } catch (Exception e) {
                logger.error("Failed to load plugin description from {}: {}", file.getName(), e.getMessage(), e);
            }
        }

        // Second pass: Load plugins in dependency order
        Set<String> loaded = new HashSet<>();
        while (loaded.size() < descriptions.size()) {
            boolean progress = false;

            for (Map.Entry<String, PluginDescriptionFile> entry : descriptions.entrySet()) {
                String name = entry.getKey();
                PluginDescriptionFile desc = entry.getValue();

                if (loaded.contains(name)) continue;

                // Check if dependencies are loaded
                boolean canLoad = true;
                if (desc.getDepend() != null) {
                    for (String dep : desc.getDepend()) {
                        if (!loaded.contains(dep)) {
                            canLoad = false;
                            break;
                        }
                    }
                }

                if (canLoad) {
                    try {
                        loadPlugin(pluginFiles.get(name), desc);
                        loaded.add(name);
                        progress = true;
                    } catch (Exception e) {
                        logger.error("Failed to load plugin {}: {}", name, e.getMessage());
                        loaded.add(name); // Mark as processed to avoid infinite loop
                    }
                }
            }

            if (!progress) {
                logger.error("Circular dependency detected or missing dependencies!");
                break;
            }
        }
    }

    private PluginDescriptionFile getPluginDescription(File file) throws Exception {
        try (JarFile jar = new JarFile(file)) {
            JarEntry entry = jar.getJarEntry("plugin.yml");
            if (entry == null) {
                throw new Exception("plugin.yml not found");
            }

            try (InputStream stream = jar.getInputStream(entry)) {
                return new PluginDescriptionFile(stream);
            }
        }
    }

    public void addOrUpdatePluginJar(File jarFile, boolean enableAfterLoad) throws Exception {
        // Load description
        PluginDescriptionFile desc = getPluginDescription(jarFile);
        String name = desc.getName();

        // If plugin exists, unload it first
        if (plugins.containsKey(name)) {
            logger.info("Updating plugin {}...", name);
            disablePlugin(name);
            PluginClassLoader oldCl = classLoaders.remove(name);
            plugins.remove(name);
            if (oldCl != null) {
                try { oldCl.close(); } catch (Exception ignored) {}
            }
        } else {
            logger.info("Adding new plugin {}...", name);
        }

        // Load new jar
        loadPlugin(jarFile, desc);
        pluginJars.put(name, jarFile);
        if (enableAfterLoad) {
            enablePlugin(name);
        }
    }

    private void loadPlugin(File file, PluginDescriptionFile description) throws Exception {
        // Create plugin data folder
        File pluginDataFolder = new File(dataDirectory, description.getName());
        if (!pluginDataFolder.exists()) {
            pluginDataFolder.mkdirs();
            logger.debug("Created data folder for plugin: {}", description.getName());
        }

        PluginClassLoader classLoader = new PluginClassLoader(
                new URL[]{file.toURI().toURL()},
                this.getClass().getClassLoader()
        );

        Class<?> pluginClass = classLoader.loadClass(description.getMain());
        Plugin plugin = (Plugin) pluginClass.getDeclaredConstructor().newInstance();

        plugin.setDescription(description);
        plugin.setClassLoader(classLoader);
        plugin.setDataFolder(pluginDataFolder);

        plugins.put(description.getName(), plugin);
        classLoaders.put(description.getName(), classLoader);

        plugin.onLoad();
        logger.info("Loaded plugin: {} v{}", description.getName(), description.getVersion());
    }

    public void enablePlugins() {
        for (Plugin plugin : plugins.values()) {
            try {
                plugin.setEnabled(true);
                
                // Register image sources if the plugin implements ImageSourcePlugin
                if (plugin instanceof ImageSourcePlugin imageSourcePlugin) {
                    registerImageSources(imageSourcePlugin);
                }
                
                logger.info("Enabled plugin: {}", plugin.getName());
            } catch (Exception e) {
                logger.error("Failed to enable plugin {}: {}", plugin.getName(), e.getMessage(), e);
            }
        }
    }

    public void disablePlugins() {
        for (Plugin plugin : plugins.values()) {
            try {
                // Unregister image sources if the plugin implements ImageSourcePlugin
                if (plugin instanceof ImageSourcePlugin imageSourcePlugin) {
                    unregisterImageSources(imageSourcePlugin);
                }
                
                plugin.setEnabled(false);
                logger.info("Disabled plugin: {}", plugin.getName());
            } catch (Exception e) {
                logger.error("Failed to disable plugin {}: {}", plugin.getName(), e.getMessage());
            }
        }
    }

    public boolean enablePlugin(String name) {
        Plugin plugin = findPlugin(name);
        if (plugin == null) return false;
        if (plugin.isEnabled()) return true; // already enabled
        try {
            plugin.setEnabled(true);
            if (plugin instanceof ImageSourcePlugin imageSourcePlugin) {
                registerImageSources(imageSourcePlugin);
            }
            return true;
        } catch (Exception e) {
            logger.error("Failed to enable plugin {}: {}", plugin.getName(), e.getMessage(), e);
            return false;
        }
    }

    public boolean disablePlugin(String name) {
        Plugin plugin = findPlugin(name);
        if (plugin == null) return false;
        if (!plugin.isEnabled()) return true; // already disabled
        try {
            if (plugin instanceof ImageSourcePlugin imageSourcePlugin) {
                unregisterImageSources(imageSourcePlugin);
            }
            plugin.setEnabled(false);
            return true;
        } catch (Exception e) {
            logger.error("Failed to disable plugin {}: {}", plugin.getName(), e.getMessage(), e);
            return false;
        }
    }

    public Plugin getPlugin(String name) {
        return plugins.get(name);
    }

    public Plugin findPlugin(String name) {
        if (name == null) return null;
        for (Plugin p : plugins.values()) {
            if (p.getName().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    public Collection<Plugin> getPlugins() {
        return Collections.unmodifiableCollection(plugins.values());
    }

    public List<Plugin> getLoadedPlugins() {
        return new ArrayList<>(plugins.values());
    }

    public List<String> getPluginNames() {
        return plugins.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    public void reloadPlugins() {
        reloadPlugins(false);
    }

    public void reloadPlugins(boolean preserveDisabled) {
        disablePlugins();

        // Clear all plugin-provided image sources
        ImageSourceRegistry.getInstance().clearPluginProviders();

        for (PluginClassLoader classLoader : classLoaders.values()) {
            try {
                classLoader.close();
            } catch (Exception e) {
                logger.error("Failed to close class loader: {}", e.getMessage());
            }
        }

        // Snapshot disabled set if requested
        Set<String> disabled = new HashSet<>();
        if (preserveDisabled) {
            for (Plugin p : plugins.values()) {
                if (!p.isEnabled()) disabled.add(p.getName());
            }
        }

        plugins.clear();
        classLoaders.clear();

        loadPlugins(pluginDirectory);
        
        if (preserveDisabled && !disabled.isEmpty()) {
            // Enable all then disable preserved ones to avoid dependency mismatches during registration
            enablePlugins();
            for (String name : disabled) {
                disablePlugin(name);
            }
        } else {
            enablePlugins();
        }
    }

    public File getPluginDirectory() {
        return pluginDirectory;
    }

    public File getDataDirectory() {
        return dataDirectory;
    }

    /**
     * Register image sources from a plugin that implements ImageSourcePlugin
     * @param plugin The plugin to register image sources from
     */
    private void registerImageSources(ImageSourcePlugin plugin) {
        try {
            List<ImageSourceProvider> providers = plugin.getImageSourceProviders();
            if (providers != null && !providers.isEmpty()) {
                ImageSourceRegistry registry = ImageSourceRegistry.getInstance();
                int registered = 0;
                
                for (ImageSourceProvider provider : providers) {
                    if (registry.registerProvider(provider)) {
                        registered++;
                    }
                }
                
                if (registered > 0) {
                    plugin.onImageSourcesRegistered();
                    logger.info("Registered {} image source(s) from plugin: {}", registered, 
                              ((Plugin) plugin).getName());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to register image sources from plugin {}: {}", 
                        ((Plugin) plugin).getName(), e.getMessage(), e);
        }
    }

    /**
     * Unregister image sources from a plugin that implements ImageSourcePlugin
     * @param plugin The plugin to unregister image sources from
     */
    private void unregisterImageSources(ImageSourcePlugin plugin) {
        try {
            List<ImageSourceProvider> providers = plugin.getImageSourceProviders();
            if (providers != null && !providers.isEmpty()) {
                ImageSourceRegistry registry = ImageSourceRegistry.getInstance();
                int unregistered = 0;
                
                for (ImageSourceProvider provider : providers) {
                    if (registry.unregisterProvider(provider.getName())) {
                        unregistered++;
                    }
                }
                
                if (unregistered > 0) {
                    plugin.onImageSourcesUnregistered();
                    logger.info("Unregistered {} image source(s) from plugin: {}", unregistered, 
                              ((Plugin) plugin).getName());
                }
            }
        } catch (Exception e) {
            logger.error("Failed to unregister image sources from plugin {}: {}", 
                        ((Plugin) plugin).getName(), e.getMessage(), e);
        }
    }
}
