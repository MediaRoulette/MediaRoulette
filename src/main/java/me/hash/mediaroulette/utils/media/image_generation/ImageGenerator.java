package me.hash.mediaroulette.utils.media.image_generation;

import me.hash.mediaroulette.utils.media.image_generation.components.TextComponent;
import me.hash.mediaroulette.utils.media.image_generation.components.TextParser;
import me.hash.mediaroulette.utils.media.image_generation.ImageRenderer;
import me.hash.mediaroulette.utils.media.image_generation.Theme;
import me.hash.mediaroulette.utils.media.image_generation.ThemeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public class ImageGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ImageGenerator.class);

    private static final int DEFAULT_WIDTH = 500;
    private static final int DEFAULT_HEIGHT = 300;
    private static final double DEFAULT_SCALE_FACTOR = 2.5;

    private final ThemeManager themeManager;
    private final int baseWidth;
    private final int baseHeight;
    private final double scaleFactor;

    public ImageGenerator() {
        this(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_SCALE_FACTOR);
    }

    public ImageGenerator(int baseWidth, int baseHeight, double scaleFactor) {
        this.themeManager = ThemeManager.getInstance();
        this.baseWidth = baseWidth;
        this.baseHeight = baseHeight;
        this.scaleFactor = scaleFactor;
    }

    /**
     * Generates an image with the given text using the default theme.
     */
    public byte[] generateImage(String text) {
        return generateImage(text, "default");
    }

    /**
     * Generates an image with the given text using the specified theme.
     */
    public byte[] generateImage(String text, String themeName) {
        try {
            Theme theme = themeManager.getTheme(themeName);
            List<TextComponent> components = TextParser.parseText(text);

            ImageRenderer renderer = new ImageRenderer(theme, baseWidth, baseHeight, scaleFactor);
            BufferedImage image = renderer.render(components);

            return convertToByteArray(image);
        } catch (Exception e) {
            logger.error("Image generation failed: {}", e.getMessage());
            return new byte[0];
        }
    }

    /**
     * Gets all available theme names.
     */
    public String[] getAvailableThemes() {
        return themeManager.getAllThemes().keySet().toArray(new String[0]);
    }

    /**
     * Gets a specific theme by name.
     */
    public Theme getTheme(String themeName) {
        return themeManager.getTheme(themeName);
    }

    private byte[] convertToByteArray(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            baos.flush();
            return baos.toByteArray();
        } finally {
            // Help GC by disposing image resources
            if (image != null) {
                image.flush();
            }
        }
    }
}