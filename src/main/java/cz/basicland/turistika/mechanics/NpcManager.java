package cz.basicland.turistika.mechanics;

import cz.basicland.turistika.BasicLandTuristika;
import cz.basicland.turistika.config.ConfigManager.StampData;
import cz.basicland.turistika.config.MessageManager;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * NpcManager v2.0 – spravuje turisticke NPC postavy (Villager entity).
 *
 * NPC:
 *  - Spawnotelne pres /turista npc spawn <stamp_id>
 *  - Oznacene PDC tagem (turistika:npc_stamp_id)
 *  - Nezranitelne, bez AI, tiché, jméno viditelné
 *  - Hrac klikne pravym → dostane znamku (pokud ji nema + znamka dostupna)
 *  - Ulozena pozice v SQLite (tabulka npc_locations)
 *  - Po restartu automaticky respawnovany
 */
public class NpcManager implements Listener {

    // PDC klice
    public static final NamespacedKey NPC_STAMP_KEY = new NamespacedKey("turistika", "npc_stamp_id");
    public static final NamespacedKey NPC_MARKER     = new NamespacedKey("turistika", "turistika_npc");

    private final BasicLandTuristika plugin;
    // stamp_id -> UUID entity
    private final Map<String, UUID> activeNpcs = new HashMap<>();

    public NpcManager(BasicLandTuristika plugin) {
        this.plugin = plugin;
    }

    // ======================================================
    //  SPAWN / REMOVE
    // ======================================================

    /**
     * Spawne NPC na dane lokaci, ulozi do DB.
     */
    public boolean spawnNpc(String stampId, Location loc) {
        StampData stamp = plugin.getConfigManager().getStamp(stampId);
        if (stamp == null) return false;

        // Odstranic stary NPC pro tuto znamku, pokud existuje
        removeNpc(stampId);

        Villager npc = loc.getWorld().spawn(loc, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setVillagerType(Villager.Type.PLAINS);
            v.setProfession(Villager.Profession.NITWIT);
            v.setVillagerLevel(1);
            v.setCustomName(MessageManager.colorize("&e&l✦ " + stamp.getName() + " &e&l✦\n&7[Klikni pro získání]"));
            v.setCustomNameVisible(true);
            v.setRemoveWhenFarAway(false);
            v.setPersistent(true);
            // PDC tagy
            PersistentDataContainer pdc = v.getPersistentDataContainer();
            pdc.set(NPC_STAMP_KEY, PersistentDataType.STRING, stampId);
            pdc.set(NPC_MARKER,    PersistentDataType.BYTE,   (byte) 1);
        });

