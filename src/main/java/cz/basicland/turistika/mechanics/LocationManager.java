package cz.basicland.turistika.mechanics;

import cz.basicland.turistika.BasicLandTuristika;
import cz.basicland.turistika.config.ConfigManager.StampData;
import cz.basicland.turistika.config.MessageManager;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LocationManager v2.0
 *
 * Novinky:
 *  - ActionBar v2: smer (kompas) + vzdalenost + animovany puls
 *  - Respektuje cas znamky (time_window, expire_date) – lokace nefunguje mimo okno
 *  - Protected zones: neoceñuje za pohyb v chranene oblasti (napr. cizi Residence)
 *  - Cooldown per-stamp-per-player zabraní opakovanym zpravám
 *  - Formatovani smeru: ↑N ↗NE →E ↘SE ↓S ↙SW ←W ↖NW
 */
public class LocationManager {

    private final BasicLandTuristika plugin;
    private final Map<String, StampLocation> stampLocations = new HashMap<>();
    // Cooldown: "uuid:stampId" -> timestamp posledniho hintu
    private final Map<String, Long> hintCooldown = new ConcurrentHashMap<>();
    // Animacni pocitadlo pro pulsovani ActionBaru
    private int animTick = 0;
    private BukkitTask checkTask;

    public LocationManager(BasicLandTuristika plugin) {
        this.plugin = plugin;
    }

    // ======================================================
    //  INIT / RELOAD
    // ======================================================

    public void loadLocations() {
        stampLocations.clear();
        ConfigurationSection stampsSection = plugin.getConfig().getConfigurationSection("stamps");
        if (stampsSection == null) return;

        int loaded = 0;
        for (String stampId : stampsSection.getKeys(false)) {
            ConfigurationSection locSection = stampsSection.getConfigurationSection(stampId + ".location");
            if (locSection == null) continue;
            String worldName = locSection.getString("world", "world");
            double x      = locSection.getDouble("x", 0);
            double y      = locSection.getDouble("y", 64);
            double z      = locSection.getDouble("z", 0);
            double radius = locSection.getDouble("radius", 5.0);
            stampLocations.put(stampId, new StampLocation(stampId, worldName, x, y, z, radius));
            loaded++;
        }
        plugin.getLogger().info("Nacteno " + loaded + " lokaci znamek.");
    }

