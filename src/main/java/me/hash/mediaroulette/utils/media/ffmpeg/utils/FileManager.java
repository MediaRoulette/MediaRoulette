package me.hash.mediaroulette.utils.media.ffmpeg.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class for file operations
 */
public class FileManager {
    private static final Logger logger = LoggerFactory.getLogger(FileManager.class);

    private final String tempDirectory;

    public FileManager(String tempDirectory) {
        this.tempDirectory = tempDirectory;
    }

    /**
     * Creates the temp directory if it doesn't exist
     */
    public void ensureTempDirectoryExists() throws IOException {
        Path tempDir = Paths.get(tempDirectory);
        Files.createDirectories(tempDir);
    }

    /**
     * Generates a unique temporary file path
     */
    public Path generateTempFilePath(String prefix, String extension) {
        String fileName = prefix + "_" + System.currentTimeMillis() + "." + extension;
        return Paths.get(tempDirectory).resolve(fileName);
    }

    /**
     * Checks if a path exists
     */
    public boolean pathExists(Path path) {
        return Files.exists(path);
    }

    /**
     * Deletes a file if it exists
     */
    public void deleteIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            logger.error("Failed to delete file: {} - {}", path, e.getMessage(), e);
        }
    }

    /**
     * Cleans up old temporary files (older than 1 hour)
     */
    public void cleanupTempFiles() {
        Path tempDir = Paths.get(tempDirectory);
        if (!Files.exists(tempDir)) {
            return;
        }

        try (var stream = Files.walk(tempDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(this::isOldFile)
                    .forEach(this::deleteIfExists);
        } catch (IOException e) {
            logger.error("Failed to cleanup temp files: {}", e.getMessage(), e);
        }
    }

    private boolean isOldFile(Path path) {
        try {
            long fileTime = Files.getLastModifiedTime(path).toMillis();
            long currentTime = System.currentTimeMillis();
            return (currentTime - fileTime) > (60 * 60 * 1000); // 1 hour
        } catch (IOException e) {
            return true; // If we can't read the time, consider it old
        }
    }
}