        activeNpcs.put(stampId, npc.getUniqueId());
        plugin.getDatabaseManager().saveNpc(stampId, loc, npc.getUniqueId());
        plugin.getLogger().info("NPC pro znamku '" + stampId + "' spawnut.");
        return true;
    }

    /**
     * Odstrani NPC pro danou znamku.
     */
    public void removeNpc(String stampId) {
        UUID uuid = activeNpcs.remove(stampId);
        if (uuid != null) {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null) e.remove();
        }
        plugin.getDatabaseManager().deleteNpc(stampId);
    }

    /**
     * Nacte vsechna NPC z databaze a respawnuje je po restartu.
     */
    public void loadFromDatabase() {
        plugin.getDatabaseManager().loadNpcs().thenAccept(list -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (var data : list) {
                    World world = Bukkit.getWorld(data.world);
                    if (world == null) continue;

                    // Zkus najit existujici entitu
                    if (data.entityUUID != null) {
                        Entity found = Bukkit.getEntity(data.entityUUID);
                        if (found instanceof Villager && !found.isDead()) {
                            activeNpcs.put(data.stampId, found.getUniqueId());
                            continue;
                        }
                    }

                    // Respawn
                    Location loc = new Location(world, data.x, data.y, data.z);
                    spawnNpc(data.stampId, loc);
                }
                plugin.getLogger().info("Nacteno " + list.size() + " turistickych NPC.");
            });
        });
    }

    public Map<String, UUID> getActiveNpcs() {
        return Collections.unmodifiableMap(activeNpcs);
    }

    public void shutdown() {
        // NPC entity zustanou ve svete – jsou persistent
        activeNpcs.clear();
    }

    // ======================================================
    //  LISTENERY
    // ======================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        PersistentDataContainer pdc = entity.getPersistentDataContainer();

        if (!pdc.has(NPC_MARKER, PersistentDataType.BYTE)) return;
        event.setCancelled(true);

        Player player = event.getPlayer();
        String stampId = pdc.get(NPC_STAMP_KEY, PersistentDataType.STRING);
        if (stampId == null) return;

        StampData stamp = plugin.getConfigManager().getStamp(stampId);
        if (stamp == null) {
            player.sendMessage(plugin.getMessageManager().getMessage("stamp_not_found").replace("{stamp_id}", stampId));
            return;
        }

        // Zkontroluj dostupnost znamky
        if (!stamp.isCurrentlyAvailable()) {
            if (stamp.isLocked()) {
                player.sendMessage(plugin.getMessageManager().getMessage("npc_stamp_locked")
                        .replace("{unlock_date}", stamp.getUnlockDate().format(cz.basicland.turistika.config.ConfigManager.DISPLAY_FORMAT)));
            } else if (stamp.isExpired()) {
                player.sendMessage(plugin.getMessageManager().getMessage("npc_stamp_expired"));
            } else if (stamp.isOutOfWindow()) {
                player.sendMessage(plugin.getMessageManager().getMessage("npc_stamp_out_of_window")
                        .replace("{window}", stamp.getWindowString()));
            }
            return;
        }

        // Zkontroluj zda hrac uz ma znamku
        plugin.getDatabaseManager().hasStamp(player.getUniqueId(), stampId).thenAccept(has -> {
            if (has) {
                Bukkit.getScheduler().runTask(plugin,
                        () -> player.sendMessage(plugin.getMessageManager().getMessage("stamp_already_owned")));
                return;
            }

            plugin.getDatabaseManager().addStamp(player.getUniqueId(), stampId, stamp.getPoints()).thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    cz.basicland.turistika.config.ConfigManager.Rarity rarity = stamp.getRarity();
                    player.sendMessage(plugin.getMessageManager().getMessage("stamp_received")
                            .replace("{stamp_name}", stamp.getName()));
                    player.sendMessage(MessageManager.colorize(
                            rarity.color + "[" + rarity.displayName + "] &7Získal jsi &e+" + stamp.getPoints() + " bodů!"));
                    player.playSound(player.getLocation(), rarity.collectSound, 1f, rarity.collectPitch);
                    player.spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation().add(0,1,0), 30, 0.5, 0.5, 0.5, 0);
                    player.spawnParticle(Particle.FIREWORKS_SPARK, player.getLocation().add(0,1,0), 40, 0.4, 0.4, 0.4, 0.1);

                    // NPC dialogový "efekt" – zpráva z NPC
                    player.sendMessage(MessageManager.colorize("&e&l✦ NPC: &r&7Vítej, průzkumníku! Tvá návštěva byla zaznamenána."));

                    plugin.getServerFirstManager().handleFirstDiscovery(player, stampId, stamp.getName());
                    plugin.getDatabaseManager().getUnlockedStamps(player.getUniqueId())
                            .thenAccept(s -> plugin.getMilestoneManager().checkMilestones(player, s.size()));
                    plugin.getStreakManager().recordActivity(player);
                });
            });
        });
    }

    /** Zabraní ublizeniFalseovi NPC */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNpcDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Villager)) return;
        if (event.getEntity().getPersistentDataContainer().has(NPC_MARKER, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    /** Zabraní hracum tlacit NPC */
    @EventHandler
    public void onNpcDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Villager)) return;
        if (event.getEntity().getPersistentDataContainer().has(NPC_MARKER, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }
}
