package me.hash.mediaroulette.utils.media;

import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility class for downloading and managing FFmpeg binaries across different operating systems.
 * Automatically detects the system architecture and downloads the appropriate FFmpeg version.
 */
public class FFmpegDownloader {
    private static final Logger logger = LoggerFactory.getLogger(FFmpegDownloader.class);

    private static final String FFMPEG_DIR = getJarDirectory() + File.separator + "ffmpeg";
    private static final String FFMPEG_EXECUTABLE_NAME = getExecutableName();
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    
    // FFmpeg download URLs for different platforms and architectures
    private static final String WINDOWS_X64_URL    = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl-shared.zip";
    private static final String WINDOWS_ARM64_URL  = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-winarm64-gpl-shared.zip";
    private static final String LINUX_X64_URL      = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz";
    private static final String LINUX_ARM64_URL    = "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linuxarm64-gpl.tar.xz";
    private static final String MACOS_X64_URL      = "https://evermeet.cx/ffmpeg/getrelease/zip";
    private static final String MACOS_ARM64_URL    = "https://evermeet.cx/ffmpeg/getrelease/arm64/zip";
    
    private static Path ffmpegPath;
    private static boolean isDownloaded = false;
    
    /**
     * Gets the path to the FFmpeg executable, downloading it if necessary
     */
    public static CompletableFuture<Path> getFFmpegPath() {
        if (isDownloaded && ffmpegPath != null && Files.exists(ffmpegPath)) {
            return CompletableFuture.completedFuture(ffmpegPath);
        }
        
        // Prefer system-installed ffmpeg if available
        Path systemFfmpeg = findFFmpegInSystemPath();
        if (systemFfmpeg != null) {
            ffmpegPath = systemFfmpeg;
            isDownloaded = false;
            return CompletableFuture.completedFuture(systemFfmpeg);
        }
        
        return downloadFFmpeg().thenApply(path -> {
            ffmpegPath = path;
            isDownloaded = true;
            return path;
        });
    }
    
    /**
     * Gets the path to the FFprobe executable
     */
    public static CompletableFuture<Path> getFFprobePath() {
        return getFFmpegPath().thenApply(ffmpegPath -> {
            String ffprobeExecutable = System.getProperty("os.name").toLowerCase().contains("windows") ? "ffprobe.exe" : "ffprobe";
            Path ffmpegDir = ffmpegPath != null ? ffmpegPath.getParent() : Paths.get(FFMPEG_DIR);
            Path candidate = ffmpegDir != null ? ffmpegDir.resolve(ffprobeExecutable) : Paths.get(ffprobeExecutable);
            if (Files.exists(candidate)) {
                return candidate;
            }
            // Fallback to PATH search
            Path systemProbe = findInSystemPath(ffprobeExecutable);
            return Objects.requireNonNullElseGet(systemProbe, () -> Paths.get(FFMPEG_DIR).resolve(ffprobeExecutable));
        });
    }
    
    /**
     * Checks if FFprobe is available
     */
    public static CompletableFuture<Boolean> isFFprobeAvailable() {
        return getFFprobePath().thenApply(Files::exists);
    }
    
    /**
     * Downloads FFmpeg for the current operating system
     */
    public static CompletableFuture<Path> downloadFFmpeg() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Detecting system information...");
                SystemInfo systemInfo = detectSystem();
                logger.info("Detected system: {} {}", systemInfo.os, systemInfo.arch);
                
                // Create ffmpeg directory if it doesn't exist
                Path ffmpegDir = Paths.get(FFMPEG_DIR);
                Files.createDirectories(ffmpegDir);
                logger.info("FFmpeg directory: {}", ffmpegDir.toAbsolutePath());
                
                // Check if FFmpeg already exists
                Path existingPath = findExistingFFmpeg(ffmpegDir);
                if (existingPath != null) {
                    logger.info("FFmpeg already exists at: {}", existingPath);
                    return existingPath;
                }
                
                String downloadUrl = getDownloadUrl(systemInfo);
                logger.info("Downloading FFmpeg from: {}", downloadUrl);
                
