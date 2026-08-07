package dev.mediaroulette.utils.discord;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Date;

public class DiscordTimestamp {

    /**
     * Generates a formatted Discord timestamp from an Instant.
     *
     * @param date The date/time to generate the timestamp for.
     * @param type The desired DiscordTimestampType.
     * @return The formatted Discord timestamp as a string.
     */
    public static String generateTimestamp(Instant date, DiscordTimestampType type) {
        long unixTimestamp = date.getEpochSecond();
        return String.format("<t:%d:%s>", unixTimestamp, type.getFormat());
    }

    /**
     * Generates a formatted Discord timestamp from a java.util.Date.
     *
     * @param date The java.util.Date to convert.
     * @param type The DiscordTimestampType.
     * @return The formatted Discord timestamp.
     */
    public static String generateTimestamp(Date date, DiscordTimestampType type) {
        return generateTimestamp(date.toInstant(), type);
    }

    /**
     * Converts an ISO 8601 date-time string into a Discord timestamp.
     *
     * @param iso8601 The ISO 8601 date-time string.
     * @param type    The desired DiscordTimestampType.
     * @return The formatted Discord timestamp.
     */
    public static String generateTimestampFromIso8601(String iso8601, DiscordTimestampType type) {
        try {
            Instant instant = Instant.parse(iso8601);
            return generateTimestamp(instant, type);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid ISO 8601 date-time format: " + iso8601, e);
        }
    }
}