package me.hash.mediaroulette.bot.utils;

public enum Emoji {
    YT_SHORTS_LOGO("<:yt_shorts_logo:1406389291915018250>"),
    YT_LOGO("<:yt_logo:1406389278866542684>"),
    URBAN_DICTIONARY_LOGO("<:urban_dictionary_logo:1406389245307781322>"),
    TENOR_LOGO("<:tenor_logo:1406389235942166620>"),
    REDDIT_LOGO("<:reddit_logo:1406389226651779142>"),
    IMGUR_LOGO("<:imgur_logo:1406389218703310880>"),
    GOOGLE_LOGO("<:google_logo:1406389210537132082>"),
    _4CHAN_LOGO("<:4chan_logo:1406389199187476480>"),
    COIN("<:coin:1394695223858167928>"),
    LOADING("<a:loading:1350829863157891094>"),
    INFO("<:info:1285350527281922121>"),

    // Progress Bars
    PROGRESS_BAR_LEFT_EMPTY("<:progress_bar_left_empty_green:1420849889058881596>"),
    PROGRESS_BAR_MID_EMPTY("<:progress_bar_mid_empty_green:1420849903781019690>"),
    PROGRESS_BAR_RIGHT_EMPTY("<:progress_bar_right_empty_green:1420849918008098866>"),
    PROGRESS_BAR_LEFT_FULL("<:progress_bar_left_full_green:1420849896516485352>"),
    PROGRESS_BAR_MID_FULL("<:progress_bar_mid_full_green:1420849910563082280>"),
    PROGRESS_BAR_RIGHT_FULL("<:progress_bar_right_full_green:1420849924655808623>");

    private final String format;

    Emoji(String format) {
        this.format = format;
    }

    public String getFormat() {
        return format;
    }
    
    /**
     * Creates a progress bar using custom Discord emojis
     * @param current Current value
     * @param max Maximum value
     * @param segments Number of segments (recommended: 8-12)
     * @return Formatted progress bar string
     */
    public static String createProgressBar(long current, long max, int segments) {
        if (max == 0 || segments < 1) return "";
        
        double percentage = (double) current / max;
        int filled = (int) Math.round(percentage * segments);
        filled = Math.max(0, Math.min(segments, filled));
        
        StringBuilder bar = new StringBuilder();
        
        for (int i = 0; i < segments; i++) {
            if (i == 0) {
                bar.append(i < filled ? PROGRESS_BAR_LEFT_FULL.format : PROGRESS_BAR_LEFT_EMPTY.format);
            } else if (i == segments - 1) {
                bar.append(i < filled ? PROGRESS_BAR_RIGHT_FULL.format : PROGRESS_BAR_RIGHT_EMPTY.format);
            } else {
                bar.append(i < filled ? PROGRESS_BAR_MID_FULL.format : PROGRESS_BAR_MID_EMPTY.format);
            }
        }
        
        return bar.toString();
    }
}