package me.hash.mediaroulette.plugins;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

public class PluginManager {
    private final Map<String, Plugin> plugins = new ConcurrentHashMap<>();
    private final Map<String, PluginClassLoader> classLoaders = new ConcurrentHashMap<>();
    private final Logger logger = Logger.getLogger(PluginManager.class.getName());

    public void loadPlugins(File pluginDirectory) {
        if (!pluginDirectory.exists() || !pluginDirectory.isDirectory()) {
            logger.warning("Plugin directory does not exist: " + pluginDirectory);
            return;
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
            } catch (Exception e) {
                logger.severe("Failed to load plugin description from " + file.getName() + ": " + e.getMessage());
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
                        logger.severe("Failed to load plugin " + name + ": " + e.getMessage());
                        loaded.add(name); // Mark as processed to avoid infinite loop
                    }
                }
            }

            if (!progress) {
                logger.severe("Circular dependency detected or missing dependencies!");
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

    private void loadPlugin(File file, PluginDescriptionFile description) throws Exception {
        PluginClassLoader classLoader = new PluginClassLoader(
                new URL[]{file.toURI().toURL()},
                this.getClass().getClassLoader()
        );

        Class<?> pluginClass = classLoader.loadClass(description.getMain());
        Plugin plugin = (Plugin) pluginClass.getDeclaredConstructor().newInstance();

        plugin.setDescription(description);
        plugin.setClassLoader(classLoader);

        plugins.put(description.getName(), plugin);
        classLoaders.put(description.getName(), classLoader);

        plugin.onLoad();
        logger.info("Loaded plugin: " + description.getName() + " v" + description.getVersion());
    }

    public void enablePlugins() {
        for (Plugin plugin : plugins.values()) {
            try {
                plugin.setEnabled(true);
                logger.info("Enabled plugin: " + plugin.getName());
            } catch (Exception e) {
                logger.severe("Failed to enable plugin " + plugin.getName() + ": " + e.getMessage());
            }
        }
    }

    public void disablePlugins() {
        for (Plugin plugin : plugins.values()) {
            try {
                plugin.setEnabled(false);
                logger.info("Disabled plugin: " + plugin.getName());
            } catch (Exception e) {
                logger.severe("Failed to disable plugin " + plugin.getName() + ": " + e.getMessage());
            }
        }
    }

    public Plugin getPlugin(String name) {
        return plugins.get(name);
    }

    public Collection<Plugin> getPlugins() {
        return Collections.unmodifiableCollection(plugins.values());
    }

    public List<Plugin> getLoadedPlugins() {
        return new ArrayList<>(plugins.values());
    }

    public void reloadPlugins() {
        disablePlugins();
        
        for (PluginClassLoader classLoader : classLoaders.values()) {
            try {
                classLoader.close();
            } catch (Exception e) {
                logger.severe("Failed to close class loader: " + e.getMessage());
            }
        }
        
        plugins.clear();
        classLoaders.clear();
        
        File pluginDir = new File("plugins");
        loadPlugins(pluginDir);
        enablePlugins();
    }
}