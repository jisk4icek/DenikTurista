package cz.basicland.turistika.mechanics;

import cz.basicland.turistika.BasicLandTuristika;
import cz.basicland.turistika.config.ConfigManager;
import cz.basicland.turistika.config.ConfigManager.StampData;
import cz.basicland.turistika.config.MessageManager;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;

import java.util.*;

/**
 * MarkerManager v2.2 – ArmorStand "Turistický Průvodce"
 *
 * AI-designed vizuál:
 *  - Celá souprava kožené zbroje obarvená do barvou explorátora
 *  - Helma: hnedá (průzkumník)
 *  - Kabát: olivová/zelená (explorer vesta)
 *  - Kalhoty: tmavě hnedá
 *  - Boty: sedohnědá
 *  - Hlavní ruka: FILLED_MAP (turistická mapa – drží a ukazuje)
 *  - Vedlejší ruka: STICK (turistická hůl)
 *  - Poza: vítající průvodce, ruka s mapou natažená k hráči
 *
 * Interakce: hráč klikne pravý → dostane turistickou zna mku
 * Persistence: ArmorStand je Persistent=true, pozice v SQLite
 */
public class MarkerManager implements Listener {

    public static final NamespacedKey MARKER_STAMP_KEY = new NamespacedKey("turistika", "marker_stamp_id");
    public static final NamespacedKey MARKER_TAG       = new NamespacedKey("turistika", "turistika_marker");

    private final BasicLandTuristika plugin;
    private final Map<String, UUID> activeMarkers = new HashMap<>();

    public MarkerManager(BasicLandTuristika plugin) {
        this.plugin = plugin;
    }

    // ======================================================
    //  SPAWN / REMOVE
    // ======================================================

    public boolean spawnMarker(String stampId, Location loc) {
        StampData stamp = plugin.getConfigManager().getStamp(stampId);
        if (stamp == null) return false;

        removeMarker(stampId);

        ArmorStand as = loc.getWorld().spawn(loc, ArmorStand.class, a -> {
            // ─── Základní vlastnosti ───────────────────────────────────
            a.setCustomName(MessageManager.colorize("&b&l✦ &e" + stripColor(stamp.getName()) + " &b&l✦"));
            a.setCustomNameVisible(true);
            a.setGlowing(true);           // Svítivý outline – hráč ho vidí z dálky
            a.setInvulnerable(true);
            a.setGravity(false);
            a.setArms(true);
            a.setBasePlate(false);
            a.setVisible(true);
            a.setPersistent(true);
            a.setRemoveWhenFarAway(false);
            a.setSmall(false);            // Plná velikost (ne malý)

            // ─── AI-designed POZA: "Vítající turistický průvodce" ─────
            //
            //  Hlava: mírně nakloněna doleva (přirozeně vítá)
            a.setHeadPose(new EulerAngle(
                    Math.toRadians(-8),   // pitch: lehce dolu (dívá se na hráče)
                    Math.toRadians(0),
                    Math.toRadians(8)     // roll: naklon hlavy doleva
            ));
            //  Tělo: velmi mírný náklon kupředu (uvítací gesto)
            a.setBodyPose(new EulerAngle(
                    Math.toRadians(3),
                    Math.toRadians(0),
                    Math.toRadians(0)
            ));
            //  Pravá ruka: natažená kupředu s mapou (jako by ji nabízel)
            //  Represents: rameno dolu-dopředu, loket ohnutý, dlaň nahoru
            a.setRightArmPose(new EulerAngle(
                    Math.toRadians(-55),  // ruka zvednutá kupředu
                    Math.toRadians(20),   // mírně ven
                    Math.toRadians(-25)   // rotace – nabízí předmět
            ));
            //  Levá ruka: drží hůl dolů pod úhlem (turistická hůl)
            a.setLeftArmPose(new EulerAngle(
                    Math.toRadians(15),   // mírně dolu
                    Math.toRadians(-10),
                    Math.toRadians(20)    // od těla
            ));
            //  Pravá noha: mírně nakročená kupředu (dynamická poza)
            a.setRightLegPose(new EulerAngle(
                    Math.toRadians(-8),
                    Math.toRadians(0),
                    Math.toRadians(2)
            ));
            //  Levá noha: mírně pozadu (váha na pravé)
            a.setLeftLegPose(new EulerAngle(
                    Math.toRadians(5),
                    Math.toRadians(0),
                    Math.toRadians(-2)
            ));

            // ─── AI-designed VÝBAVA ──────────────────────────────────
            equipExplorerOutfit(a, stamp);

            // ─── PDC identifikace ─────────────────────────────────────
            PersistentDataContainer pdc = a.getPersistentDataContainer();
            pdc.set(MARKER_STAMP_KEY, PersistentDataType.STRING, stampId);
            pdc.set(MARKER_TAG,       PersistentDataType.BYTE,   (byte) 1);
        });

        activeMarkers.put(stampId, as.getUniqueId());
        plugin.getDatabaseManager().saveMarker(stampId, loc, as.getUniqueId());
        plugin.getLogger().info("Marker (turistický průvodce) pro zna mku '" + stampId + "' spawnut.");
        return true;
    }

