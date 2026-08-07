package dev.mediaroulette.utils.media.image_generation.components;

public class TextComponent {
    private final String content;
    private final ComponentType type;
    private final String emojiUrl;

    public TextComponent(String content, ComponentType type) {
        this.content = content;
        this.type = type;
        this.emojiUrl = type == ComponentType.EMOJI ? content : null;
    }

    public String getContent() { return content; }
    public ComponentType getType() { return type; }
    public String getEmojiUrl() { return emojiUrl; }

    public boolean isEmoji() { return type == ComponentType.EMOJI; }
    public boolean isText() { return type == ComponentType.TEXT; }

    public enum ComponentType {
        TEXT, EMOJI
    }
}