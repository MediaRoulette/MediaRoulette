package me.hash.mediaroulette.content.provider.impl.images;

import me.hash.mediaroulette.content.http.HttpClientWrapper;
import me.hash.mediaroulette.model.content.MediaResult;
import me.hash.mediaroulette.model.content.MediaSource;
import me.hash.mediaroulette.content.provider.MediaProvider;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Random;

public class ImgurProvider implements MediaProvider {
    private static final String[] IMAGE_FORMATS = {"jpg", "png", "gif"};
    private static final String IMGUR_ID_CHARACTERS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int[] IMGUR_ID_LENGTH_RANGE = {5, 6};
    private static final int MAX_ATTEMPTS = 120;

    private final Random random = new Random();
    private final HttpClientWrapper httpClient;

    public ImgurProvider(HttpClientWrapper httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public MediaResult getRandomMedia(String query) throws IOException {
        int attempts = 0;

        while (attempts < MAX_ATTEMPTS) {
            String imgurId = generateRandomImgurId();
            String imageUrl = "https://i.imgur.com/" + imgurId + "." +
                    IMAGE_FORMATS[random.nextInt(IMAGE_FORMATS.length)];

            try {
                byte[] imageBytes = httpClient.getBytes(imageUrl);
                try (ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes)) {
                    BufferedImage image = ImageIO.read(bis);
                    if (isValidImage(image)) {
                        String description = String.format("🌐 Source: Imgur\n🔁 Failed Image Count: %s", attempts);
                        return new MediaResult(imageUrl, "Here is your random Imgur picture!", description, MediaSource.IMGUR);
                    }
                }
            } catch (Exception e) {
                // Continue to next attempt on error (404, etc)
            }
            attempts++;
        }

        throw new IOException("Could not find valid Imgur image after " + MAX_ATTEMPTS + " attempts");
    }

    private boolean isValidImage(BufferedImage image) {
        return image != null &&
                !(image.getWidth() == 198 && image.getHeight() == 160) &&
                !(image.getWidth() == 161 && image.getHeight() == 81) &&
                image.getWidth() >= 64 &&
                image.getHeight() >= 64;
    }

    private String generateRandomImgurId() {
        int length = random.nextInt(IMGUR_ID_LENGTH_RANGE[1] - IMGUR_ID_LENGTH_RANGE[0] + 1) + IMGUR_ID_LENGTH_RANGE[0];
        StringBuilder idBuilder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int pos = random.nextInt(IMGUR_ID_CHARACTERS.length());
            idBuilder.append(IMGUR_ID_CHARACTERS.charAt(pos));
        }
        return idBuilder.toString();
    }

    @Override
    public boolean supportsQuery() {
        return false;
    }

    @Override
    public String getProviderName() {
        return "Imgur";
    }
}