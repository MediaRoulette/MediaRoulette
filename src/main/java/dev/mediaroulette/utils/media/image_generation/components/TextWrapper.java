package dev.mediaroulette.utils.media.image_generation.components;

import dev.mediaroulette.bot.Bot;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.requests.Route;

import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles text wrapping, scaling, and line breaking for text components
 */
public class TextWrapper {
    private static final int MAX_LINES = 10; // Maximum number of lines before scaling down
    private static final double MIN_FONT_SCALE = 0.3; // Minimum font scale (30% of original)
    private static final double MAX_BOX_WIDTH_RATIO = 0.8; // Max box width as ratio of image width
    private static final double MAX_BOX_HEIGHT_RATIO = 0.6; // Max box height as ratio of image height
    
    public static class WrappedText {
        private final List<String> lines;
        private final double fontScale;
        private final int maxLineWidth;
        private final int totalHeight;
        
        public WrappedText(List<String> lines, double fontScale, int maxLineWidth, int totalHeight) {
            this.lines = lines;
            this.fontScale = fontScale;
            this.maxLineWidth = maxLineWidth;
            this.totalHeight = totalHeight;
        }
        
        public List<String> getLines() { return lines; }
        public double getFontScale() { return fontScale; }
        public int getMaxLineWidth() { return maxLineWidth; }
        public int getTotalHeight() { return totalHeight; }
    }
    
    /**
     * Wraps and scales text to fit within the available space
     */
    public static WrappedText wrapAndScaleText(String text, FontMetrics originalFm, 
                                               int maxWidth, int maxHeight, int imageWidth, int imageHeight) {
        
        // Calculate maximum allowed box dimensions
        int absoluteMaxWidth = (int) (imageWidth * MAX_BOX_WIDTH_RATIO);
        int absoluteMaxHeight = (int) (imageHeight * MAX_BOX_HEIGHT_RATIO);
        
        maxWidth = Math.min(maxWidth, absoluteMaxWidth);
        maxHeight = Math.min(maxHeight, absoluteMaxHeight);
        
        double currentScale = 1.0;
        WrappedText result = null;
        
        // Try different font scales until text fits
        while (currentScale >= MIN_FONT_SCALE) {
            // Create scaled font metrics (approximate)
            int scaledFontSize = (int) (originalFm.getFont().getSize() * currentScale);
            int scaledHeight = (int) (originalFm.getHeight() * currentScale);
            int scaledAscent = (int) (originalFm.getAscent() * currentScale);
            
            // Wrap text with current scale
            List<String> lines = wrapTextToLines(text, originalFm, maxWidth, currentScale);
            
            // Calculate dimensions
            int maxLineWidth = 0;
            for (String line : lines) {
                int lineWidth = (int) (originalFm.stringWidth(line) * currentScale);
                maxLineWidth = Math.max(maxLineWidth, lineWidth);
            }
            
            int totalHeight = lines.size() * scaledHeight;
            
            // Check if it fits
            if (lines.size() <= MAX_LINES && totalHeight <= maxHeight && maxLineWidth <= maxWidth) {
                result = new WrappedText(lines, currentScale, maxLineWidth, totalHeight);
                break;
            }
            
            // Reduce scale and try again
            currentScale -= 0.05; // Reduce by 5% each iteration
        }
        
        // Fallback if nothing fits
        if (result == null) {
            List<String> fallbackLines = wrapTextToLines(text, originalFm, maxWidth, MIN_FONT_SCALE);
            if (fallbackLines.size() > MAX_LINES) {
                fallbackLines = fallbackLines.subList(0, MAX_LINES);
                // Add ellipsis to last line
                String lastLine = fallbackLines.get(MAX_LINES - 1);
                if (lastLine.length() > 3) {
                    fallbackLines.set(MAX_LINES - 1, lastLine.substring(0, lastLine.length() - 3) + "...");
                }
            }
            
            int maxLineWidth = 0;
            for (String line : fallbackLines) {
                int lineWidth = (int) (originalFm.stringWidth(line) * MIN_FONT_SCALE);
                maxLineWidth = Math.max(maxLineWidth, lineWidth);
            }
            
            int totalHeight = (int) (fallbackLines.size() * originalFm.getHeight() * MIN_FONT_SCALE);
            result = new WrappedText(fallbackLines, MIN_FONT_SCALE, maxLineWidth, totalHeight);
        }
        
        return result;
    }
    
