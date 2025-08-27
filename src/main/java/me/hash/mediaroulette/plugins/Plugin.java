package me.hash.mediaroulette.plugins;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Plugin {
    private PluginDescriptionFile description;
    private ClassLoader classLoader;
    private Logger logger;
    private boolean enabled = false;

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
}