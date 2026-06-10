package cz.basicland.turistika.config;

import cz.basicland.turistika.BasicLandTuristika;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MessageManager {

    private final BasicLandTuristika plugin;
    private File file;
    private FileConfiguration config;
    private final Map<String, String> messages = new HashMap<>();
    private String prefix = "";

    public MessageManager(BasicLandTuristika plugin) {
        this.plugin = plugin;
        loadFile();
    }

    public void reload() {
        loadFile();
    }

    private void loadFile() {
        file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        messages.clear();
        
        prefix = colorize(config.getString("prefix", "&8[&bTuristika&8]&7 "));
        
        for (String key : config.getKeys(false)) {
            if (!key.equals("prefix")) {
                messages.put(key, config.getString(key));
            }
        }
    }

    public String getMessage(String key) {
        String msg = messages.getOrDefault(key, "&cSprava nebyla nalezena: " + key);
        return colorize(prefix + msg);
    }
    
    public String getRawMessage(String key) {
        return colorize(messages.getOrDefault(key, "&c" + key));
    }

    public static String colorize(String str) {
        return ChatColor.translateAlternateColorCodes('&', str);
    }
}
