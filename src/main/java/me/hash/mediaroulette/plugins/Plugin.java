package me.hash.mediaroulette.plugins;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public abstract class Plugin {
    private PluginDescriptionFile description;
    private ClassLoader classLoader;
    private Logger logger;
    private boolean enabled = false;
    private File dataFolder;

    public Plugin() {}

    /**
     * Called when the plugin is loaded
     */
    public void onLoad() {}

    /**
     * Called when the plugin is enabled
     */
    public void onEnable() {}

    /**
     * Called when the plugin is disabled
     */
    public void onDisable() {}

    /**
     * Saves a resource from the plugin jar to the data folder
     * @param resourcePath Path to the resource in the jar
     * @param replace Whether to replace existing files
     * @return true if the resource was saved successfully
     */
    public boolean saveResource(String resourcePath, boolean replace) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            throw new IllegalArgumentException("ResourcePath cannot be null or empty");
        }

        resourcePath = resourcePath.replace('\\', '/');
        InputStream in = getResource(resourcePath);
        if (in == null) {
            throw new IllegalArgumentException("The embedded resource '" + resourcePath + "' cannot be found in " + getName());
        }

        File outFile = new File(dataFolder, resourcePath);
        int lastIndex = resourcePath.lastIndexOf('/');
        File outDir = new File(dataFolder, resourcePath.substring(0, Math.max(lastIndex, 0)));

        if (!outDir.exists()) {
            outDir.mkdirs();
        }

        try {
            if (!outFile.exists() || replace) {
                Files.copy(in, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return true;
            } else {
                logger.warn("Could not save {} to {} because {} already exists.", outFile.getName(), outFile, outFile.getName());
            }
        } catch (IOException ex) {
            logger.error("Could not save {} to {}", outFile.getName(), outFile, ex);
        } finally {
            try {
                in.close();
            } catch (IOException ex) {
                logger.error("Error closing resource stream", ex);
            }
        }
        return false;
    }

    /**
     * Saves the default config.yml from the plugin jar to the data folder
     */
    public void saveDefaultConfig() {
        saveResource("config.yml", false);
    }

    /**
     * Gets a resource from the plugin jar
     * @param filename Name of the resource
     * @return InputStream of the resource, or null if not found
     */
    public InputStream getResource(String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("Filename cannot be null");
        }

        try {
            return classLoader.getResourceAsStream(filename);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Creates a new file in the plugin's data folder
     * @param name Name of the file
     * @return File object
     */
    public File getDataFile(String name) {
        return new File(dataFolder, name);
    }

    /**
     * Gets the plugin's configuration file (config.yml)
     * @return File object for config.yml
     */
    public File getConfigFile() {
        return new File(dataFolder, "config.yml");
    }

    // Getters and setters
    public final PluginDescriptionFile getDescription() {
        return description;
    }

    public final void setDescription(PluginDescriptionFile description) {
        this.description = description;
    }

    public final ClassLoader getPluginClassLoader() {
        return classLoader;
    }

    public final void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public final Logger getLogger() {
        if (logger == null) {
            logger = LoggerFactory.getLogger(description.getName());
        }
        return logger;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public final void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            if (enabled) {
                onEnable();
            } else {
                onDisable();
            }
        }
    }

    public final String getName() {
        return description.getName();
    }

    public final String getVersion() {
        return description.getVersion();
    }

    public final File getDataFolder() {
        return dataFolder;
    }

    public final void setDataFolder(File dataFolder) {
        this.dataFolder = dataFolder;
    }
}