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
    INFO("<:info:1285350527281922121>");

    private final String format;

    Emoji(String format) {
        this.format = format;
    }

    public String getFormat() {
        return format;
    }
}