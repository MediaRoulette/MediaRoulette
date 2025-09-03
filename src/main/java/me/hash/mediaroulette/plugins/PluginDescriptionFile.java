package me.hash.mediaroulette.plugins;

import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class PluginDescriptionFile {
    private String name;
    private String version;
    private String main;
    private String description;
    private String author;
    private List<String> authors;
    private List<String> depend;
    private List<String> softDepend;

    public PluginDescriptionFile(InputStream stream) {
        loadFrom(stream);
    }

    @SuppressWarnings("unchecked")
    private void loadFrom(InputStream stream) {
        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(stream);

        this.name = (String) data.get("name");
        this.version = (String) data.get("version");
        this.main = (String) data.get("main");
        this.description = (String) data.get("description");
        this.author = (String) data.get("author");
        this.authors = (List<String>) data.get("authors");
        this.depend = normalizeStringList((List<String>) data.get("depend"));
        this.softDepend = normalizeStringList((List<String>) data.get("softdepend"));
    }
    private List<String> normalizeStringList(List<String> list) {
        if (list == null) {
            return null;
        }

        list.removeIf(item -> item == null || item.trim().isEmpty());
        return list.isEmpty() ? null : list;
    }

    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getMain() { return main; }
    public String getDescription() { return description; }
    public String getAuthor() { return author; }
    public List<String> getAuthors() { return authors; }
    public List<String> getDepend() { return depend; }
    public List<String> getSoftDepend() { return softDepend; }

    @Override
    public String toString() {
        return String.format("PluginDescriptionFile{name='%s', version='%s', main='%s', depend=%s}",
                name, version, main, depend);
    }
}