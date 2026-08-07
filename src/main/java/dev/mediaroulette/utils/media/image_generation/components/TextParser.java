package dev.mediaroulette.utils.media.image_generation.components;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TextParser {
    private static final String EMOJI_REGEX = "[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u200D]+|[^\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u200D]+";
    private static final Pattern EMOJI_PATTERN = Pattern.compile(EMOJI_REGEX);

    public static List<TextComponent> parseText(String text) {
        List<TextComponent> components = new ArrayList<>();
        Matcher matcher = EMOJI_PATTERN.matcher(text);

        while (matcher.find()) {
            String match = matcher.group();
            if (isEmoji(match)) {
                components.add(new TextComponent(getTwemojiUrl(match), TextComponent.ComponentType.EMOJI));
            } else {
                components.add(new TextComponent(match, TextComponent.ComponentType.TEXT));
            }
        }
        return components;
    }

    private static boolean isEmoji(String text) {
        if (text == null || text.isEmpty()) return false;
        int codePoint = text.codePointAt(0);
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);

        return block.equals(Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS_AND_PICTOGRAPHS)
                || block.equals(Character.UnicodeBlock.EMOTICONS)
                || block.equals(Character.UnicodeBlock.TRANSPORT_AND_MAP_SYMBOLS)
                || block.equals(Character.UnicodeBlock.SUPPLEMENTAL_SYMBOLS_AND_PICTOGRAPHS)
                || block.equals(Character.UnicodeBlock.MISCELLANEOUS_SYMBOLS)
                || block.equals(Character.UnicodeBlock.DINGBATS);
    }

    private static String getTwemojiUrl(String emoji) {
        String unicode = emoji.codePoints()
                .mapToObj(cp -> String.format("%x", cp))
                .collect(Collectors.joining("-"));
        return "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/" + unicode + ".png";
    }
}
