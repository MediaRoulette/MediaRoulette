package me.hash.mediaroulette.bot.utils;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.thumbnail.Thumbnail;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Unified factory for creating Discord containers with consistent styling.
 * Uses JDA v5's Components V2 API for modern, visual UI elements.
 */
public class ContainerFactory {

    // ===== COLOR PRESETS =====
    public static final Color SUCCESS_COLOR = new Color(87, 242, 135);
    public static final Color ERROR_COLOR = new Color(255, 107, 107);
    public static final Color WARNING_COLOR = new Color(255, 193, 7);
    public static final Color INFO_COLOR = new Color(88, 101, 242);
    public static final Color LOADING_COLOR = new Color(88, 101, 242);
    public static final Color SETTINGS_COLOR = new Color(114, 137, 218);
    public static final Color PREMIUM_COLOR = new Color(138, 43, 226);
    
    // ===== PROGRESS BAR CHARACTERS =====
    private static final String PROGRESS_FILLED = "▓";
    private static final String PROGRESS_EMPTY = "░";
    private static final int PROGRESS_BAR_LENGTH = 10;

    // ===== BASIC CONTAINER TYPES =====

    /**
     * Create a success container with green accent.
     */
    public static Container createSuccess(User user, String title, String description) {
        return createBasicContainer(user, "✅ " + title, description, SUCCESS_COLOR);
    }

    /**
     * Create an error container with red accent.
     */
    public static Container createError(User user, String title, String description) {
        return createBasicContainer(user, "❌ " + title, description, ERROR_COLOR);
    }

    /**
     * Create a warning container with yellow accent.
     */
    public static Container createWarning(User user, String title, String description) {
        return createBasicContainer(user, "⚠️ " + title, description, WARNING_COLOR);
    }

    /**
     * Create an info container with blue accent.
     */
    public static Container createInfo(User user, String title, String description) {
        return createBasicContainer(user, "ℹ️ " + title, description, INFO_COLOR);
    }

    /**
     * Create a loading container with animated loading indicator.
     */
    public static Container createLoading(User user, String title, String description) {
        return Container.of(
                Section.of(
                        Thumbnail.fromUrl(user.getEffectiveAvatarUrl()),
                        TextDisplay.of("## <a:loading:1350829863157891094> " + title),
                        TextDisplay.of("**" + description + "**"),
                        TextDisplay.of("*Please wait...*")
                )
        ).withAccentColor(LOADING_COLOR);
    }

    /**
     * Create a basic container with custom color.
     */
    public static Container createBasicContainer(User user, String title, String description, Color color) {
        return Container.of(
                Section.of(
                        Thumbnail.fromUrl(user.getEffectiveAvatarUrl()),
                        TextDisplay.of("## " + title),
                        TextDisplay.of("**" + description + "**")
                )
        ).withAccentColor(color);
    }

    // ===== SETTINGS CONTAINERS =====

    /**
     * Create a settings container with header and multiple sections.
     */
    public static Container createSettings(User user, String title, String subtitle, List<Section> sections) {
        List<ContainerChildComponent> components = new ArrayList<>();
        
        // Header section
        components.add(Section.of(
                Thumbnail.fromUrl(user.getEffectiveAvatarUrl()),
                TextDisplay.of("## ⚙️ " + title),
                TextDisplay.of("**" + subtitle + "**")
        ));
        
        components.add(Separator.createDivider(Separator.Spacing.SMALL));
        
        // Add all content sections
        for (Section section : sections) {
            components.add(section);
            components.add(Separator.createDivider(Separator.Spacing.SMALL));
        }
        
        // Remove trailing separator
        if (!components.isEmpty() && components.getLast() instanceof Separator) {
            components.removeLast();
        }
        
        return Container.of((Collection<? extends ContainerChildComponent>) components).withAccentColor(SETTINGS_COLOR);
    }

    /**
     * Create a settings container with header, sections, and action buttons.
     */
    public static Container createSettingsWithActions(User user, String title, String subtitle, 
                                                       List<Section> sections, ActionRow actionRow) {
        List<ContainerChildComponent> components = new ArrayList<>();
        
        // Header section
        components.add(Section.of(
                Thumbnail.fromUrl(user.getEffectiveAvatarUrl()),
                TextDisplay.of("## ⚙️ " + title),
                TextDisplay.of("**" + subtitle + "**")
        ));
        
        components.add(Separator.createDivider(Separator.Spacing.SMALL));
        
        // Add all content sections
        for (Section section : sections) {
            components.add(section);
            components.add(Separator.createDivider(Separator.Spacing.SMALL));
        }
        
        // Add action row
        components.add(actionRow);
        
        return Container.of((Collection<? extends ContainerChildComponent>) components).withAccentColor(SETTINGS_COLOR);
    }

