package me.hash.mediaroulette.content.factory;

import me.hash.mediaroulette.Main;
import me.hash.mediaroulette.content.http.HttpClientWrapper;
import me.hash.mediaroulette.content.provider.MediaProvider;
import me.hash.mediaroulette.content.provider.impl.gifs.TenorProvider;
import me.hash.mediaroulette.content.provider.impl.images.*;
import me.hash.mediaroulette.content.provider.impl.videos.TMDBMovieProvider;
import me.hash.mediaroulette.content.provider.impl.videos.TMDBTvProvider;
import me.hash.mediaroulette.content.provider.impl.videos.YouTubeProvider;
import me.hash.mediaroulette.content.provider.impl.videos.YouTubeShortsProvider;

import me.hash.mediaroulette.content.provider.impl.text.UrbanDictionaryProvider;

public class MediaServiceFactory {
    private static final HttpClientWrapper SHARED_HTTP_CLIENT = new HttpClientWrapper();
    
    private final HttpClientWrapper httpClient;
    
    public MediaServiceFactory() {
        this.httpClient = SHARED_HTTP_CLIENT;
    }
    
    public MediaProvider createUrbanDictionaryProvider() {
        return new UrbanDictionaryProvider(httpClient);
    }

    public MediaProvider createFourChanProvider() {
        return new FourChanProvider(httpClient);
    }

    public MediaProvider createPicsumProvider() {
        return new PicsumProvider(httpClient);
    }

    public MediaProvider createImgurProvider() {
        return new ImgurProvider(httpClient);
    }

    public MediaProvider createGoogleProvider() {
        return new GoogleProvider(httpClient, Main.getEnv("GOOGLE_API_KEY"), Main.getEnv("GOOGLE_CX"));
    }

    public MediaProvider createTenorProvider() {
        return new TenorProvider(httpClient, Main.getEnv("TENOR_API"));
    }


    public MediaProvider createRule34Provider() {
        return new Rule34Provider(httpClient);
    }

    public MediaProvider createTMDBTvProvider() {
        return new TMDBTvProvider(httpClient, Main.getEnv("TMDB_API"));
    }

    public MediaProvider createTMDBMovieProvider() {
        return new TMDBMovieProvider(httpClient, Main.getEnv("TMDB_API"));
    }

    public MediaProvider createYouTubeProvider() {
        return new YouTubeProvider(httpClient, Main.getEnv("GOOGLE_API_KEY"));
    }

    public MediaProvider createYouTubeShortsProvider() {
        return new YouTubeShortsProvider(httpClient, Main.getEnv("GOOGLE_API_KEY"));
    }
}