                // Download the archive
                Path downloadedFile = downloadFile(downloadUrl, ffmpegDir);
                logger.info("Downloaded to: {}", downloadedFile);
                
                // Extract the archive
                Path extractedPath = extractArchive(downloadedFile, ffmpegDir);
                logger.info("Extracted FFmpeg to: {}", extractedPath);
                
                // Clean up downloaded archive
                Files.deleteIfExists(downloadedFile);
                
                // Make executable on Unix systems
                if (systemInfo.os != OperatingSystem.WINDOWS) {
                    makeExecutable(extractedPath);
                    
                    // Also make ffprobe executable if it exists
                    Path ffprobePath = extractedPath.getParent().resolve(getProbeExecutableName());
                    if (Files.exists(ffprobePath)) {
                        makeExecutable(ffprobePath);
                    }
                }
                
                return extractedPath;
                
            } catch (Exception e) {
                logger.error("Failed to download FFmpeg: {}", e.getMessage(), e);
            }
            return null;
        });
    }
    
    /**
     * Checks if FFmpeg is available (either downloaded or in system PATH)
     */
    public static boolean isFFmpegAvailable() {
        // Check if we have a downloaded version
        if (isDownloaded && ffmpegPath != null && Files.exists(ffmpegPath)) {
            return true;
        }
        
        // Check if FFmpeg is in system PATH
        Path systemFfmpeg = findFFmpegInSystemPath();
        return systemFfmpeg != null && Files.isExecutable(systemFfmpeg);
    }
    
    /**
     * Gets the FFmpeg executable name for the current OS
     */
    private static String getExecutableName() {
        return System.getProperty("os.name").toLowerCase().contains("windows") ? "ffmpeg.exe" : "ffmpeg";
    }
    
    /**
     * Gets the directory where the JAR file is located
     */
    private static String getJarDirectory() {
        try {
            URI jarUri = FFmpegDownloader.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI();
            
            if (jarUri.getScheme().equals("file")) {
                File jarFile = new File(jarUri);
                String parentDir = jarFile.getParent();
                if (parentDir != null) {
                    return parentDir;
                }
            }
        } catch (Exception e) {
            logger.error("Could not determine JAR directory: {}", e.getMessage());
        }
        
        // Fallback to current working directory
        return System.getProperty("user.dir");
    }
    
    /**
     * Detects the current operating system and architecture
     */
    private static SystemInfo detectSystem() {
        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();
        
        OperatingSystem os;
        Architecture arch;
        
        // Detect OS
        if (osName.contains("windows")) {
            os = OperatingSystem.WINDOWS;
        } else if (osName.contains("linux")) {
            os = OperatingSystem.LINUX;
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            os = OperatingSystem.MACOS;
        } else {
            throw new UnsupportedOperationException("Unsupported operating system: " + osName);
        }
        
        // Detect Architecture
        if (osArch.contains("aarch64") || osArch.contains("arm64")) {
            arch = Architecture.ARM64;
        } else if (osArch.contains("x86_64") || osArch.contains("amd64")) {
            arch = Architecture.X64;
        } else if (osArch.contains("arm")) {
            arch = Architecture.ARM64; // Assume ARM64 for ARM variants
        } else {
            logger.warn("Unknown architecture: {}, defaulting to x64", osArch);
            arch = Architecture.X64;
        }
        
        return new SystemInfo(os, arch);
    }
    
    /**
     * Gets the download URL for the specified system
     */
    private static String getDownloadUrl(SystemInfo systemInfo) {
        return switch (systemInfo.os) {
            case WINDOWS -> systemInfo.arch == Architecture.ARM64 ? WINDOWS_ARM64_URL : WINDOWS_X64_URL;
            case LINUX -> systemInfo.arch == Architecture.ARM64 ? LINUX_ARM64_URL : LINUX_X64_URL;
            case MACOS -> systemInfo.arch == Architecture.ARM64 ? MACOS_ARM64_URL : MACOS_X64_URL;
        };
    }
    
    /**
     * Downloads a file from the given URL with progress tracking
     */
    private static Path downloadFile(String url, Path targetDir) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "MediaRoulette-Bot/1.0")
                .build();
        
        String fileName = getFileNameFromUrl(url);
        Path targetFile = targetDir.resolve(fileName);

        logger.info("Requesting: {}", url);
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            logger.info("Response: HTTP {} {}", response.code(), response.message());
            if (response.request().url().toString().equals(url)) {
                logger.info("Direct download (no redirect)");
            } else {
                logger.info("Redirected to: {}", response.request().url());
            }
            
            if (!response.isSuccessful()) {
                throw new IOException("Failed to download file: HTTP " + response.code() + " " + response.message() + 
                                    "\nFinal URL: " + response.request().url());
            }
            
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Response body is null");
            }
            
            long contentLength = body.contentLength();
            
            try (InputStream inputStream = body.byteStream();
                 FileOutputStream outputStream = new FileOutputStream(targetFile.toFile())) {
                
                byte[] buffer = new byte[8192];
                long totalBytes = 0;
                int bytesRead;
                long lastProgressUpdate = 0;

                logger.info("Downloading FFmpeg... (Size: {})", formatBytes(contentLength));
                printProgressBar(0, contentLength);
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                    
                    // Update progress bar every 100KB or when complete
                    if (totalBytes - lastProgressUpdate >= 102400 || totalBytes == contentLength) {
                        printProgressBar(totalBytes, contentLength);
                        lastProgressUpdate = totalBytes;
                    }
                }

                logger.info("\n✅ Download complete: {}", formatBytes(totalBytes));
            }
        }
        
        return targetFile;
    }
    
    /**
     * Prints a progress bar for download progress
     */
    private static void printProgressBar(long downloaded, long total) {
        if (total <= 0) {
            System.out.print("\rDownloading... " + formatBytes(downloaded));
            return;
        }
        
        int barLength = 40;
        double progress = (double) downloaded / total;
        int filledLength = (int) (barLength * progress);
        
        StringBuilder bar = new StringBuilder();
        bar.append("\r[");
        
        for (int i = 0; i < barLength; i++) {
            if (i < filledLength) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }
        
        bar.append("] ");
        bar.append(String.format("%.1f%%", progress * 100));
        bar.append(" (").append(formatBytes(downloaded));
        bar.append("/").append(formatBytes(total));
        bar.append(")");
        
        System.out.print(bar);
    }
    
    /**
     * Formats bytes into human-readable format
     */
    @NotNull
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Extracts the downloaded archive and finds the FFmpeg executable
     */
    private static Path extractArchive(Path archiveFile, Path targetDir) throws IOException {
        String fileName = archiveFile.getFileName().toString().toLowerCase();
        
        if (fileName.endsWith(".zip")) {
            return extractZip(archiveFile, targetDir);
        } else if (fileName.endsWith(".tar.xz")) {
            return extractTarXz(archiveFile, targetDir);
        } else {
            throw new UnsupportedOperationException("Unsupported archive format: " + fileName);
        }
    }
    
    /**
     * Extracts a ZIP archive and finds the FFmpeg executable
     */
    private static Path extractZip(Path zipFile, Path targetDir) throws IOException {
        logger.info("Extracting ZIP archive: {}", zipFile.getFileName());
        
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            Path ffmpegPath = null;
            int extractedFiles = 0;
            
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                
                String entryName = entry.getName();
                String fileName = Paths.get(entryName).getFileName().toString();
                
                // Extract FFmpeg executable
                if (fileName.equals(FFMPEG_EXECUTABLE_NAME)) {
                    ffmpegPath = targetDir.resolve(FFMPEG_EXECUTABLE_NAME);
                    extractFile(zis, ffmpegPath);
                    extractedFiles++;
                }
                // Also extract ffprobe if available
                else if (fileName.equals(getProbeExecutableName())) {
                    Path ffprobePath = targetDir.resolve(getProbeExecutableName());
                    extractFile(zis, ffprobePath);
                    extractedFiles++;
                }
                // Extract any DLL files on Windows
                else if (fileName.endsWith(".dll") && System.getProperty("os.name").toLowerCase().contains("windows")) {
                    Path dllPath = targetDir.resolve(fileName);
                    extractFile(zis, dllPath);
                    extractedFiles++;

                }

                logger.info("✅ Extracted: {}", fileName);

                zis.closeEntry();
            }

            logger.info("Extracted {} files from ZIP archive", extractedFiles);
            
            if (ffmpegPath == null) {
                throw new IOException("FFmpeg executable not found in ZIP archive");
            }
            
            return ffmpegPath;
        }
    }
    
    /**
     * Extracts a single file from ZIP stream
     */
    private static void extractFile(ZipInputStream zis, Path outputPath) throws IOException {
        Files.createDirectories(outputPath.getParent());
        
        try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = zis.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
        }
    }
    
    /**
     * Gets the FFprobe executable name for the current OS
     */
    private static String getProbeExecutableName() {
        return System.getProperty("os.name").toLowerCase().contains("windows") ? "ffprobe.exe" : "ffprobe";
    }
    
    /**
     * Extracts a TAR.XZ archive (Linux) - Enhanced version
     */
    private static Path extractTarXz(Path tarXzFile, Path targetDir) throws IOException {
        logger.info("Extracting TAR.XZ archive: {}", tarXzFile.getFileName());

        // Try multiple extraction methods
        Path foundFfmpeg = null;

        // Method 1: Try system tar command
        try {
            logger.info("Attempting extraction with system tar command...");
            ProcessBuilder pb = new ProcessBuilder("tar", "-xf", tarXzFile.toString(), "-C", targetDir.toString());
            Process process = pb.start();

            // Capture stderr for debugging
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.debug("tar: {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                logger.info("✅ TAR extraction successful");
                foundFfmpeg = findFFmpegInDirectory(targetDir);
            } else {
                logger.error("tar command failed with exit code: {}", exitCode);
            }
        } catch (Exception e) {
            logger.error("System tar extraction failed: {}", e.getMessage());
        }

        // Method 2: Try with different tar options if not found yet
        if (foundFfmpeg == null) {
            try {
                logger.info("Attempting extraction with alternative tar options...");
                ProcessBuilder pb = new ProcessBuilder("tar", "-xJf", tarXzFile.toString(), "-C", targetDir.toString());
                Process process = pb.start();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    foundFfmpeg = findFFmpegInDirectory(targetDir);
                }
            } catch (Exception e) {
                logger.error("Alternative tar extraction failed: {}", e.getMessage());
            }
        }

        if (foundFfmpeg != null) {
            // Place binaries in root targetDir and cleanup extracted directories
            Path destFfmpeg = targetDir.resolve(FFMPEG_EXECUTABLE_NAME);
            try {
                if (!Files.exists(destFfmpeg) || !Files.isSameFile(foundFfmpeg, destFfmpeg)) {
                    Files.copy(foundFfmpeg, destFfmpeg, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException copyEx) {
                logger.error("Failed to copy ffmpeg binary: {}", copyEx.getMessage());
                throw copyEx;
            }

            // Try to copy ffprobe alongside if present next to ffmpeg
            Path probeSrc = foundFfmpeg.getParent().resolve(getProbeExecutableName());
            Path destProbe = targetDir.resolve(getProbeExecutableName());
            if (Files.exists(probeSrc) && Files.isRegularFile(probeSrc)) {
                try {
                    Files.copy(probeSrc, destProbe, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException copyEx) {
                    logger.warn("Failed to copy ffprobe binary: {}", copyEx.getMessage());
                }
            }

            // Ensure executables are marked executable on Unix
            makeExecutable(destFfmpeg);
            if (Files.exists(destProbe)) {
                makeExecutable(destProbe);
            }

            // Cleanup: remove extracted directories, keep only binaries in target root
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetDir)) {
                for (Path p : stream) {
                    String name = p.getFileName().toString();
                    if (name.equals(FFMPEG_EXECUTABLE_NAME) || name.equals(getProbeExecutableName())) {
                        continue;
                    }
                    // Don't touch the original tar file here; it's deleted by caller
                    if (Files.isDirectory(p)) {
                        deleteRecursively(p);
                    }
                }
            } catch (IOException cleanupEx) {
                logger.warn("Cleanup after extraction encountered issues: {}", cleanupEx.getMessage());
            }

            logger.info("✅ Found FFmpeg at: {}", destFfmpeg);
            return destFfmpeg;
        }

        // Method 3: Manual extraction instructions
        logger.warn("Automatic extraction failed. Manual extraction required:");
        logger.warn("1. Extract {} to {}", tarXzFile, targetDir);
        logger.warn("2. Ensure ffmpeg executable is in {}", targetDir);
        logger.warn("3. Make sure ffmpeg has execute permissions (chmod +x ffmpeg)");

        throw new IOException("Failed to extract TAR.XZ archive automatically. " +
                "Please extract manually or install tar/xz-utils.");
    }
    
    /**
     * Finds an existing FFmpeg executable in the given directory
     */
    private static Path findExistingFFmpeg(Path directory) {
        Path directPath = directory.resolve(FFMPEG_EXECUTABLE_NAME);
        if (Files.exists(directPath) && Files.isRegularFile(directPath)) {
            return directPath;
        }
        
        return findFFmpegInDirectory(directory);
    }
    
    /**
     * Recursively searches for FFmpeg executable in directory
     */
    private static Path findFFmpegInDirectory(Path directory) {
        try {
            return Files.walk(directory)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(FFMPEG_EXECUTABLE_NAME))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
    
    /**
     * Makes a file executable on Unix systems
     */
    private static void makeExecutable(Path file) {
        if (System.getProperty("os.name").toLowerCase().contains("windows")) {
            return; // No chmod needed on Windows
        }
        try {
            // Try to set POSIX permissions first
            try {
                java.nio.file.attribute.PosixFileAttributeView view = java.nio.file.Files.getFileAttributeView(file, java.nio.file.attribute.PosixFileAttributeView.class);
                if (view != null) {
                    java.util.Set<java.nio.file.attribute.PosixFilePermission> perms = new java.util.HashSet<>();
                    perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_READ);
                    perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
                    perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
                    perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_READ);
                    perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE);
                    perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_READ);
                    perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE);
                    java.nio.file.Files.setPosixFilePermissions(file, perms);
                    return;
                }
            } catch (Throwable ignored) {
                // Fall back to chmod below
            }

            // Fallback: use chmod command
            ProcessBuilder pb = new ProcessBuilder("chmod", "+x", file.toString());
            Process process = pb.start();
            process.waitFor();
        } catch (Exception e) {
            logger.error("Failed to make file executable: {}", e.getMessage());
        }
    }
    
    /**
     * Extracts filename from URL
     */
    private static String getFileNameFromUrl(String url) {
        if (url.contains("evermeet.cx")) {
            return "ffmpeg-macos.zip";
        }
        
        String[] parts = url.split("/");
        String fileName = parts[parts.length - 1];
        
        // Handle query parameters
        if (fileName.contains("?")) {
            fileName = fileName.substring(0, fileName.indexOf("?"));
        }
        
        return fileName;
    }
    
    /**
     * Operating system enumeration
     */
    // Locate an executable in the system PATH in a cross-platform way
    private static Path findInSystemPath(String executableName) {
        // Try OS-native commands first
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
        String[] command = isWindows ? new String[]{"where", executableName} : new String[]{"which", executableName};
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = r.readLine();
                if (line != null && !line.isBlank()) {
                    Path p = Paths.get(line.trim());
                    if (Files.exists(p) && Files.isRegularFile(p)) {
                        return p;
                    }
                }
            }
        } catch (Exception ignored) { }

        // Fallback: manually walk PATH
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] dirs = pathEnv.split(File.pathSeparator);
            for (String dir : dirs) {
                Path candidate = Paths.get(dir).resolve(executableName);
                if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static Path findFFmpegInSystemPath() {
        return findInSystemPath(FFMPEG_EXECUTABLE_NAME);
    }

    private enum OperatingSystem {
        WINDOWS, LINUX, MACOS
    }
    
    /**
     * Architecture enumeration
     */
    private enum Architecture {
        X64, ARM64
    }

    /**
     * System information container
     */
    private record SystemInfo(OperatingSystem os, Architecture arch) {

        @NotNull
        @Override
        public String toString() {
            return os + "_" + arch;
        }
    }
    
    /**
     * Gets the version of the downloaded/available FFmpeg
     */
    public static CompletableFuture<String> getFFmpegVersion() {
        return getFFmpegPath().thenCompose(path -> CompletableFuture.supplyAsync(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(path.toString(), "-version");
                Process process = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String firstLine = reader.readLine();
                    if (firstLine != null && firstLine.contains("ffmpeg version")) {
                        return firstLine;
                    }
                }

                return "Unknown version";
            } catch (Exception e) {
                return "Error getting version: " + e.getMessage();
            }
        }));
    }
    
    /**
     * Cleans up downloaded FFmpeg files
     */
    public static void cleanup() {
        try {
            Path ffmpegDir = Paths.get(FFMPEG_DIR);
            if (Files.exists(ffmpegDir)) {
                logger.info("Cleaning up FFmpeg directory: {}", ffmpegDir);
                Files.walk(ffmpegDir)
                        .sorted(Comparator.reverseOrder()) // Delete files before directories
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                                logger.info("Deleted: {}", path.getFileName());
                            } catch (IOException e) {
                                logger.error("Failed to delete: {} - {}", path, e.getMessage());
                            }
                        });
                logger.info("✅ FFmpeg cleanup complete");
            }
            isDownloaded = false;
            ffmpegPath = null;
        } catch (IOException e) {
            logger.error("Failed to cleanup FFmpeg directory: {}", e.getMessage());
        }
    }

   private static void deleteRecursively(Path dir) throws IOException {
       if (!Files.exists(dir)) return;
       Files.walk(dir)
               .sorted(Comparator.reverseOrder())
               .forEach(p -> {
                   try {
                       Files.deleteIfExists(p);
                   } catch (IOException ignored) {}
               });
   }
    
    /**
     * Gets information about the current FFmpeg installation
     */
    public static void printInstallationInfo() {
        logger.info("=== FFmpeg Installation Info ===");
        logger.info("FFmpeg Directory: {}", FFMPEG_DIR);
        logger.info("Executable Name: {}", FFMPEG_EXECUTABLE_NAME);
        logger.info("Is Downloaded: {}", isDownloaded);
        logger.info("Is Available: {}", isFFmpegAvailable());
        
        if (ffmpegPath != null) {
            logger.info("FFmpeg Path: {}", ffmpegPath);
            logger.info("File Exists: {}", Files.exists(ffmpegPath));
            if (Files.exists(ffmpegPath)) {
                try {
                    logger.info("File Size: {}", formatBytes(Files.size(ffmpegPath)));
                    logger.info("Is Executable: {}", Files.isExecutable(ffmpegPath));
                } catch (IOException e) {
                    logger.error("Error reading file info: {}", e.getMessage());
                }
            }
        }
        
        // Check for ffprobe
        Path ffprobeDir = Paths.get(FFMPEG_DIR);
        Path ffprobePath = ffprobeDir.resolve(getProbeExecutableName());
        logger.info("FFprobe Available: {}", Files.exists(ffprobePath));
        if (Files.exists(ffprobePath)) {
            try {
                logger.info("FFprobe Size: {}", formatBytes(Files.size(ffprobePath)));
                logger.info("FFprobe Executable: {}", Files.isExecutable(ffprobePath));
            } catch (IOException e) {
                logger.error("Error reading FFprobe info: {}", e.getMessage());
            }
        }

        logger.info("================================");
    }

    /**
     * Shuts down the OkHttpClient resources
     */
    public static void shutdown() {
        try {
            HTTP_CLIENT.dispatcher().executorService().shutdown();
            HTTP_CLIENT.connectionPool().evictAll();
            // If cache was used, close it here
        } catch (Exception e) {
            logger.error("Error shutting down FFmpegDownloader: {}", e.getMessage());
        }
    }
}