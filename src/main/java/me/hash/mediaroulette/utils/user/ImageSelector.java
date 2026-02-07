package me.hash.mediaroulette.utils.user;

import me.hash.mediaroulette.content.factory.MediaServiceFactory;
import me.hash.mediaroulette.content.http.HttpClientWrapper;
import me.hash.mediaroulette.model.ImageOptions;
import me.hash.mediaroulette.exceptions.InvalidChancesException;
import me.hash.mediaroulette.exceptions.NoEnabledOptionsException;
import me.hash.mediaroulette.config.LocalConfig;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class ImageSelector {
    private final Map<String, ImageOptions> userImageOptions;
    private final Random random = new Random();

    public ImageSelector(Map<String, ImageOptions> userImageOptions) {
        this.userImageOptions = userImageOptions;
    }

    public Map<String, String> selectImage() throws NoEnabledOptionsException, InvalidChancesException {
        return selectImage(null);
    }
    
    public Map<String, String> selectImage(String userId) throws NoEnabledOptionsException, InvalidChancesException {
        List<ImageOptions> defaultImageOptions = ImageOptions.getDefaultOptions();
        PriorityQueue<ImageOptions> queue = new PriorityQueue<>(Comparator.comparingDouble(ImageOptions::getChance));
        LocalConfig config = LocalConfig.getInstance();

        double totalChance = 0;
        for (ImageOptions defaultOption : defaultImageOptions) {
            String imageType = defaultOption.getImageType();
            
            // Check if source is enabled in admin config first
            if (!isSourceEnabledInConfig(config, imageType)) {
                continue; // Skip disabled sources
            }
            
            ImageOptions userOption = userImageOptions.get(imageType);
            
            if (userOption != null) {
                // User has explicitly set this option, respect their choice
                if (userOption.isEnabled()) {
                    totalChance += userOption.getChance();
                    queue.add(userOption);
                }
                // If user disabled it, don't add it at all (no fallback to default)
            } else if (defaultOption.isEnabled()) {
                // User hasn't set this option, use default if enabled
                totalChance += defaultOption.getChance();
                queue.add(defaultOption);
            }
        }

        if (queue.isEmpty()) {
            throw new NoEnabledOptionsException("All image options are disabled");
        }

        normalizeProbabilities(queue, totalChance);

        return getRandomImageFromQueue(queue, userId);
    }

    private void normalizeProbabilities(PriorityQueue<ImageOptions> queue, double totalChance) {
        if (totalChance != 100) {
            double difference = 100 - totalChance;
            double additionalChance = difference / queue.size();
            for (ImageOptions option : queue) {
                option.setChance(option.getChance() + additionalChance);
            }
        }
    }

    private Map<String, String> getRandomImageFromQueue(PriorityQueue<ImageOptions> queue) throws InvalidChancesException {
        return getRandomImageFromQueue(queue, null);
    }
    
    private Map<String, String> getRandomImageFromQueue(PriorityQueue<ImageOptions> queue, String userId) throws InvalidChancesException {
        double rand = random.nextDouble() * 100;
        double cumulativeProbability = 0;
        while (!queue.isEmpty()) {
            ImageOptions selectedOption = queue.poll();
            cumulativeProbability += selectedOption.getChance();
            if (rand <= cumulativeProbability) {
                try {
                    return getImageByType(selectedOption.getImageType(), userId);
                } catch (IOException e) {
                    throw new InvalidChancesException("Failed to fetch image from source: " + selectedOption.getImageType());
                } catch (ExecutionException | InterruptedException | HttpClientWrapper.RateLimitException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        throw new InvalidChancesException("Failed to select an image.");
    }

    private Map<String, String> getImageByType(String imageType, String userId) throws IOException, ExecutionException, InterruptedException, HttpClientWrapper.RateLimitException {
        return switch (imageType) {
            case "4chan" -> new MediaServiceFactory().createFourChanProvider().getRandomMedia(null).toMap();
            case "picsum" -> new MediaServiceFactory().createPicsumProvider().getRandomMedia(null).toMap();
            case "imgur" -> new MediaServiceFactory().createImgurProvider().getRandomMedia(null).toMap();
            case "tenor" -> {
                var provider = new MediaServiceFactory().createTenorProvider();
                if (provider instanceof me.hash.mediaroulette.content.provider.impl.gifs.TenorProvider tenorProvider && userId != null) {
                    yield tenorProvider.getRandomMedia(null, userId).toMap();
                } else {
                    yield provider.getRandomMedia(null).toMap();
                }
            }
            case "google" -> {
                var provider = new MediaServiceFactory().createGoogleProvider();
                if (provider instanceof me.hash.mediaroulette.content.provider.impl.images.GoogleProvider googleProvider && userId != null) {
                    yield googleProvider.getRandomMedia(null, userId).toMap();
                } else {
                    yield provider.getRandomMedia(null).toMap();
                }
            }
            case "movies" -> new MediaServiceFactory().createTMDBMovieProvider().getRandomMedia(null).toMap();
            case "tvshow" -> new MediaServiceFactory().createTMDBTvProvider().getRandomMedia(null).toMap();
            case "youtube" -> new MediaServiceFactory().createYouTubeProvider().getRandomMedia(null).toMap();
            case "short" -> new MediaServiceFactory().createYouTubeShortsProvider().getRandomMedia(null).toMap();
            case "urban" -> new MediaServiceFactory().createUrbanDictionaryProvider().getRandomMedia(null).toMap();
            default -> throw new IllegalArgumentException("Unknown image type: " + imageType);
        };
    }
    
    /**
     * Check if a source is enabled in the admin configuration
     * Maps image types to their corresponding config keys
     */
    private boolean isSourceEnabledInConfig(LocalConfig config, String imageType) {
        String configKey = mapImageTypeToConfigKey(imageType);
        return config.isSourceEnabled(configKey);
    }
    
    /**
     * Map image types used in ImageOptions to config keys used in LocalConfig
     */
    private String mapImageTypeToConfigKey(String imageType) {
        return switch (imageType) {
            case "4chan" -> "4chan";
            case "picsum" -> "picsum";
            case "imgur" -> "imgur";
            case "reddit" -> "reddit";
            case "rule34xxx" -> "rule34xxx";
            case "tenor" -> "tenor";
            case "google" -> "google";
            case "movies" -> "tmdb_movie";
            case "tvshow" -> "tmdb_tv";
            case "youtube" -> "youtube";
            case "short" -> "youtube_shorts";
            case "urban" -> "urban_dictionary";
            default -> imageType; // fallback to original name
        };
    }
}