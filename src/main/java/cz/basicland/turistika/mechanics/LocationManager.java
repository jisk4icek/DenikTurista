package cz.basicland.turistika.mechanics;

import cz.basicland.turistika.BasicLandTuristika;
import cz.basicland.turistika.config.ConfigManager.StampData;
import cz.basicland.turistika.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Spravuje fyzicke lokace turistickych znamek ve svete.
 *
 * Kazda znamka muze mit v config.yml definovanou lokaci (souradnice + radius).
 * Kazde 2 sekundy se kontroluje, zda je nektery online hrac v dosahu lokace.
 * Pokud ano a znamku jeste nema, automaticky ji dostane.
 *
 * Konfigurace v config.yml:
 *   stamps:
 *     moje_znamka:
 *       location:
 *         world: "world"
 *         x: 100.0
 *         y: 64.0
 *         z: 200.0
 *         radius: 5.0
 */
public class LocationManager {

    private final BasicLandTuristika plugin;
    // Mapa stamp_id -> StampLocation (pouze znamky, ktere maji nastavenu lokaci)
    private final Map<String, StampLocation> stampLocations = new HashMap<>();
    // Cooldown mapa: uuid hrace -> cas posledniho oznámení (zabraní spamu zprav)
    private final Map<UUID, Long> notifyCooldown = new HashMap<>();
    private BukkitTask checkTask;

    public LocationManager(BasicLandTuristika plugin) {
        this.plugin = plugin;
    }

    /**
     * Nacte vsechny lokace ze sekce stamps.*.location v config.yml.
     */
    public void loadLocations() {
        stampLocations.clear();
        ConfigurationSection stampsSection = plugin.getConfig().getConfigurationSection("stamps");
        if (stampsSection == null) return;

        int loaded = 0;
        for (String stampId : stampsSection.getKeys(false)) {
            ConfigurationSection locSection = stampsSection.getConfigurationSection(stampId + ".location");
            if (locSection == null) continue;

            String worldName = locSection.getString("world", "world");
            double x = locSection.getDouble("x", 0);
            double y = locSection.getDouble("y", 64);
            double z = locSection.getDouble("z", 0);
            double radius = locSection.getDouble("radius", 5.0);

            stampLocations.put(stampId, new StampLocation(stampId, worldName, x, y, z, radius));
            loaded++;
        }
        plugin.getLogger().info("Nacteno " + loaded + " lokaci znamek.");
    }

    /**
     * Ulozi nebo prepise lokaci znamky v config.yml a v pameti.
     * Vola se pres /turista setlocation <id>.
     */
    public void saveLocation(String stampId, Location loc, double radius) {
        String path = "stamps." + stampId + ".location";
        plugin.getConfig().set(path + ".world", loc.getWorld().getName());
        plugin.getConfig().set(path + ".x", loc.getX());
        plugin.getConfig().set(path + ".y", loc.getY());
        plugin.getConfig().set(path + ".z", loc.getZ());
        plugin.getConfig().set(path + ".radius", radius);
        plugin.saveConfig();

        stampLocations.put(stampId, new StampLocation(stampId, loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(), radius));
    }

    /**
     * Odstrani lokaci znamky z config.yml i pameti.
     */
    public void removeLocation(String stampId) {
        plugin.getConfig().set("stamps." + stampId + ".location", null);
        plugin.saveConfig();
        stampLocations.remove(stampId);
    }

    /**
     * Spusti periodicky task, ktery kazde 2 sekundy kontroluje proximity.
     */
    public void startCheckTask() {
        if (checkTask != null && !checkTask.isCancelled()) return;

        checkTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (stampLocations.isEmpty()) return;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    checkPlayerProximity(player);
                }
            }
        }.runTaskTimer(plugin, 40L, 40L); // Kazde 2 sekundy
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

            // Pokud je znamka casove zamcena, preskocit
            if (stamp.isLocked()) continue;

            World world = Bukkit.getWorld(sl.worldName);
            if (world == null || !world.equals(player.getWorld())) continue;

            Location stampLoc = new Location(world, sl.x, sl.y, sl.z);

            // Kontrola vzdalenosti (2D - ignoruje Y pro hory apod.)
            double dist2D = Math.sqrt(
                    Math.pow(player.getLocation().getX() - sl.x, 2) +
                    Math.pow(player.getLocation().getZ() - sl.z, 2)
            );

            if (dist2D <= sl.radius) {
                // Je hrac v dosahu - zkontrolujeme zda uz znamku nema
                plugin.getDatabaseManager().hasStamp(player.getUniqueId(), stampId).thenAccept(has -> {
                    if (!has) {
                        // Hrac je v dosahu a jeste nema znamku -> pridat!
                        plugin.getDatabaseManager().addStamp(player.getUniqueId(), stampId).thenRun(() -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                // Efekty
                                player.sendMessage(plugin.getMessageManager().getMessage("stamp_received")
                                        .replace("{stamp_name}", stamp.getName()));
                                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                                player.spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation().add(0, 1, 0),
                                        40, 0.4, 0.4, 0.4, 0.1);
                                player.spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation().add(0, 1, 0),
                                        15, 0.5, 0.5, 0.5, 0.05);

                                // Server first + Milniky
                                plugin.getServerFirstManager().handleFirstDiscovery(player, stampId, stamp.getName());
                                plugin.getDatabaseManager().getUnlockedStamps(player.getUniqueId())
                                        .thenAccept(stamps -> plugin.getMilestoneManager()
                                                .checkMilestones(player, stamps.size()));
                            });
                        });
                    }
                });
            } else if (dist2D <= sl.radius * 3) {
                // Hrac je pobliz (3x radius = "blizko") -> zobrazit hint jednou za 30s
                long now = System.currentTimeMillis();
                String cooldownKey = player.getUniqueId().toString() + ":" + stampId;
                Long lastNotif = notifyCooldown.get(UUID.fromString(player.getUniqueId().toString()));

                if (lastNotif == null || (now - lastNotif) > 30_000L) {
                    notifyCooldown.put(player.getUniqueId(), now);
                    plugin.getDatabaseManager().hasStamp(player.getUniqueId(), stampId).thenAccept(has -> {
                        if (!has) {
                            Bukkit.getScheduler().runTask(plugin, () ->
                                    player.sendActionBar(MessageManager.colorize(
                                            "&b✦ &7Jsi blizko turisticke znamky &e" + stamp.getName() +
                                            " &7(" + (int)dist2D + "m) &b✦"
                                    ))
                            );
                        }
                    });
                }
            }
        }
    }

    public Map<String, StampLocation> getStampLocations() {
        return stampLocations;
    }

    // ======================================================
    //  DATA CLASS
    // ======================================================

    public static class StampLocation {
        public final String stampId;
        public final String worldName;
        public final double x, y, z;
        public final double radius;

        public StampLocation(String stampId, String worldName, double x, double y, double z, double radius) {
            this.stampId = stampId;
            this.worldName = worldName;
            this.x = x; this.y = y; this.z = z;
            this.radius = radius;
        }
    }
}