    /**
     * AI-designed souprava turistického průvodce.
     *
     * Barevná paleta:
     *  Helma   → hnědá   (#7B4F2E) – plstěný průzkumnický klobouk
     *  Kabát   → olivová (#4A5C2A) – explorer vesta
     *  Kalhoty → tmavě hnedá (#3E2A1A) – terénní kalhoty
     *  Boty    → sedohnědá (#5C3D1E) – turistické boty
     *
     * Hands:
     *  Pravá   → FILLED_MAP  – turistická mapa (hlavní interakční předmět)
     *  Levá    → STICK       – turistická hůl/hole
     */
    private void equipExplorerOutfit(ArmorStand as, StampData stamp) {
        EntityEquipment eq = as.getEquipment();
        if (eq == null) return;

        // Klobouk (hnědá kůže)
        eq.setHelmet(dyedLeather(Material.LEATHER_HELMET,
                Color.fromRGB(123, 79, 46)), false);
        // Vesta (olivová/lesní zelená)
        eq.setChestplate(dyedLeather(Material.LEATHER_CHESTPLATE,
                Color.fromRGB(74, 92, 42)), false);
        // Kalhoty (tmavě hnedé)
        eq.setLeggings(dyedLeather(Material.LEATHER_LEGGINGS,
                Color.fromRGB(62, 42, 26)), false);
        // Boty (sedohnědé)
        eq.setBoots(dyedLeather(Material.LEATHER_BOOTS,
                Color.fromRGB(92, 61, 30)), false);

        // Pravá ruka: turistická mapa (vypadá reálně co průvodce drží)
        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        ItemMeta mapMeta = mapItem.getItemMeta();
        if (mapMeta != null) {
            mapMeta.setDisplayName(MessageManager.colorize("&e" + stripColor(stamp.getName())));
            mapItem.setItemMeta(mapMeta);
        }
        eq.setItemInMainHand(mapItem, false);

        // Levá ruka: turistická hůl
        eq.setItemInOffHand(new ItemStack(Material.STICK), false);

        // Drop chance = 0 (nic se nevypadne)
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
        eq.setItemInOffHandDropChance(0f);
    }

