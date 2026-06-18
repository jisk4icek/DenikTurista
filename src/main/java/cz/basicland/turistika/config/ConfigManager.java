package cz.basicland.turistika.config;

import cz.basicland.turistika.BasicLandTuristika;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;

import java.time.*;
import java.time.format.*;
import java.util.*;

/**
 * Načítá a cachuje všechna nastavení z config.yml.
 * v2.4: Přidán Rarity systém (COMMON/RARE/EPIC/LEGENDARY) s bodovým ohodnocením.
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
            String name = section.getString(key + ".name", "Neznámá známka");
            List<String> lore = section.getStringList(key + ".lore");
            Material material = parseMaterial(section.getString(key + ".material", "PAPER"), key);
            int customModelData = section.getInt(key + ".custom_model_data", 0);

            LocalDateTime unlockDate = parseDateTime(section.getString(key + ".unlock_date"), key, "unlock_date");
            LocalDateTime expireDate = parseDateTime(section.getString(key + ".expire_date"),  key, "expire_date");

            String windowFrom  = section.getString(key + ".time_window.from",  null);
            String windowUntil = section.getString(key + ".time_window.until", null);

            // v2.4: Rarity
            Rarity rarity = parseRarity(section.getString(key + ".rarity", "COMMON"), key);

            stamps.put(key, new StampData(key, name, lore, material, customModelData,
                    unlockDate, expireDate, windowFrom, windowUntil, rarity));
        }
        plugin.getLogger().info("Načteno " + stamps.size() + " turistických známek z config.yml.");
    }

    private Material parseMaterial(String s, String key) {
        Material m = Material.matchMaterial(s == null ? "PAPER" : s);
        if (m == null) {
            plugin.getLogger().warning("Neplatný material '" + s + "' pro známku '" + key + "'. Používám PAPER.");
            return Material.PAPER;
        }
        return m;
    }

    private LocalDateTime parseDateTime(String s, String key, String field) {
        if (s == null || s.isEmpty()) return null;
        try {
            return LocalDateTime.parse(s, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            plugin.getLogger().warning("Špatný formát " + field + " pro známku '" + key + "'. Očekáván: yyyy-MM-dd HH:mm:ss");
            return null;
        }
    }

    private Rarity parseRarity(String s, String key) {
        if (s == null) return Rarity.COMMON;
        try {
            return Rarity.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Neznámá rarity '" + s + "' pro známku '" + key + "'. Používám COMMON.");
            return Rarity.COMMON;
        }
    }

    public StampData getStamp(String id)        { return stamps.get(id); }
    public Map<String, StampData> getStamps()   { return stamps; }
    public String getGuiTitle() {
        return plugin.getConfig().getString("gui.title", "&8Turistický Deník");
    }

    // ======================================================
    //  RARITY ENUM (v2.4)
    // ======================================================

    /**
     * Systém vzácnosti turistických známek.
     *
     * Každá rarity má:
     *  - color      – ChatColor kód pro označení v GUI / zprávách
     *  - displayName – česky pro zobrazení hráči
     *  - points     – bodová hodnota (základ pro žebříček)
     *  - collectSound  – zvuk při sebrání
     *  - collectPitch  – výška zvuku
     *  - glowColor  – ChatColor pro glowing ArmorStand outline (Scoreboard Team)
     *
     * Config.yml příklad:
     *   stamps:
     *     my_stamp:
     *       rarity: RARE    # COMMON / RARE / EPIC / LEGENDARY
     */
    public enum Rarity {
        COMMON(
                "&f", "Běžná", 1,
                Sound.ENTITY_PLAYER_LEVELUP, 1.0f,
                org.bukkit.ChatColor.WHITE
        ),
        RARE(
                "&9", "Vzácná", 3,
                Sound.UI_TOAST_IN, 1.2f,
                org.bukkit.ChatColor.BLUE
        ),
        EPIC(
                "&5", "Epická", 5,
                Sound.ENTITY_PLAYER_LEVELUP, 1.6f,
                org.bukkit.ChatColor.DARK_PURPLE
        ),
        LEGENDARY(
                "&6", "Legendární", 10,
                Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f,
                org.bukkit.ChatColor.GOLD
        );

        public final String color;
        public final String displayName;
        public final int points;
        public final Sound collectSound;
        public final float collectPitch;
        public final org.bukkit.ChatColor glowColor;

        Rarity(String color, String displayName, int points,
               Sound collectSound, float collectPitch,
               org.bukkit.ChatColor glowColor) {
            this.color = color;
            this.displayName = displayName;
            this.points = points;
            this.collectSound = collectSound;
            this.collectPitch = collectPitch;
            this.glowColor = glowColor;
        }

        /** Řetězec pro zobrazení v GUI (např. "&f[Běžná &f1b]") */
        public String getBadge() {
            return color + "[" + displayName + " " + color + points + "b]";
        }
    }

    // ======================================================
    //  STAMP STATUS ENUM
    // ======================================================

    public enum StampStatus {
        FOUND,        // Hráč už známku má
        AVAILABLE,    // Aktivní, hráč ji ještě nemá
        LOCKED,       // Časový zámek – unlock_date ještě nenastal
        EXPIRED,      // Datum platnosti vypršelo
        OUT_OF_WINDOW // Mimo denní časové okno
    }

    // ======================================================
    //  STAMP DATA CLASS
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
        private final Rarity rarity; // v2.4

        public StampData(String id, String name, List<String> lore, Material material, int customModelData,
                         LocalDateTime unlockDate, LocalDateTime expireDate,
                         String timeWindowFrom, String timeWindowUntil,
                         Rarity rarity) {
            this.id = id; this.name = name; this.lore = lore;
            this.material = material; this.customModelData = customModelData;
            this.unlockDate = unlockDate; this.expireDate = expireDate;
            this.timeWindowFrom = timeWindowFrom; this.timeWindowUntil = timeWindowUntil;
            this.rarity = rarity != null ? rarity : Rarity.COMMON;
        }

        public String getId()              { return id; }
        public String getName()            { return name; }
        public List<String> getLore()      { return lore; }
        public Material getMaterial()      { return material; }
        public int getCustomModelData()    { return customModelData; }
        public LocalDateTime getUnlockDate() { return unlockDate; }
        public LocalDateTime getExpireDate() { return expireDate; }
        public String getTimeWindowFrom()  { return timeWindowFrom; }
        public String getTimeWindowUntil() { return timeWindowUntil; }
        public Rarity getRarity()          { return rarity; }
        public int getPoints()             { return rarity.points; }

        public boolean isLocked() {
            return unlockDate != null && LocalDateTime.now().isBefore(unlockDate);
        }

        public boolean isExpired() {
            return expireDate != null && LocalDateTime.now().isAfter(expireDate);
        }

        public boolean isOutOfWindow() {
            if (timeWindowFrom == null || timeWindowUntil == null) return false;
            try {
                LocalTime now   = LocalTime.now();
                LocalTime from  = LocalTime.parse(timeWindowFrom,  TIME_FORMAT);
                LocalTime until = LocalTime.parse(timeWindowUntil, TIME_FORMAT);
                if (from.isBefore(until)) {
                    return now.isBefore(from) || now.isAfter(until);
                } else {
                    return now.isAfter(until) && now.isBefore(from);
                }
            } catch (DateTimeParseException e) {
                return false;
            }
        }

        public StampStatus getStatus(boolean playerHasStamp) {
            if (playerHasStamp) return StampStatus.FOUND;
            if (isLocked())      return StampStatus.LOCKED;
            if (isExpired())     return StampStatus.EXPIRED;
            if (isOutOfWindow()) return StampStatus.OUT_OF_WINDOW;
            return StampStatus.AVAILABLE;
        }

        public boolean isCurrentlyAvailable() {
            return !isLocked() && !isExpired() && !isOutOfWindow();
        }

        public String getWindowString() {
            if (timeWindowFrom == null || timeWindowUntil == null) return "";
            return timeWindowFrom + " – " + timeWindowUntil;
        }
    }
}