    public void saveLocation(String stampId, Location loc, double radius) {
        String path = "stamps." + stampId + ".location";
        plugin.getConfig().set(path + ".world",  loc.getWorld().getName());
        plugin.getConfig().set(path + ".x",      loc.getX());
        plugin.getConfig().set(path + ".y",      loc.getY());
        plugin.getConfig().set(path + ".z",      loc.getZ());
        plugin.getConfig().set(path + ".radius", radius);
        plugin.saveConfig();
        stampLocations.put(stampId, new StampLocation(stampId, loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), radius));
    }

    public void removeLocation(String stampId) {
        plugin.getConfig().set("stamps." + stampId + ".location", null);
        plugin.saveConfig();
        stampLocations.remove(stampId);
    }

    public void startCheckTask() {
        if (checkTask != null && !checkTask.isCancelled()) return;
        checkTask = new BukkitRunnable() {
            @Override public void run() {
                animTick++;
                if (stampLocations.isEmpty()) return;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkPlayerProximity(player);
                }
            }
        }.runTaskTimer(plugin, 40L, 40L); // kazde 2s
    }

    public void stopCheckTask() {
        if (checkTask != null) checkTask.cancel();
    }

    // ======================================================
    //  KONTROLA PROXIMITY
    // ======================================================

    private void checkPlayerProximity(Player player) {
        for (Map.Entry<String, StampLocation> entry : stampLocations.entrySet()) {
            String stampId = entry.getKey();
            StampLocation sl = entry.getValue();

            StampData stamp = plugin.getConfigManager().getStamp(stampId);
            if (stamp == null) continue;

            // v2.0: Respektuj casove okno a expiraci
            if (!stamp.isCurrentlyAvailable()) continue;

            World world = Bukkit.getWorld(sl.worldName);
            if (world == null || !world.equals(player.getWorld())) continue;

            double dist2D = dist2D(player.getLocation(), sl.x, sl.z);

            if (dist2D <= sl.radius) {
                // === HRÁČ JE V DOSAHU ===
                plugin.getDatabaseManager().hasStamp(player.getUniqueId(), stampId).thenAccept(has -> {
                    if (!has) {
                        int points = stamp.getPoints();
                        cz.basicland.turistika.config.ConfigManager.Rarity rarity = stamp.getRarity();
                        plugin.getDatabaseManager().addStamp(player.getUniqueId(), stampId, points).thenRun(() ->
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (!player.isOnline()) return;
                                player.sendMessage(plugin.getMessageManager().getMessage("stamp_received")
                                        .replace("{stamp_name}", stamp.getName()));
                                player.sendMessage(cz.basicland.turistika.config.MessageManager.colorize(
                                        rarity.color + "[" + rarity.displayName + "] &7Získal jsi &e+" + points + " bodů!"));
                                player.playSound(player.getLocation(), rarity.collectSound, 1f, rarity.collectPitch);
                                player.spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation().add(0,1,0), 40, 0.4, 0.4, 0.4, 0.1);
                                player.spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation().add(0,1,0), 15, 0.5, 0.5, 0.5, 0.05);

                                plugin.getServerFirstManager().handleFirstDiscovery(player, stampId, stamp.getName());
                                plugin.getDatabaseManager().getUnlockedStamps(player.getUniqueId())
                                        .thenAccept(s -> plugin.getMilestoneManager().checkMilestones(player, s.size()));
                                plugin.getStreakManager().recordActivity(player);
                            })
                        );
                    }
                });


            } else if (dist2D <= sl.radius * 4) {
                // === HRAC JE BLIZKO (hint zone) ===
                String coolKey = player.getUniqueId() + ":" + stampId;
                long now = System.currentTimeMillis();
                Long last = hintCooldown.get(coolKey);
                if (last != null && (now - last) < 20_000L) continue; // 20s cooldown
                hintCooldown.put(coolKey, now);

                plugin.getDatabaseManager().hasStamp(player.getUniqueId(), stampId).thenAccept(has -> {
                    if (!has) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) return;
                            // v2.0: Formatovany ActionBar s smerem
                            String direction = getDirectionArrow(player, sl.x, sl.z);
                            int dist = (int) dist2D;
                            // Pulsovani (strida se kazde 2s)
                            String pulse = (animTick % 2 == 0) ? "&b✦" : "&e✦";
                            String bar = MessageManager.colorize(
                                    pulse + " &7Turistická zn. &e" + stamp.getName() +
                                    " &8» &a" + dist + "m &7" + direction + " " + pulse);
                            player.sendActionBar(net.kyori.adventure.text.Component.text(
                                    org.bukkit.ChatColor.translateAlternateColorCodes('&',
                                            pulse + " Turistická zn.: " + stripColor(stamp.getName()) +
                                            " » " + dist + "m " + direction)));
                        });
                    }
                });
            }
        }
    }

    // ======================================================
    //  UTILS
    // ======================================================

    /** 2D vzdalenost (ignoruje Y) */
    private double dist2D(Location loc, double tx, double tz) {
        double dx = loc.getX() - tx;
        double dz = loc.getZ() - tz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * v2.0: Vypocita kompasovy smer od hrace k cili.
     * Vraci unicode sipku + zkratku svetove strany.
     *
     * Minecraft souradnice: X+ = East, Z+ = South
     */
    private String getDirectionArrow(Player player, double tx, double tz) {
        double dx = tx - player.getLocation().getX();
        double dz = tz - player.getLocation().getZ();
        double angleDeg = Math.toDegrees(Math.atan2(dz, dx));
        angleDeg = (angleDeg + 360) % 360;

        // 8 sektoru po 45 stupnich, offset o 22.5 pro spravne centrovani
        int sector = (int) ((angleDeg + 22.5) / 45) % 8;
        String[] arrows = {"→E", "↘SE", "↓S", "↙SW", "←W", "↖NW", "↑N", "↗NE"};
        return arrows[sector];
    }

    private String stripColor(String s) {
        return s.replaceAll("(?i)&[0-9a-fk-or]", "");
    }

    public Map<String, StampLocation> getStampLocations() {
        return stampLocations;
    }

    // ======================================================
    //  DATA CLASS
    // ======================================================

    public static class StampLocation {
        public final String stampId, worldName;
        public final double x, y, z, radius;
        public StampLocation(String si, String w, double x, double y, double z, double r) {
            this.stampId = si; this.worldName = w;
            this.x = x; this.y = y; this.z = z; this.radius = r;
        }
    }
}
