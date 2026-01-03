package me.hash.mediaroulette.utils.resources;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Manifest model for tracking external resource versions.
 * Downloaded from GitHub to determine which resources need updating.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceManifest {
    
    @JsonProperty("version")
    private String version;
    
    @JsonProperty("lastUpdated")
    private long lastUpdated;
    
    @JsonProperty("resources")
    private Map<String, ResourceEntry> resources;
    
    public ResourceManifest() {
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public long getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    
    public Map<String, ResourceEntry> getResources() {
        return resources;
    }
    
    public void setResources(Map<String, ResourceEntry> resources) {
        this.resources = resources;
    }
    
    public List<ResourceEntry> getResourceList() {
        if (resources == null) return List.of();
        return resources.entrySet().stream()
                .map(e -> {
                    ResourceEntry entry = e.getValue();
                    entry.setPath(e.getKey());
                    return entry;
                })
                .toList();
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResourceEntry {
        
        private String path;
        
        @JsonProperty("sha256")
        private String sha256;
        
        @JsonProperty("size")
        private long size;
        
        @JsonProperty("required")
        private boolean required = true;
        
        public ResourceEntry() {
        }
        
        public String getPath() {
            return path;
        }
        
        public void setPath(String path) {
            this.path = path;
        }
        
        public String getSha256() {
            return sha256;
        }
        
        public void setSha256(String sha256) {
            this.sha256 = sha256;
        }
        
        public long getSize() {
            return size;
        }
        
        public void setSize(long size) {
            this.size = size;
        }
        
        public boolean isRequired() {
            return required;
        }
        
        public void setRequired(boolean required) {
            this.required = required;
        }
    }
}
