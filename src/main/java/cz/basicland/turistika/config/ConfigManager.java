package cz.basicland.turistika.config;

import cz.basicland.turistika.BasicLandTuristika;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nacita a cachuje vsechna nastaveni z config.yml.
 * Pouziva thread-safe Java Time API (LocalDateTime + DateTimeFormatter).
 */
public class ConfigManager {

    // Thread-safe formatter - na rozdil od SimpleDateFormat
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // Formatovaci pattern pro zobrazeni hracum (CZ styl)
    public static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final BasicLandTuristika plugin;
    private final Map<String, StampData> stamps = new HashMap<>();

    public ConfigManager(BasicLandTuristika plugin) {
        this.plugin = plugin;
        loadStamps();
    }

    public void reload() {
        plugin.reloadConfig();
        loadStamps();
    }

    private void loadStamps() {
        stamps.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("stamps");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String name = section.getString(key + ".name", "Neznama znamka");
            List<String> lore = section.getStringList(key + ".lore");
            String matStr = section.getString(key + ".material", "PAPER");
            Material material = Material.matchMaterial(matStr);
            if (material == null) {
                plugin.getLogger().warning("Neplatny material '" + matStr + "' pro znamku '" + key + "'. Pouzivam PAPER.");
                material = Material.PAPER;
            }
            int customModelData = section.getInt(key + ".custom_model_data", 0);

            LocalDateTime unlockDate = null;
            if (section.contains(key + ".unlock_date")) {
                try {
                    unlockDate = LocalDateTime.parse(section.getString(key + ".unlock_date"), DATE_FORMAT);
                } catch (DateTimeParseException e) {
                    plugin.getLogger().warning("Spatny format data pro znamku '" + key + "'. Ocekavan format: yyyy-MM-dd HH:mm:ss");
                }
            }

            stamps.put(key, new StampData(key, name, lore, material, customModelData, unlockDate));
        }
        plugin.getLogger().info("Nacteno " + stamps.size() + " turistickych znamek z config.yml.");
    }

    public StampData getStamp(String id) {
        return stamps.get(id);
    }

    public Map<String, StampData> getStamps() {
        return stamps;
    }

    public String getGuiTitle() {
        return plugin.getConfig().getString("gui.title", "&8Turisticky Denik");
    }

    // ======================================================
    //  DATA CLASS
    // ======================================================

    public static class StampData {
        private final String id;
        private final String name;
        private final List<String> lore;
        private final Material material;
        private final int customModelData;
        private final LocalDateTime unlockDate;

        public StampData(String id, String name, List<String> lore, Material material, int customModelData, LocalDateTime unlockDate) {
            this.id = id;
            this.name = name;
            this.lore = lore;
            this.material = material;
            this.customModelData = customModelData;
            this.unlockDate = unlockDate;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public List<String> getLore() { return lore; }
        public Material getMaterial() { return material; }
        public int getCustomModelData() { return customModelData; }
        public LocalDateTime getUnlockDate() { return unlockDate; }

        /** @return true pokud ma znamka datum zamku a ten jeste neprobehl */
        public boolean isLocked() {
            return unlockDate != null && LocalDateTime.now().isBefore(unlockDate);
        }
    }
}
