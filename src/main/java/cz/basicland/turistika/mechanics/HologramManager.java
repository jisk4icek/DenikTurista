package cz.basicland.turistika.mechanics;

import cz.basicland.turistika.BasicLandTuristika;
import cz.basicland.turistika.config.MessageManager;
import cz.basicland.turistika.database.DatabaseManager.HologramData;
import org.bukkit.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Spravuje interaktivni hologramy pomoci moderni entity TextDisplay (MC 1.20+).
 * Hologram zobrazuje live TOP hracu a jeho lokace je persistovana v SQLite.
 *
 * Pouziti ArmorStandu je zakazano - TextDisplay je spravny moderni pristup.
 */
public class HologramManager {

    // Prefix hologram ID v databazi pro TOP leaderboard hologram
    public static final String LEADERBOARD_HOLOGRAM_ID = "leaderboard_main";

    private final BasicLandTuristika plugin;
    // Cache aktivnich hologramu: id -> UUID entity ve svete
    private final Map<String, UUID> activeHolograms = new HashMap<>();
    private BukkitTask updateTask;

    public HologramManager(BasicLandTuristika plugin) {
        this.plugin = plugin;
    }

    // ======================================================
    //  INICIALIZACE - NACTENI HOLOGRAMU PO RESTARTU
    // ======================================================

    /**
     * Po startu serveru nacte vsechny ulozene hologramy z SQLite,
     * zkusi najit jejich existujici entity, nebo je vytvorí znovu.
     */
    public void loadFromDatabase() {
        plugin.getDatabaseManager().loadHolograms().thenAccept(hologramList -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (HologramData data : hologramList) {
                    World world = Bukkit.getWorld(data.world);
                    if (world == null) {
                        plugin.getLogger().warning("Hologram '" + data.id + "': svet '" + data.world + "' nenalezen!");
                        continue;
                    }

                    // Zkusit najit existujici entitu
                    boolean found = false;
                    if (data.entityUUID != null) {
                        Entity entity = Bukkit.getEntity(data.entityUUID);
                        if (entity instanceof TextDisplay && !entity.isDead()) {
                            activeHolograms.put(data.id, entity.getUniqueId());
                            found = true;
                            plugin.getLogger().info("Hologram '" + data.id + "' obnoven z existujici entity.");
                        }
                    }

                    // Pokud entita neexistuje, vytvorime novou na ulozene lokaci
                    if (!found) {
                        Location loc = new Location(world, data.x, data.y, data.z);
                        TextDisplay td = spawnTextDisplay(loc, "§7Načítám...");
                        activeHolograms.put(data.id, td.getUniqueId());
                        plugin.getDatabaseManager().updateHologramEntityUUID(data.id, td.getUniqueId());
                        plugin.getLogger().info("Hologram '" + data.id + "' znovu vytvoren na " + data.x + " " + data.y + " " + data.z);
                    }
                }

                // Spustit periodicke aktualizace textu
                startUpdateTask();
            });
        });
    }

    // ======================================================
    //  VYTVORENI HOLOGRAMU
    // ======================================================

    /**
     * Vytvorí nový leaderboard hologram na dane lokaci a ulozi ho do SQLite.
     * @param location Misto spawnu hologramu.
     * @param hologramId Unikatni ID pro databazi.
     */
    public void createHologram(Location location, String hologramId) {
        // Pokud uz existuje, smazat stary
        if (activeHolograms.containsKey(hologramId)) {
            removeHologram(hologramId);
        }

        TextDisplay td = spawnTextDisplay(location, "§7Generuji žebříček...");
        UUID entityUUID = td.getUniqueId();

        activeHolograms.put(hologramId, entityUUID);

        plugin.getDatabaseManager().saveHologram(
                hologramId,
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                entityUUID
        );

        // Okamzite naplnit content
        updateHologramContent(hologramId, entityUUID);

        // Ujistit se, ze bezí update task
        startUpdateTask();
        plugin.getLogger().info("Hologram '" + hologramId + "' spawnut.");
    }

    /**
     * Odstrani hologram ze sveta a ze SQLite databaze.
     */
    public void removeHologram(String hologramId) {
        UUID uuid = activeHolograms.remove(hologramId);
        if (uuid != null) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) entity.remove();
        }
        plugin.getDatabaseManager().deleteHologram(hologramId);
        plugin.getLogger().info("Hologram '" + hologramId + "' odstranen.");
    }

    // ======================================================
    //  AKTUALIZACE OBSAHU
    // ======================================================

    /**
     * Spusti BukkitRunnable, ktery kazde 2 minuty aktualizuje text hologramu.
     */
    private void startUpdateTask() {
        if (updateTask != null && !updateTask.isCancelled()) return; // Jiz bezi

        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<String, UUID> entry : activeHolograms.entrySet()) {
                    updateHologramContent(entry.getKey(), entry.getValue());
                }
            }
        }.runTaskTimer(plugin, 20L * 10, 20L * 120); // Prvni update za 10s, pak kazde 2 minuty
    }

    /**
     * Nacte aktualni TOP 10 hracu z DB a aktualizuje text TextDisplay entity.
     */
    private void updateHologramContent(String hologramId, UUID entityUUID) {
        plugin.getDatabaseManager().getTopPlayers(10).thenAccept(top -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Entity entity = Bukkit.getEntity(entityUUID);
                if (!(entity instanceof TextDisplay)) return;
                TextDisplay td = (TextDisplay) entity;

                StringBuilder sb = new StringBuilder();
                sb.append(MessageManager.colorize("&b&l🏆 TURISTICKÝ ŽEBŘÍČEK 🏆\n"));
                sb.append(MessageManager.colorize("&8&m                    \n"));

                if (top.isEmpty()) {
                    sb.append(MessageManager.colorize("&7Zatím žádní hráči.\n"));
                } else {
                    String[] medals = {"&e⬛", "&f⬛", "&6⬛"};
                    for (int i = 0; i < top.size(); i++) {
                        String rankIcon = i < 3 ? MessageManager.colorize(medals[i]) : "&7" + (i + 1) + ".";
                        sb.append(MessageManager.colorize(rankIcon + " &a" + top.get(i).getKey() +
                                " &7- &e" + top.get(i).getValue() + " &7zn.\n"));
                    }
                }

                sb.append(MessageManager.colorize("\n&8Aktualizováno: &7nyní"));
                td.setText(sb.toString());
            });
        });
    }

    // ======================================================
    //  HELPER - VYTVORENI TextDisplay ENTITY
    // ======================================================

    private TextDisplay spawnTextDisplay(Location location, String initialText) {
        return location.getWorld().spawn(location, TextDisplay.class, td -> {
            td.setText(initialText);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setBillboard(Display.Billboard.CENTER); // Vzdy otocen k hracovi
            td.setDefaultBackground(false);
            td.setBackgroundColor(Color.fromARGB(160, 0, 0, 0)); // Pruhledne cerne pozadi
            td.setShadowed(true);
            td.setLineWidth(200); // Sirka textu
            td.setPersistent(true);
        });
    }

    public Map<String, UUID> getActiveHolograms() {
        return Collections.unmodifiableMap(activeHolograms);
    }

    public void shutdown() {
        if (updateTask != null) updateTask.cancel();
    }
}
