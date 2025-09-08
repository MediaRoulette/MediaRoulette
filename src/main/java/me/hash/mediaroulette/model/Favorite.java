package me.hash.mediaroulette.model;

public class Favorite {
   private int id;
   private String title;
   private String description;
   private String image;
   private String type;
   private Integer accentColor; // Optional stored accent color (RGB int), may be null

   public Favorite(int id, String title, String description, String image, String type) {
       this.id = id;
       this.title = title;
       this.description = description;
       this.image = image;
       this.type = type;
   }

   public Favorite(int id, String title, String description, String image, String type, Integer accentColor) {
       this(id, title, description, image, type);
       this.accentColor = accentColor;
   }

   // Backward-compat constructor for old code paths
   public Favorite(int id, String description, String image, String type) {
       this(id, "Favorite", description, image, type);
   }

   // --- Getters and Setters ---
   public int getId() { return id; }
   public void setId(int id) { this.id = id; }

   public String getTitle() { return title; }
   public void setTitle(String title) { this.title = title; }

   public String getDescription() { return description; }
   public void setDescription(String description) { this.description = description; }

   public String getImage() { return image; }
   public void setImage(String image) { this.image = image; }

   public String getType() { return type; }
   public void setType(String type) { this.type = type; }

   public Integer getAccentColor() { return accentColor; }
   public void setAccentColor(Integer accentColor) { this.accentColor = accentColor; }
}