    /**
     * Wraps text into lines that fit within the specified width
     */
    private static List<String> wrapTextToLines(String text, FontMetrics fm, int maxWidth, double scale) {
        List<String> lines = new ArrayList<>();
        
        // Handle explicit line breaks first
        String[] paragraphs = text.split("\\n");
        
        for (String paragraph : paragraphs) {
            if (paragraph.trim().isEmpty()) {
                lines.add(""); // Preserve empty lines
                continue;
            }
            
            List<String> wrappedLines = wrapParagraph(paragraph.trim(), fm, maxWidth, scale);
            lines.addAll(wrappedLines);
        }
        
        return lines;
    }
    
    /**
     * Wraps a single paragraph into multiple lines
     */
    private static List<String> wrapParagraph(String paragraph, FontMetrics fm, int maxWidth, double scale) {
        List<String> lines = new ArrayList<>();
        String[] words = paragraph.split("\\s+");
        
        StringBuilder currentLine = new StringBuilder();
        
        for (String word : words) {
            // Handle very long words that need to be broken
            if (getScaledWidth(word, fm, scale) > maxWidth) {
                // Add current line if it has content
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString().trim());
                    currentLine = new StringBuilder();
                }
                
                // Break the long word
                List<String> brokenWord = breakLongWord(word, fm, maxWidth, scale);
                lines.addAll(brokenWord);
                continue;
            }
            
            // Check if adding this word would exceed the width
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            
            if (getScaledWidth(testLine, fm, scale) <= maxWidth) {
                // Word fits, add it to current line
                if (currentLine.length() > 0) {
                    currentLine.append(" ");
                }
                currentLine.append(word);
            } else {
                // Word doesn't fit, start new line
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString().trim());
                }
                currentLine = new StringBuilder(word);
            }
        }
        
        // Add the last line if it has content
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString().trim());
        }
        
        return lines;
    }
    
    /**
     * Breaks a long word into smaller parts that fit within the width
     */
    private static List<String> breakLongWord(String word, FontMetrics fm, int maxWidth, double scale) {
        List<String> parts = new ArrayList<>();
        
        // Try to break at natural points first (hyphens, underscores, etc.)
        String[] naturalBreaks = word.split("(?<=[-_./\\\\])|(?=[-_./\\\\])");
        
        if (naturalBreaks.length > 1) {
            StringBuilder currentPart = new StringBuilder();
            
            for (String part : naturalBreaks) {
                String testPart = currentPart + part;
                
                if (getScaledWidth(testPart, fm, scale) <= maxWidth) {
                    currentPart.append(part);
                } else {
                    if (currentPart.length() > 0) {
                        parts.add(currentPart.toString());
                        currentPart = new StringBuilder(part);
                    } else {
                        // Even the single part is too long, force break it
                        parts.addAll(forceBreakWord(part, fm, maxWidth, scale));
                    }
                }
            }
            
            if (currentPart.length() > 0) {
                parts.add(currentPart.toString());
            }
        } else {
            // No natural break points, force break
            parts.addAll(forceBreakWord(word, fm, maxWidth, scale));
        }
        
        return parts;
    }
    
    /**
     * Force breaks a word by character when no other option exists
     */
    private static List<String> forceBreakWord(String word, FontMetrics fm, int maxWidth, double scale) {
        List<String> parts = new ArrayList<>();
        StringBuilder currentPart = new StringBuilder();
        
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            StringBuilder testPart = currentPart.append(c);
            
            if (getScaledWidth(String.valueOf(testPart), fm, scale) <= maxWidth) {
                currentPart.append(c);
            } else {
                if (currentPart.length() > 0) {
                    // Add hyphen if we're breaking mid-word
                    if (i < word.length() - 1 && currentPart.length() > 1) {
                        parts.add(currentPart.toString() + "-");
                    } else {
                        parts.add(currentPart.toString());
                    }
                    currentPart = new StringBuilder();
                }
                currentPart.append(c);
            }
        }
        
        if (currentPart.length() > 0) {
            parts.add(currentPart.toString());
        }
        
        return parts;
    }
    
    /**
     * Gets the scaled width of text
     */
    private static int getScaledWidth(String text, FontMetrics fm, double scale) {
        return (int) (fm.stringWidth(text) * scale);
    }
}