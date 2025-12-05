package me.hash.mediaroulette.content.provider.impl.text;

import me.hash.mediaroulette.content.http.HttpClientWrapper;
import me.hash.mediaroulette.content.provider.MediaProvider;
import me.hash.mediaroulette.model.content.MediaResult;
import me.hash.mediaroulette.model.content.MediaSource;
import me.hash.mediaroulette.utils.discord.DiscordTimestamp;
import me.hash.mediaroulette.utils.discord.DiscordTimestampType;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UrbanDictionaryProvider implements MediaProvider {
    private static final String BASE_URL = "https://api.urbandictionary.com/v0";
    private final HttpClientWrapper httpClient;
    private final Random random = new Random();

    public UrbanDictionaryProvider(HttpClientWrapper httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public MediaResult getRandomMedia(String query) throws IOException, HttpClientWrapper.RateLimitException, InterruptedException {
        String url;
        if (query != null && !query.isBlank()) {
            url = BASE_URL + "/define?term=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        } else {
            url = BASE_URL + "/random";
        }

        String response = httpClient.getBody(url);
        JSONObject jsonObject = new JSONObject(response);
        JSONArray list = jsonObject.getJSONArray("list");

        if (list.isEmpty()) {
            throw new IOException("No word found with the given meaning.");
        }

        JSONObject randomWord;
        if (query != null && !query.isBlank()) {
            randomWord = list.getJSONObject(0);
        } else {
            randomWord = list.getJSONObject(random.nextInt(list.length()));
        }

        String word = randomWord.getString("word");
        String definition = randomWord.getString("definition");
        String author = randomWord.getString("author");
        String writtenOn = randomWord.getString("written_on");

        String processedDefinition = processDefinition(definition);
        
        String description = "📖 **Word:** " + word
                + "\n📝 **Meaning:** " + processedDefinition
                + "\n🖋️ **Submitted by:** [" + author + "](https://www.urbandictionary.com/author.php?author=" + URLEncoder.encode(author, StandardCharsets.UTF_8) + ")"
                + "\n📅 **Date:** " + DiscordTimestamp.generateTimestampFromIso8601(writtenOn, DiscordTimestampType.SHORT_DATE_TIME);

        return new MediaResult(
                "attachment://image.png", // Image URL (special case for generated images)
                "🎲 Here's your random Urban Dictionary word!",
                description,
                MediaSource.URBAN_DICTIONARY,
                "create",
                word // content for image generation
        );
    }

    private String processDefinition(String definition) {
        Pattern pattern = Pattern.compile("\\[(.+?)]");
        Matcher matcher = pattern.matcher(definition);

        StringBuilder processedDefinition = new StringBuilder();
        while (matcher.find()) {
            String term = matcher.group(1);
            String encodedTerm = URLEncoder.encode(term, StandardCharsets.UTF_8);
            String replacement = "[" + term + "](https://www.urbandictionary.com/define.php?term=" + encodedTerm + ")";
            matcher.appendReplacement(processedDefinition, replacement);
        }
        matcher.appendTail(processedDefinition);

        return processedDefinition.toString();
    }

    @Override
    public boolean supportsQuery() {
        return true;
    }

    @Override
    public String getProviderName() {
        return "Urban Dictionary";
    }
}
