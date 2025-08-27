package me.hash.mediaroulette.utils.media.image_generation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;

public class Theme {
    private static final Logger logger = LoggerFactory.getLogger(Theme.class);

    private String name;
    private String backgroundImage;
    private ColorPalette colorPalette;
    private TextStyle textStyle;
    private BoxStyle boxStyle;

    // Constructors
    public Theme() {}

    public Theme(String name, String backgroundImage, ColorPalette colorPalette,
                 TextStyle textStyle, BoxStyle boxStyle) {
        this.name = name;
        this.backgroundImage = backgroundImage;
        this.colorPalette = colorPalette;
        this.textStyle = textStyle;
        this.boxStyle = boxStyle;
    }

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBackgroundImage() { return backgroundImage; }
    public void setBackgroundImage(String backgroundImage) { this.backgroundImage = backgroundImage; }

    public ColorPalette getColorPalette() { return colorPalette; }
    public void setColorPalette(ColorPalette colorPalette) { this.colorPalette = colorPalette; }

    public TextStyle getTextStyle() { return textStyle; }
    public void setTextStyle(TextStyle textStyle) { this.textStyle = textStyle; }

    public BoxStyle getBoxStyle() { return boxStyle; }
    public void setBoxStyle(BoxStyle boxStyle) { this.boxStyle = boxStyle; }

    // Inner classes for theme components
    public static class ColorPalette {
        private String primary;
        private String secondary;
        private String accent;
        private String text;
        private String overlay;
        private String shadow;

        public ColorPalette() {}

        // Getters and Setters
        public String getPrimary() { return primary; }
        public void setPrimary(String primary) { this.primary = primary; }

        public String getSecondary() { return secondary; }
        public void setSecondary(String secondary) { this.secondary = secondary; }

        public String getAccent() { return accent; }
        public void setAccent(String accent) { this.accent = accent; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public String getOverlay() { return overlay; }
        public void setOverlay(String overlay) { this.overlay = overlay; }

        public String getShadow() { return shadow; }
        public void setShadow(String shadow) { this.shadow = shadow; }

        public Color getPrimaryColor() { return parseColor(primary); }
        public Color getSecondaryColor() { return parseColor(secondary); }
        public Color getAccentColor() { return parseColor(accent); }
        public Color getTextColor() { return parseColor(text); }
        public Color getOverlayColor() { return parseColor(overlay); }
        public Color getShadowColor() { return parseColor(shadow); }

        private Color parseColor(String colorStr) {
            if (colorStr == null || colorStr.isEmpty()) {
                logger.warn("Warning: Null or empty color string, using fallback");
                return new Color(128, 128, 128, 255); // Gray fallback instead of black
            }

            colorStr = colorStr.trim();

            try {
                if (colorStr.startsWith("#")) {
                    // Handle hex colors
                    Color color = Color.decode(colorStr);
                    // Ensure full opacity for hex colors if not specified
                    return new Color(color.getRed(), color.getGreen(), color.getBlue(), 255);
                } else if (colorStr.startsWith("rgba(")) {
                    // Parse rgba(r,g,b,a) format - IMPROVED VERSION
                    String values = colorStr.substring(5, colorStr.length() - 1);
                    String[] parts = values.split(",");

                    if (parts.length != 4) {
                        throw new IllegalArgumentException("Invalid RGBA format: expected 4 values, got " + parts.length);
                    }

                    int r = Math.max(0, Math.min(255, Integer.parseInt(parts[0].trim())));
                    int g = Math.max(0, Math.min(255, Integer.parseInt(parts[1].trim())));
                    int b = Math.max(0, Math.min(255, Integer.parseInt(parts[2].trim())));

                    // Improved alpha handling
                    String alphaStr = parts[3].trim();
                    int alpha;

                    if (alphaStr.contains(".")) {
                        // Alpha as decimal (0.0-1.0)
                        float alphaFloat = Float.parseFloat(alphaStr);
                        alpha = Math.round(Math.max(0, Math.min(1, alphaFloat)) * 255);
                    } else {
                        // Alpha as integer (0-255)
                        int alphaInt = Integer.parseInt(alphaStr);
                        // Handle case where alpha might be given as 0-255 or 0-1 scale
                        if (alphaInt <= 1) {
                            alpha = alphaInt * 255;
                        } else {
                            alpha = Math.min(255, alphaInt);
                        }
                    }

                    // Ensure minimum alpha to prevent completely transparent colors
                    alpha = Math.max(alpha, 10);

                    return new Color(r, g, b, alpha);
                } else if (colorStr.startsWith("rgb(")) {
                    // Parse rgb(r,g,b) format
                    String values = colorStr.substring(4, colorStr.length() - 1);
                    String[] parts = values.split(",");

                    if (parts.length != 3) {
                        throw new IllegalArgumentException("Invalid RGB format: expected 3 values, got " + parts.length);
                    }

                    int r = Math.max(0, Math.min(255, Integer.parseInt(parts[0].trim())));
                    int g = Math.max(0, Math.min(255, Integer.parseInt(parts[1].trim())));
                    int b = Math.max(0, Math.min(255, Integer.parseInt(parts[2].trim())));

                    return new Color(r, g, b, 255); // Full opacity for RGB
                } else {
                    // Try to decode as hex without #
                    Color color = Color.decode("#" + colorStr);
                    return new Color(color.getRed(), color.getGreen(), color.getBlue(), 255);
                }
            } catch (Exception e) {
                logger.error("Failed to parse color: '{}' - {}", colorStr, e.getMessage(), e);
                return new Color(128, 128, 128, 200); // Semi-transparent gray
            }
        }
    }

    public static class TextStyle {
        private String fontFamily;
        private int fontSize;
        private String fontWeight;
        private boolean antiAliasing;

        public TextStyle() {}

        // Getters and Setters
        public String getFontFamily() { return fontFamily; }
        public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }

        public int getFontSize() { return fontSize; }
        public void setFontSize(int fontSize) { this.fontSize = fontSize; }

        public String getFontWeight() { return fontWeight; }
        public void setFontWeight(String fontWeight) { this.fontWeight = fontWeight; }

        public boolean isAntiAliasing() { return antiAliasing; }
        public void setAntiAliasing(boolean antiAliasing) { this.antiAliasing = antiAliasing; }
    }

    public static class BoxStyle {
        private int cornerRadius;
        private int paddingX;
        private int paddingY;
        private int borderWidth;
        private int shadowOffsetX;
        private int shadowOffsetY;
        private String boxType;

        public BoxStyle() {}

        // Getters and Setters
        public int getCornerRadius() { return cornerRadius; }
        public void setCornerRadius(int cornerRadius) { this.cornerRadius = cornerRadius; }

        public int getPaddingX() { return paddingX; }
        public void setPaddingX(int paddingX) { this.paddingX = paddingX; }

        public int getPaddingY() { return paddingY; }
        public void setPaddingY(int paddingY) { this.paddingY = paddingY; }

        public int getBorderWidth() { return borderWidth; }
        public void setBorderWidth(int borderWidth) { this.borderWidth = borderWidth; }

        public int getShadowOffsetX() { return shadowOffsetX; }
        public void setShadowOffsetX(int shadowOffsetX) { this.shadowOffsetX = shadowOffsetX; }

        public int getShadowOffsetY() { return shadowOffsetY; }
        public void setShadowOffsetY(int shadowOffsetY) { this.shadowOffsetY = shadowOffsetY; }

        public String getBoxType() { return boxType; }
        public void setBoxType(String boxType) { this.boxType = boxType; }
    }
}