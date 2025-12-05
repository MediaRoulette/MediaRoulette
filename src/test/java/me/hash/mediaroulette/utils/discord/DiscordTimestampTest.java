package me.hash.mediaroulette.utils.discord;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class DiscordTimestampTest {

    @Test
    void generateTimestampFromInstant() {
        Instant instant = Instant.ofEpochSecond(1678886400); // 2023-03-15T13:20:00Z
        String timestamp = DiscordTimestamp.generateTimestamp(instant, DiscordTimestampType.SHORT_TIME);
        assertEquals("<t:1678886400:t>", timestamp);
    }

    @Test
    void generateTimestampFromDate() {
        Date date = new Date(1678886400000L); // 2023-03-15T13:20:00Z
        String timestamp = DiscordTimestamp.generateTimestamp(date, DiscordTimestampType.LONG_DATE);
        assertEquals("<t:1678886400:D>", timestamp);
    }

    @Test
    void generateTimestampFromIso8601_Valid() {
        String isoDate = "2023-03-15T13:20:00Z";
        String timestamp = DiscordTimestamp.generateTimestampFromIso8601(isoDate, DiscordTimestampType.RELATIVE);
        assertEquals("<t:1678886400:R>", timestamp);
    }

    @Test
    void generateTimestampFromIso8601_Invalid() {
        String invalidIsoDate = "invalid-date-string";
        assertThrows(IllegalArgumentException.class, () -> {
            DiscordTimestamp.generateTimestampFromIso8601(invalidIsoDate, DiscordTimestampType.SHORT_TIME);
        });
    }
}