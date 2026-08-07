package dev.mediaroulette.utils.discord;

public enum DiscordTimestampType {
    SHORT_TIME("t"),        // 2:30 PM
    LONG_TIME("T"),         // 2:30:45 PM
    SHORT_DATE("d"),        // 10/21/2023
    LONG_DATE("D"),         // October 21, 2023
    SHORT_DATE_TIME("f"),   // October 21, 2023 2:30 PM
    LONG_DATE_TIME("F"),    // Saturday, October 21, 2023 2:30 PM
    RELATIVE("R");          // 5 minutes ago

    private final String format;

    DiscordTimestampType(String format) {
        this.format = format;
    }

    public String getFormat() {
        return format;
    }
}