    /** Vytvoří barvený kožený kus zbroje. */
    private ItemStack dyedLeather(Material material, Color color) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof LeatherArmorMeta lam) {
            lam.setColor(color);
            item.setItemMeta(lam);
        }
        return item;
    }

    // ======================================================
    //  REMOVE / LOAD / SHUTDOWN
    // ======================================================

    public void removeMarker(String stampId) {
        UUID uuid = activeMarkers.remove(stampId);
        if (uuid != null) {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null) e.remove();
        }
        plugin.getDatabaseManager().deleteMarker(stampId);
    }

    public void loadFromDatabase() {
        plugin.getDatabaseManager().loadMarkers().thenAccept(list -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (var data : list) {
                    World world = Bukkit.getWorld(data.world);
                    if (world == null) continue;
                    if (data.entityUUID != null) {
                        Entity found = Bukkit.getEntity(data.entityUUID);
                        if (found instanceof ArmorStand && !found.isDead()) {
                            activeMarkers.put(data.stampId, found.getUniqueId());
                            continue;
                        }
                    }
                    spawnMarker(data.stampId, new Location(world, data.x, data.y, data.z));
                }
                plugin.getLogger().info("Nacteno " + list.size() + " ArmorStand Markeru (turistických průvodců).");
            });
        });
    }

    public Map<String, UUID> getActiveMarkers() { return Collections.unmodifiableMap(activeMarkers); }
    public void shutdown() { activeMarkers.clear(); }

    // ======================================================
    //  LISTENER – KLIKNUTÍ NA MARKER
    // ======================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onMarkerInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Entity entity = event.getRightClicked();
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        if (!pdc.has(MARKER_TAG, PersistentDataType.BYTE)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        String stampId = pdc.get(MARKER_STAMP_KEY, PersistentDataType.STRING);
        if (stampId == null) return;

        StampData stamp = plugin.getConfigManager().getStamp(stampId);
        if (stamp == null) {
            player.sendMessage(plugin.getMessageManager().getMessage("stamp_not_found")
                    .replace("{stamp_id}", stampId));
            return;
        }

        if (!stamp.isCurrentlyAvailable()) {
            if (stamp.isLocked()) {
                player.sendMessage(plugin.getMessageManager().getMessage("npc_stamp_locked")
                        .replace("{unlock_date}", stamp.getUnlockDate().format(ConfigManager.DISPLAY_FORMAT)));
            } else if (stamp.isExpired()) {
                player.sendMessage(plugin.getMessageManager().getMessage("npc_stamp_expired"));
            } else if (stamp.isOutOfWindow()) {
                player.sendMessage(plugin.getMessageManager().getMessage("npc_stamp_out_of_window")
                        .replace("{window}", stamp.getWindowString()));
            }
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        plugin.getDatabaseManager().hasStamp(player.getUniqueId(), stampId).thenAccept(has -> {
            if (has) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(plugin.getMessageManager().getMessage("stamp_already_owned"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.2f);
                });
                return;
            }

            plugin.getDatabaseManager().addStamp(player.getUniqueId(), stampId).thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    player.sendMessage(plugin.getMessageManager().getMessage("stamp_received")
                            .replace("{stamp_name}", stamp.getName()));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.5f);

                    Location markerLoc = entity.getLocation().add(0, 1.5, 0);
                    player.spawnParticle(Particle.FIREWORKS_SPARK, markerLoc, 60, 0.3, 0.5, 0.3, 0.1);
                    player.spawnParticle(Particle.VILLAGER_HAPPY,  markerLoc, 25, 0.4, 0.4, 0.4, 0);
                    player.spawnParticle(Particle.TOTEM,           markerLoc, 35, 0.4, 0.4, 0.4, 0.05);

                    plugin.getServerFirstManager().handleFirstDiscovery(player, stampId, stamp.getName());
                    plugin.getDatabaseManager().getUnlockedStamps(player.getUniqueId())
                            .thenAccept(s -> plugin.getMilestoneManager().checkMilestones(player, s.size()));
                    plugin.getStreakManager().recordActivity(player);
                });
            });
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMarkerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof ArmorStand)) return;
        if (event.getEntity().getPersistentDataContainer().has(MARKER_TAG, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    private String stripColor(String s) {
        return s == null ? "" : s.replaceAll("(?i)&[0-9a-fk-or]", "");
    }
}
