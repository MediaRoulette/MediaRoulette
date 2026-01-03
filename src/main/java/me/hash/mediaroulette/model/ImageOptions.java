package me.hash.mediaroulette.model;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ImageOptions {
    private static final Logger logger = LoggerFactory.getLogger(ImageOptions.class);
    private static final Path EXTERNAL_CONFIG = Path.of("resources", "config", "randomWeightValues.json");
    
    private final String imageType;
    private boolean enabled;
    private double chance;

    public ImageOptions(String imageType, boolean enabled, double chance) {
        this.imageType = imageType;
        this.enabled = enabled;
        this.chance = chance;
    }

    public String getImageType() {
        return imageType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getChance() {
        return chance;
    }

    public void setChance(double chance) {
        this.chance = chance;
    }

    public static List<ImageOptions> getDefaultOptions() {
        InputStream is = null;
        try {
            // Try external resources folder first
            if (Files.exists(EXTERNAL_CONFIG)) {
                is = Files.newInputStream(EXTERNAL_CONFIG);
            } else {
                // Fallback to classpath
                is = ImageOptions.class.getResourceAsStream("/config/randomWeightValues.json");
            }
            
            if (is == null) {
                logger.warn("randomWeightValues.json not found, returning empty options");
                return new ArrayList<>();
            }
            
            try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name())) {
                String json = scanner.useDelimiter("\\A").next();
                JSONArray array = new JSONArray(json);
                List<ImageOptions> options = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.getJSONObject(i);
                    String imageType = object.getString("imageType");
                    boolean enabled = object.getBoolean("enabled");
                    double chance = object.getDouble("chance");
                    options.add(new ImageOptions(imageType, enabled, chance));
                }
                return options;
            }
        } catch (Exception e) {
            logger.error("Failed to load randomWeightValues.json: {}", e.getMessage());
            return new ArrayList<>();
        } finally {
            if (is != null) {
                try { is.close(); } catch (Exception ignored) {}
            }
        }
    }
}