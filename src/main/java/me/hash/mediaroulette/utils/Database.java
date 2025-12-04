package me.hash.mediaroulette.utils;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import java.util.ArrayList;

import org.bson.Document;

public class Database {
    private final MongoClient mongoClient;
    private final MongoDatabase database;

    public Database(String connectionString, String databaseName) {
        this.mongoClient = MongoClients.create(connectionString);
        this.database = mongoClient.getDatabase(databaseName);
    }

    public boolean databaseExists() {
        return mongoClient.listDatabaseNames().into(new ArrayList<String>()).contains(database.getName());
    }

    public MongoDatabase getDatabase() {
        return database;
    }

    public MongoCollection<Document> getCollection(String collectionName) {
        return database.getCollection(collectionName);
    }

    public boolean collectionExists(String collectionName) {
        return database.listCollectionNames().into(new ArrayList<String>()).contains(collectionName);
    }

    public void createCollection(String collectionName) {
        database.createCollection(collectionName);
    }

    public void deleteCollection(String collectionName) {
        MongoCollection<Document> collection = getCollection(collectionName);
        collection.drop();
    }

    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}