    // ===== HELPER COMPONENTS =====

    /**
     * Create a visual progress bar as a text display.
     * Example: "▓▓▓▓▓░░░░░ 50%"
     */
    public static String createProgressBar(double value, double max) {
        double percentage = max > 0 ? (value / max) * 100 : 0;
        int filled = (int) Math.round((percentage / 100) * PROGRESS_BAR_LENGTH);
        filled = Math.max(0, Math.min(PROGRESS_BAR_LENGTH, filled));
        
        StringBuilder bar = new StringBuilder();
        bar.append(PROGRESS_FILLED.repeat(filled));
        bar.append(PROGRESS_EMPTY.repeat(PROGRESS_BAR_LENGTH - filled));
        bar.append(String.format(" %.0f%%", percentage));
        
        return bar.toString();
    }

    /**
     * Create a source card as a formatted line.
     * Example: "✓ Reddit          ▓▓▓▓▓▓░░░░ 30%"
     */
    public static String createSourceLine(String emoji, String name, boolean enabled, double chance) {
        String statusIcon = enabled ? "🟢" : "🔴";
        String progressBar = createProgressBar(chance, 100);
        return String.format("%s %s **%s** %s", statusIcon, emoji, name, progressBar);
    }

    /**
     * Create a content block displaying multiple source statistics.
     * Returns formatted TextDisplay instead of Section (Section requires accessory).
     */
    public static TextDisplay createSourcesContent(String categoryTitle, List<SourceDisplay> sources) {
        StringBuilder content = new StringBuilder();
        content.append("### ").append(categoryTitle).append("\n");
        
        for (SourceDisplay source : sources) {
            content.append(createSourceLine(
                    source.emoji(), source.name(), source.enabled(), source.chance()));
            content.append("\n");
        }
        
        return TextDisplay.of(content.toString());
    }

    /**
     * Create a statistics content block with labeled values.
     * Returns formatted TextDisplay instead of Section (Section requires accessory).
     */
    public static TextDisplay createStatsContent(String title, Map<String, String> stats) {
        StringBuilder content = new StringBuilder();
        content.append("### 📊 ").append(title).append("\n");
        content.append("```\n");
        
        for (Map.Entry<String, String> entry : stats.entrySet()) {
            content.append(String.format("%-15s %s\n", entry.getKey() + ":", entry.getValue()));
        }
        content.append("```");
        
        return TextDisplay.of(content.toString());
    }

    /**
     * Create a banner content for alerts/warnings.
     * Returns formatted TextDisplay instead of Section (Section requires accessory).
     */
    public static TextDisplay createBanner(String emoji, String message, boolean isWarning) {
        String prefix = isWarning ? "⚠️" : emoji;
        return TextDisplay.of("**" + prefix + " " + message + "**");
    }

    /**
     * Create a status banner for unsaved changes.
     * Returns formatted TextDisplay instead of Section (Section requires accessory).
     */
    public static TextDisplay createUnsavedChangesBanner() {
        return TextDisplay.of("### ⚠️ Unsaved Changes\n*You have unsaved changes. Click **Save** to apply them.*");
    }

    /**
     * Create a status message content.
     * Returns formatted TextDisplay instead of Section (Section requires accessory).
     */
    public static TextDisplay createStatusContent(String emoji, String message) {
        return TextDisplay.of(emoji + " " + message);
    }

    // ===== ACTION ROW BUILDERS =====

    /**
     * Create a standard save/cancel action row.
     */
    public static ActionRow createSaveActionRow(String saveId, String cancelId, boolean hasChanges) {
        return ActionRow.of(
                Button.success(saveId, "💾 Save").withDisabled(!hasChanges),
                Button.secondary(cancelId, "↩️ Cancel")
        );
    }

    /**
     * Create navigation buttons for tabbed interfaces.
     */
    public static ActionRow createNavigationRow(String prevId, String nextId, int current, int total) {
        return ActionRow.of(
                Button.secondary(prevId, "◀").withDisabled(current <= 1),
                Button.secondary("page_indicator", current + "/" + total).asDisabled(),
                Button.secondary(nextId, "▶").withDisabled(current >= total)
        );
    }

    /**
     * Create toggle button for enabling/disabling something.
     */
    public static Button createToggleButton(String id, String label, boolean currentState) {
        if (currentState) {
            return Button.danger(id, "🔴 Disable " + label);
        } else {
            return Button.success(id, "🟢 Enable " + label);
        }
    }

    /**
     * Create edit button for opening a modal.
     */
    public static Button createEditButton(String id, String label) {
        return Button.primary(id, "✏️ Edit " + label);
    }

    // ===== DATA CLASSES =====

    /**
     * Record for displaying source information.
     */
    public record SourceDisplay(String key, String name, String emoji, boolean enabled, double chance) {}
}
