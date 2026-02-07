package me.hash.mediaroulette.service;

import me.hash.mediaroulette.plugins.images.ImageSourceProvider;

/**
 * Result of channel access validation for image source requests.
 * Contains information about whether access is allowed and, if not,
 * details about why it was denied for proper error messaging.
 *
 * @param allowed whether channel access is granted
 * @param errorTitleKey locale key for error title (null if allowed)
 * @param errorDescriptionKey locale key for error description (null if allowed)
 * @param deniedProvider the provider that was denied access (null if allowed or not source-specific)
 */
public record ValidationResult(
        boolean allowed,
        String errorTitleKey,
        String errorDescriptionKey,
        ImageSourceProvider deniedProvider
) {
    
    /**
     * Create an allowed result - access is granted.
     */
    public static ValidationResult success() {
        return new ValidationResult(true, null, null, null);
    }
    
    /**
     * Create a denied result for a specific NSFW source in a non-NSFW channel.
     */
    public static ValidationResult nsfwSourceDenied(ImageSourceProvider provider) {
        return new ValidationResult(
                false,
                "error.nsfw_source_required_title",
                "error.nsfw_source_required_description",
                provider
        );
    }
    
    /**
     * Create a denied result for DM access without NSFW enabled.
     */
    public static ValidationResult nsfwNotEnabledInDm() {
        return new ValidationResult(
                false,
                "error.nsfw_not_enabled_title",
                "error.nsfw_not_enabled_description",
                null
        );
    }
    
    /**
     * Create a denied result for generic NSFW channel requirement (legacy).
     */
    public static ValidationResult nsfwChannelRequired() {
        return new ValidationResult(
                false,
                "error.use_nsfw_channel_title",
                "error.use_nsfw_channel_description",
                null
        );
    }
    
    /**
     * Check if this result has a source-specific error (for parameterized messages).
     */
    public boolean hasSourceSpecificError() {
        return deniedProvider != null;
    }
    
    /**
     * Get the display name of the denied provider for error messages.
     */
    public String getDeniedProviderName() {
        return deniedProvider != null ? deniedProvider.getDisplayName() : null;
    }
}
