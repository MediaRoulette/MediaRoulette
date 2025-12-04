package me.hash.mediaroulette.utils.media.image_generation;

import me.hash.mediaroulette.utils.media.image_generation.components.TextComponent;
import me.hash.mediaroulette.utils.media.image_generation.components.TextWrapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ImageRenderer {
    private static final Logger logger = LoggerFactory.getLogger(ImageRenderer.class);

    private final Theme theme;
    private final int width;
    private final int height;
    private final double scaleFactor;

    public ImageRenderer(Theme theme, int baseWidth, int baseHeight, double scaleFactor) {
        this.theme = theme;
        this.width = (int) (baseWidth * scaleFactor);
        this.height = (int) (baseHeight * scaleFactor);
        this.scaleFactor = scaleFactor;
    }

    public BufferedImage render(List<TextComponent> components) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = null;

        try {
            g2d = image.createGraphics();
            setupRenderingHints(g2d);
            renderBackground(g2d);
            renderOverlay(g2d);
            renderTextBox(g2d, components);
            return image;
        } catch (Exception e) {
            // Clean up on error
            if (image != null) {
                image.flush();
            }
            throw e;
        } finally {
            if (g2d != null) {
                g2d.dispose();
            }
        }
    }

    private void setupRenderingHints(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
    }

    private void renderBackground(Graphics2D g2d) {
        String bgPath = theme.getBackgroundImage();
        boolean backgroundLoaded = false;
        BufferedImage background = null;
        BufferedImage scaledBg = null;
        Graphics2D bgG2d = null;

        // Try to load background image if path is provided and not empty
        if (bgPath != null && !bgPath.trim().isEmpty()) {
            try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(bgPath)) {
                if (inputStream != null) {
                    background = ImageIO.read(inputStream);
                    if (background != null) {
                        // Optimized scaling - use TYPE_INT_RGB for better performance
                        scaledBg = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                        bgG2d = scaledBg.createGraphics();
                        bgG2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        bgG2d.drawImage(background, 0, 0, width, height, null);
                        bgG2d.dispose();

                        g2d.drawImage(scaledBg, 0, 0, null);
                        backgroundLoaded = true;
                    }
                } else {
                    logger.warn("Background image not found: {}, using gradient fallback", bgPath);
                }
            } catch (IOException e) {
                logger.error("Error loading background image '{}': {}", bgPath, e.getMessage());
            } finally {
                // Clean up temporary images
                if (background != null) {
                    background.flush();
                }
                if (scaledBg != null) {
                    scaledBg.flush();
                }
                if (bgG2d != null) {
                    bgG2d.dispose();
                }
            }
        } else {
            logger.warn("No background image specified, using gradient background");
        }

        // Always render gradient background if image loading failed or no image specified
        if (!backgroundLoaded) {
            renderGradientBackground(g2d);
        }
    }

    private void renderGradientBackground(Graphics2D g2d) {
        Color primaryColor = theme.getColorPalette().getPrimaryColor();
        Color secondaryColor = theme.getColorPalette().getSecondaryColor();

        // Create gradient with proper alpha handling
        GradientPaint gradient = new GradientPaint(
                0, 0, primaryColor,
                width, height, secondaryColor
        );
        
        // Set composite mode for proper alpha blending
        Composite originalComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
        g2d.setPaint(gradient);
        g2d.fillRect(0, 0, width, height);
        g2d.setComposite(originalComposite);
    }

    private void renderOverlay(Graphics2D g2d) {
        Color overlayColor = theme.getColorPalette().getOverlayColor();

        // Only render overlay if it has some transparency
        if (overlayColor.getAlpha() > 0) {
            // Use AlphaComposite for proper blending
            Composite originalComposite = g2d.getComposite();
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
            g2d.setColor(overlayColor);
            g2d.fillRect(0, 0, width, height);
            g2d.setComposite(originalComposite);
        }
    }

    private void renderTextBox(Graphics2D g2d, List<TextComponent> components) {
        Font originalFont = loadFont();
        g2d.setFont(originalFont);
        FontMetrics originalFm = g2d.getFontMetrics();

        // Convert components to plain text for wrapping analysis
        String plainText = componentsToPlainText(components);
        
        // Calculate available space for text box
        Theme.BoxStyle boxStyle = theme.getBoxStyle();
        int paddingX = (int) (boxStyle.getPaddingX() * scaleFactor);
        int paddingY = (int) (boxStyle.getPaddingY() * scaleFactor);
        
        int maxBoxWidth = (int) (width * 0.8); // 80% of image width
        int maxBoxHeight = (int) (height * 0.6); // 60% of image height
        int maxTextWidth = maxBoxWidth - (paddingX * 2);
        int maxTextHeight = maxBoxHeight - (paddingY * 2);

        // Wrap and scale text to fit
        TextWrapper.WrappedText wrappedText = TextWrapper.wrapAndScaleText(
                plainText, originalFm, maxTextWidth, maxTextHeight, width, height);

        // Create scaled font
        Font scaledFont = originalFont.deriveFont((float) (originalFont.getSize() * wrappedText.getFontScale()));
        g2d.setFont(scaledFont);
        FontMetrics scaledFm = g2d.getFontMetrics();

        // Calculate box dimensions with wrapped text
        BoxDimensions boxDims = calculateBoxDimensionsForWrappedText(wrappedText, scaledFm, paddingX, paddingY);

        // Render components in order: shadow, box, border, content
        renderShadow(g2d, boxDims);
        renderBox(g2d, boxDims);
        renderBorder(g2d, boxDims);
        renderWrappedContent(g2d, wrappedText, boxDims, scaledFm);
    }

    /**
     * Converts text components to plain text for wrapping analysis
     */
    private String componentsToPlainText(List<TextComponent> components) {
        StringBuilder sb = new StringBuilder();
        for (TextComponent component : components) {
            if (component.isEmoji()) {
                sb.append("E"); // Use a placeholder character for emoji width calculation
            } else {
                sb.append(component.getContent());
            }
        }
        return sb.toString();
    }

    /**
     * Calculate box dimensions for wrapped text
     */
    private BoxDimensions calculateBoxDimensionsForWrappedText(
            TextWrapper.WrappedText wrappedText, FontMetrics fm, int paddingX, int paddingY) {
        
        int boxWidth = wrappedText.getMaxLineWidth() + (paddingX * 2);
        int boxHeight = wrappedText.getTotalHeight() + (paddingY * 2);

        // Center the box
        int boxX = (width - boxWidth) / 2;
        int boxY = (height - boxHeight) / 2;

        return new BoxDimensions(boxX, boxY, boxWidth, boxHeight, paddingX, paddingY, fm.getHeight());
    }

    /**
     * Renders wrapped text content with proper line breaks and scaling
     */
    private void renderWrappedContent(Graphics2D g2d, TextWrapper.WrappedText wrappedText,
            BoxDimensions boxDims, FontMetrics fm) {
        
        g2d.setColor(theme.getColorPalette().getTextColor());
        
        List<String> lines = wrappedText.getLines();
        int lineHeight = fm.getHeight();
        int startY = boxDims.y + boxDims.paddingY + fm.getAscent();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) {
                continue; // Skip empty lines but maintain spacing
            }
            
            // Calculate x position for center alignment
            int lineWidth = fm.stringWidth(line);
            int startX = boxDims.x + (boxDims.width - lineWidth) / 2;
            
            // Calculate y position for current line
            int currentY = startY + (i * lineHeight);
            
            // Render the line
            g2d.drawString(line, startX, currentY);
        }
    }

    private Font loadFont() {
        Theme.TextStyle textStyle = theme.getTextStyle();
        int fontSize = (int) (textStyle.getFontSize() * scaleFactor);
        String fontWeight = textStyle.getFontWeight();

        // Try to load custom font
        try (InputStream fontStream = getClass().getClassLoader()
                .getResourceAsStream("fonts/" + textStyle.getFontFamily() + ".ttf")) {
            if (fontStream != null) {
                Font baseFont = Font.createFont(Font.TRUETYPE_FONT, fontStream);
                int style = getFontStyle(fontWeight);
                return baseFont.deriveFont(style, (float) fontSize);
            }
        } catch (Exception e) {
            logger.error("Failed to load custom font, using system font: {}", e.getMessage());
        }

        // Fallback to system fonts
        String[] fallbackFonts = {"SansSerif", "Arial", "Helvetica"};
        int style = getFontStyle(fontWeight);

        for (String fontName : fallbackFonts) {
            Font font = new Font(fontName, style, fontSize);
            if (font.getFamily().equals(fontName)) {
                return font;
            }
        }

        return new Font(Font.SANS_SERIF, style, fontSize);
    }

    private int getFontStyle(String fontWeight) {
        if (fontWeight == null) return Font.PLAIN;

        switch (fontWeight.toUpperCase()) {
            case "BOLD":
                return Font.BOLD;
            case "ITALIC":
                return Font.ITALIC;
            case "BOLD_ITALIC":
                return Font.BOLD | Font.ITALIC;
            default:
                return Font.PLAIN;
        }
    }

    private void renderShadow(Graphics2D g2d, BoxDimensions boxDims) {
        Theme.BoxStyle boxStyle = theme.getBoxStyle();
        int shadowOffsetX = (int) (boxStyle.getShadowOffsetX() * scaleFactor);
        int shadowOffsetY = (int) (boxStyle.getShadowOffsetY() * scaleFactor);
        int cornerRadius = (int) (boxStyle.getCornerRadius() * scaleFactor);

        Color shadowColor = theme.getColorPalette().getShadowColor();
        if (shadowColor.getAlpha() > 0) {
            g2d.setColor(shadowColor);
            g2d.fillRoundRect(
                    boxDims.x + shadowOffsetX,
                    boxDims.y + shadowOffsetY,
                    boxDims.width,
                    boxDims.height,
                    cornerRadius,
                    cornerRadius
            );
        }
    }

    private void renderBox(Graphics2D g2d, BoxDimensions boxDims) {
        int cornerRadius = (int) (theme.getBoxStyle().getCornerRadius() * scaleFactor);
        Color primaryColor = theme.getColorPalette().getPrimaryColor();

        // Ensure we're using the correct composite mode for alpha blending
        Composite originalComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER));
        g2d.setColor(primaryColor);
        g2d.fillRoundRect(boxDims.x, boxDims.y, boxDims.width, boxDims.height, cornerRadius, cornerRadius);
        g2d.setComposite(originalComposite);
    }

    private void renderBorder(Graphics2D g2d, BoxDimensions boxDims) {
        Theme.BoxStyle boxStyle = theme.getBoxStyle();
        int borderWidth = (int) (boxStyle.getBorderWidth() * scaleFactor);

        if (borderWidth > 0) {
            int cornerRadius = (int) (boxStyle.getCornerRadius() * scaleFactor);
            Color secondaryColor = theme.getColorPalette().getSecondaryColor();

            g2d.setColor(secondaryColor);
            g2d.setStroke(new BasicStroke(borderWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.drawRoundRect(boxDims.x, boxDims.y, boxDims.width, boxDims.height, cornerRadius, cornerRadius);
        }
    }

    // Immutable data class for box dimensions
    private static class BoxDimensions {
        final int x, y, width, height, paddingX, paddingY, textHeight;

        BoxDimensions(int x, int y, int width, int height, int paddingX, int paddingY, int textHeight) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.paddingX = paddingX;
            this.paddingY = paddingY;
            this.textHeight = textHeight;
        }
    }
}