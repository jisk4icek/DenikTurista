package cz.basicland.turistika.config;

import cz.basicland.turistika.BasicLandTuristika;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.time.*;
import java.time.format.*;
import java.util.*;

/**
 * Nacita a cachuje vsechna nastaveni z config.yml.
 * v2.0: Pridana podpora expireDate, timeWindow (from/until) a ProtectedZones.
 */
public class ConfigManager {

    public static final DateTimeFormatter DATE_FORMAT    = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    public static final DateTimeFormatter TIME_FORMAT    = DateTimeFormatter.ofPattern("HH:mm");

    private final BasicLandTuristika plugin;
    private final Map<String, StampData> stamps = new LinkedHashMap<>();

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
            Material material = parseMaterial(section.getString(key + ".material", "PAPER"), key);
            int customModelData = section.getInt(key + ".custom_model_data", 0);

            LocalDateTime unlockDate = parseDateTime(section.getString(key + ".unlock_date"), key, "unlock_date");
            LocalDateTime expireDate = parseDateTime(section.getString(key + ".expire_date"), key, "expire_date");

            String windowFrom  = section.getString(key + ".time_window.from",  null);
            String windowUntil = section.getString(key + ".time_window.until", null);

            stamps.put(key, new StampData(key, name, lore, material, customModelData,
                    unlockDate, expireDate, windowFrom, windowUntil));
        }
        plugin.getLogger().info("Nacteno " + stamps.size() + " turistickych znamek z config.yml.");
    }

    private Material parseMaterial(String s, String key) {
        Material m = Material.matchMaterial(s == null ? "PAPER" : s);
        if (m == null) {
            plugin.getLogger().warning("Neplatny material '" + s + "' pro znamku '" + key + "'. Pouzivam PAPER.");
            return Material.PAPER;
        }
        return m;
    }

    private LocalDateTime parseDateTime(String s, String key, String field) {
        if (s == null || s.isEmpty()) return null;
        try {
            return LocalDateTime.parse(s, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            plugin.getLogger().warning("Spatny format " + field + " pro znamku '" + key + "'. Ocekavan: yyyy-MM-dd HH:mm:ss");
            return null;
        }
    }

    public StampData getStamp(String id) { return stamps.get(id); }
    public Map<String, StampData> getStamps() { return stamps; }
    public String getGuiTitle() {
        return plugin.getConfig().getString("gui.title", "&8Turisticky Denik");
    }

    // ======================================================
    //  STAMP STATUS ENUM
    // ======================================================

    public enum StampStatus {
        /** Hrac uz znamku ma */
        FOUND,
        /** Znamka je aktivni a hrac ji jeste nema */
        AVAILABLE,
        /** Casovy zamek – unlock_date jeste nenastal */
        LOCKED,
        /** Datum platnosti vyprselo (expire_date je v minulosti) */
        EXPIRED,
        /** Mimo denni casove okno (time_window) */
        OUT_OF_WINDOW
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
        private final LocalDateTime expireDate;
        private final String timeWindowFrom;
        private final String timeWindowUntil;

        public StampData(String id, String name, List<String> lore, Material material, int customModelData,
                         LocalDateTime unlockDate, LocalDateTime expireDate,
                         String timeWindowFrom, String timeWindowUntil) {
            this.id = id; this.name = name; this.lore = lore;
            this.material = material; this.customModelData = customModelData;
            this.unlockDate = unlockDate; this.expireDate = expireDate;
            this.timeWindowFrom = timeWindowFrom; this.timeWindowUntil = timeWindowUntil;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public List<String> getLore() { return lore; }
        public Material getMaterial() { return material; }
        public int getCustomModelData() { return customModelData; }
        public LocalDateTime getUnlockDate() { return unlockDate; }
        public LocalDateTime getExpireDate() { return expireDate; }
        public String getTimeWindowFrom() { return timeWindowFrom; }
        public String getTimeWindowUntil() { return timeWindowUntil; }

        /** Znamka je pred datumem odemceni */
        public boolean isLocked() {
            return unlockDate != null && LocalDateTime.now().isBefore(unlockDate);
        }

        /** Znamka uz neni platna (datum expirace probehl) */
        public boolean isExpired() {
            return expireDate != null && LocalDateTime.now().isAfter(expireDate);
        }

        /** Znamka je mimo denni casove okno */
        public boolean isOutOfWindow() {
            if (timeWindowFrom == null || timeWindowUntil == null) return false;
            try {
                LocalTime now   = LocalTime.now();
                LocalTime from  = LocalTime.parse(timeWindowFrom,  TIME_FORMAT);
                LocalTime until = LocalTime.parse(timeWindowUntil, TIME_FORMAT);
                if (from.isBefore(until)) {
                    return now.isBefore(from) || now.isAfter(until);
                } else {
                    // Okno presahuje pulnoc (napr 22:00 - 02:00)
                    return now.isAfter(until) && now.isBefore(from);
                }
            } catch (DateTimeParseException e) {
                return false;
            }
        }

        /**
         * Vrati stav znamky vuci hracovi.
         * @param playerHasStamp true pokud hrac tuto znamku uz vlastni
         */
        public StampStatus getStatus(boolean playerHasStamp) {
            if (playerHasStamp) return StampStatus.FOUND;
            if (isLocked())      return StampStatus.LOCKED;
            if (isExpired())     return StampStatus.EXPIRED;
            if (isOutOfWindow()) return StampStatus.OUT_OF_WINDOW;
            return StampStatus.AVAILABLE;
        }

        /** Znamka je prave dostupna (neni zamcena, nevyprsela, je v okne) */
        public boolean isCurrentlyAvailable() {
            return !isLocked() && !isExpired() && !isOutOfWindow();
        }

        /** Vrati formatovany retezec casoveho okna pro zobrazeni v GUI */
        public String getWindowString() {
            if (timeWindowFrom == null || timeWindowUntil == null) return "";
            return timeWindowFrom + " – " + timeWindowUntil;
        }
    }
}
