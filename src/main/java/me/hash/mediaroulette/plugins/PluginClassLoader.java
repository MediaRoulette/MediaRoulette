package me.hash.mediaroulette.plugins;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Memory-cached class loader for plugins.
 * 
 * This class loader reads all class bytecode from a JAR file into memory during
 * construction. This allows the JAR file to be replaced on disk without causing
 * ClassNotFoundException errors for classes that are already loaded.
 * 
 * Key features:
 * - All class bytecode is cached in memory on construction
 * - Classes are defined from cached bytes, not from JAR file
 * - JAR file can be safely replaced/deleted after plugin is loaded
 * - Parent-first delegation for shared classes (Plugin API, etc.)
 */
public class PluginClassLoader extends URLClassLoader {
    private static final Logger logger = LoggerFactory.getLogger(PluginClassLoader.class);
    
    /**
     * Packages that MUST be loaded from parent classloader to avoid LinkageError.
     * These are shared between the main application and plugins.
     */
    private static final String[] PARENT_ONLY_PACKAGES = {
        // Logging frameworks - must be shared to avoid classloader conflicts
        "org.slf4j.",
        "ch.qos.logback.",
        "org.apache.logging.log4j.",
        // Plugin API - plugins extend these classes
        "me.hash.mediaroulette.plugins.",
        // JDA - shared bot framework
        "net.dv8tion.jda.",
        // MongoDB driver - shared database classes
        "com.mongodb.",
        "org.bson.",
        // Java standard library extensions
        "javax.",
        "jakarta."
    };
    
    /** Cache of class bytecode: fully qualified class name -> bytecode */
    private final Map<String, byte[]> classCache = new ConcurrentHashMap<>();
    
    /** Cache of resource bytes: resource path -> content */
    private final Map<String, byte[]> resourceCache = new ConcurrentHashMap<>();
    
    /** Name of the plugin (for logging) */
    private final String pluginName;
    
    /**
     * Create a new plugin class loader and cache all classes from the JAR.
     * 
     * @param urls URLs to the plugin JAR file (single URL expected)
     * @param parent Parent class loader for delegation
     */
    public PluginClassLoader(URL[] urls, ClassLoader parent) {
        this(urls, parent, null);
    }
    
    /**
     * Create a new plugin class loader and cache all classes from the JAR.
     * 
     * @param urls URLs to the plugin JAR file (single URL expected)
     * @param parent Parent class loader for delegation
     * @param pluginName Name of the plugin for logging
     */
    public PluginClassLoader(URL[] urls, ClassLoader parent, String pluginName) {
        super(urls, parent);
        this.pluginName = pluginName != null ? pluginName : "unknown";
        
        // Load all classes and resources into memory
        for (URL url : urls) {
            try {
                cacheJarContents(url);
            } catch (IOException e) {
                logger.error("Failed to cache JAR contents for plugin {}: {}", this.pluginName, e.getMessage());
            }
        }
        
        logger.debug("Plugin {} class loader initialized with {} classes, {} resources cached",
                this.pluginName, classCache.size(), resourceCache.size());
    }
    
    /**
     * Cache all classes and resources from a JAR file.
     */
    private void cacheJarContents(URL jarUrl) throws IOException {
        String path = jarUrl.getPath();
        // Handle Windows paths - remove leading slash if present
        if (path.startsWith("/") && path.length() > 2 && path.charAt(2) == ':') {
            path = path.substring(1);
        }
        
        try (JarFile jarFile = new JarFile(path)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                
                if (entry.isDirectory()) {
                    continue;
                }
                
                try (InputStream is = jarFile.getInputStream(entry)) {
                    byte[] bytes = readAllBytes(is);
                    
                    if (name.endsWith(".class")) {
                        // Convert path to class name: com/example/MyClass.class -> com.example.MyClass
                        String className = name.substring(0, name.length() - 6).replace('/', '.');
                        classCache.put(className, bytes);
                    } else {
                        // Cache as resource
                        resourceCache.put(name, bytes);
                    }
                }
            }
        }
    }
    
    /**
     * Read all bytes from an input stream.
     */
    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    /**
     * Check if a class should always be loaded from the parent classloader.
     */
    private boolean isParentOnlyClass(String name) {
        for (String prefix : PARENT_ONLY_PACKAGES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            // Check if already loaded
            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass != null) {
                return loadedClass;
            }
            
            // CRITICAL: Force parent-first for shared packages to avoid LinkageError.
            // Even if the plugin bundles these classes, we MUST use the parent's version
            // to ensure type compatibility between plugin and main application.
            if (isParentOnlyClass(name)) {
                try {
                    Class<?> clazz = getParent().loadClass(name);
                    if (resolve) {
                        resolveClass(clazz);
                    }
                    return clazz;
                } catch (ClassNotFoundException e) {
                    // Parent doesn't have it. 
                    // If we have it, that's fine (plugin providing its own dependency).
                    // Only warn if we DON'T have it either, as that implies a missing dependency.
                    if (!classCache.containsKey(name)) {
                        // ResourceBundle lookups often probe for classes that don't exist (e.g. LocalStrings).
                        // Lowering to DEBUG to avoid log noise.
                        logger.debug("Parent-only class {} not found in parent classloader and not in plugin cache", name);
                    } else {
                        // Debug log that we're falling back to plugin version
                        logger.debug("Likely parent-only class {} not found in parent, using plugin version", name);
                    }
                }
            }
            
            // Parent-first for non-plugin classes (Java standard, our API classes, libraries)
            // This ensures plugins use the same versions of shared dependencies
            if (!classCache.containsKey(name)) {
                try {
                    Class<?> clazz = getParent().loadClass(name);
                    if (resolve) {
                        resolveClass(clazz);
                    }
                    return clazz;
                } catch (ClassNotFoundException e) {
                    // Not found in parent or cache
                    throw e;
                }
            }
            
            // Load from our cache
            Class<?> clazz = findClass(name);
            if (resolve) {
                resolveClass(clazz);
            }
            return clazz;
        }
    }
    
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classCache.get(name);
        
        if (bytes == null) {
            throw new ClassNotFoundException("Class not found in plugin cache: " + name);
        }
        
        // Define the class from cached bytes
        return defineClass(name, bytes, 0, bytes.length);
    }
    
    @Override
    public InputStream getResourceAsStream(String name) {
        // Check our cache first
        byte[] bytes = resourceCache.get(name);
        if (bytes != null) {
            return new java.io.ByteArrayInputStream(bytes);
        }
        
        // Fall back to parent
        return super.getResourceAsStream(name);
    }
    
    @Override
    public URL getResource(String name) {
        // Check if we have it cached (can't return URL for cached resource, but parent can)
        if (resourceCache.containsKey(name)) {
            // Return the original URL from super - still works for metadata
            return super.getResource(name);
        }
        return super.getResource(name);
    }
    
    /**
     * Get the number of cached classes.
     */
    public int getCachedClassCount() {
        return classCache.size();
    }
    
    /**
     * Get the number of cached resources.
     */
    public int getCachedResourceCount() {
        return resourceCache.size();
    }
    
    /**
     * Clear all cached data (called during cleanup).
     */
    public void clearCache() {
        classCache.clear();
        resourceCache.clear();
    }
    
    @Override
    public void close() throws IOException {
        clearCache();
        super.close();
    }
}