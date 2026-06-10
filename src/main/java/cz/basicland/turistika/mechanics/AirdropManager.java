package cz.basicland.turistika.mechanics;

import cz.basicland.turistika.BasicLandTuristika;
import cz.basicland.turistika.config.ConfigManager;
import cz.basicland.turistika.config.ConfigManager.StampData;
import cz.basicland.turistika.config.MessageManager;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Spravuje system limitovanych airdropu turistickych znamek.
 *
 * Mechanka:
 *  - Kazdu minutu kontroluje config, zda nema spadnout novy airdrop.
 *  - Fyzicky item je spawnut ve svete a opatren PDC tagem "turistika:airdrop_id",
 *    ktery ho chrání pred smazanim pluginy typu ClearLag.
 *  - Item zmizi po nastavenem limitu, nebo kdyz ho sebre pozadovany pocet hracu.
 */
public class AirdropManager implements Listener {

    // Klic ulozeny v PersistentDataContainer itemu - slouzi jako ochrana pred ClearLagem
    public static final NamespacedKey PDC_AIRDROP_ID = new NamespacedKey("turistika", "airdrop_id");
    // Klic pro pocet zbyvajicich pickup slotu
    public static final NamespacedKey PDC_AIRDROP_SLOTS = new NamespacedKey("turistika", "airdrop_slots");

    private final BasicLandTuristika plugin;
    // Mapa aktivnich airdropu: airdrop_id -> metadata aktivniho dropu
    private final Map<String, ActiveAirdrop> activeAirdrops = new HashMap<>();
    private BukkitTask schedulerTask;

    public AirdropManager(BasicLandTuristika plugin) {
        this.plugin = plugin;
    }

    /**
     * Spusti periodicky task (kazda minuta), ktery kontroluje,
     * zda nema podle configu vzniknout novy airdrop.
     */
    public void startScheduler() {
        schedulerTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkForNewAirdrops();
                tickActiveAirdrops();
            }
        }.runTaskTimer(plugin, 20L * 30, 20L * 60); // Prvni check za 30s, pak kazde minutu
    }

    public void stopScheduler() {
        if (schedulerTask != null && !schedulerTask.isCancelled()) {
            schedulerTask.cancel();
        }
        // Despawnovat vsechny aktivni airdropy
        for (ActiveAirdrop ad : activeAirdrops.values()) {
            removeAirdrop(ad, false);
        }
        activeAirdrops.clear();
    }

    // ======================================================
    //  KONTROLA A SPAWN AIRDROPU
    // ======================================================

    private void checkForNewAirdrops() {
        ConfigurationSection airdrops = plugin.getConfig().getConfigurationSection("airdrops");
        if (airdrops == null) return;

        LocalDateTime now = LocalDateTime.now();

        for (String key : airdrops.getKeys(false)) {
            if (activeAirdrops.containsKey(key)) continue; // Jiz aktivni

            String spawnDateStr = airdrops.getString(key + ".spawn_time");
            if (spawnDateStr == null) continue;

            LocalDateTime spawnTime;
            try {
                spawnTime = LocalDateTime.parse(spawnDateStr, ConfigManager.DATE_FORMAT);
            } catch (DateTimeParseException e) {
                plugin.getLogger().warning("Spatny format spawn_time pro airdrop '" + key + "'");
                continue;
            }

            // Spawnovat, pokud uz nastal cas a nespawnoval se za poslednich 65 minut
            if (!now.isBefore(spawnTime) && now.isBefore(spawnTime.plusMinutes(65))) {
                String stampId = airdrops.getString(key + ".stamp_id");
                StampData stamp = plugin.getConfigManager().getStamp(stampId);
                if (stamp == null) {
                    plugin.getLogger().warning("Neplatne stamp_id '" + stampId + "' pro airdrop '" + key + "'");
                    continue;
                }

                String worldName = airdrops.getString(key + ".world", "world");
                double x = airdrops.getDouble(key + ".x");
                double y = airdrops.getDouble(key + ".y");
                double z = airdrops.getDouble(key + ".z");
                int maxPickups = airdrops.getInt(key + ".max_pickups", 1);
                int lifetimeMinutes = airdrops.getInt(key + ".lifetime_minutes", 60);

                spawnAirdrop(key, stamp, worldName, x, y, z, maxPickups, lifetimeMinutes);
            }
        }
    }

    private void tickActiveAirdrops() {
        Iterator<Map.Entry<String, ActiveAirdrop>> it = activeAirdrops.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ActiveAirdrop> entry = it.next();
            ActiveAirdrop ad = entry.getValue();
            if (LocalDateTime.now().isAfter(ad.expireTime)) {
                removeAirdrop(ad, true);
                it.remove();
                plugin.getLogger().info("Airdrop '" + entry.getKey() + "' vyprsel a byl odstranen.");
            }
        }
    }

    /**
     * Spawnuje fyzicky item ve svete a zapise ho jako aktivni airdrop.
     */
    private void spawnAirdrop(String airdropId, StampData stamp, String worldName,
                              double x, double y, double z, int maxPickups, int lifetimeMinutes) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Svet '" + worldName + "' pro airdrop '" + airdropId + "' nebyl nalezen!");
            return;
        }

        Location loc = new Location(world, x, y, z);

        // Sestavit item
        ItemStack item = buildAirdropItem(stamp, maxPickups);

        // Spawnovat item ve svete (velocity 0 = pada rovnou dolu)
        Item droppedItem = world.dropItem(loc, item);
        droppedItem.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        droppedItem.setPickupDelay(20); // Nelze zvednout prvih 1s po spawnu
        droppedItem.setCustomName(MessageManager.colorize("&b&lAIRDROP: " + stamp.getName()));
        droppedItem.setCustomNameVisible(true);

        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(lifetimeMinutes);

        activeAirdrops.put(airdropId, new ActiveAirdrop(airdropId, droppedItem.getUniqueId(), loc, expireTime, stamp));

        // Broadcast oznameni vsem hracum
        String broadcast = MessageManager.colorize("&b&l[!] &bTURISTICKY AIRDROP &7- &eznamka &b" + stamp.getName() +
                " &7spadla na &e" + (int)x + ", " + (int)y + ", " + (int)z + " &7ve svete &e" + worldName + "!");
        Bukkit.broadcastMessage(broadcast);

        // Particle efekt (opakujici se task)
        startParticleTask(droppedItem);

        plugin.getLogger().info("Spawnut airdrop '" + airdropId + "' pro znamku '" + stamp.getId() + "'.");
    }

    /**
     * Sestavi ItemStack airdropoveho predmetu a vlozi do nej PDC ochranu.
     */
    private ItemStack buildAirdropItem(StampData stamp, int maxPickups) {
        ItemStack item = new ItemStack(stamp.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(MessageManager.colorize("&b&l✦ " + stamp.getName() + " &b&l✦"));

        List<String> lore = new ArrayList<>(stamp.getLore());
        lore.add("");
        lore.add(MessageManager.colorize("&7Misto: " + maxPickups + " hrac(u) muze sebrat tuto znamku!"));
        lore.add(MessageManager.colorize("&e&lSEBER CO NEJDRIVE!"));
        meta.setLore(lore);

        if (stamp.getCustomModelData() > 0) {
            meta.setCustomModelData(stamp.getCustomModelData());
        }

        // === PDC OCHRANA PRED CLEARLAGOM ===
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(PDC_AIRDROP_ID, PersistentDataType.STRING, stamp.getId());
        pdc.set(PDC_AIRDROP_SLOTS, PersistentDataType.INTEGER, maxPickups);

        item.setItemMeta(meta);
        return item;
    }

    private void startParticleTask(Item droppedItem) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (droppedItem.isDead() || !droppedItem.isValid()) {
                    this.cancel();
                    return;
                }
                Location loc = droppedItem.getLocation().add(0, 0.5, 0);
                droppedItem.getWorld().spawnParticle(Particle.TOTEM, loc, 8, 0.3, 0.3, 0.3, 0.05);
                droppedItem.getWorld().spawnParticle(Particle.END_ROD, loc, 3, 0.2, 0.2, 0.2, 0.02);
            }
        }.runTaskTimer(plugin, 0L, 10L); // Kazde pulsekundy
    }

    private void removeAirdrop(ActiveAirdrop ad, boolean playEffect) {
        // Najit entitu ve svete a odstranit ji
        World world = ad.location.getWorld();
        if (world != null) {
            Entity entity = Bukkit.getEntity(ad.itemEntityUUID);
            if (entity != null && !entity.isDead()) {
                if (playEffect) {
                    // Majestátní zvukový efekt zmizení
                    world.playSound(ad.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 0.5f);
                    world.spawnParticle(Particle.EXPLOSION_LARGE, ad.location, 10, 0.5, 0.5, 0.5, 0.1);
                }
                entity.remove();
            }
        }
    }

    // ======================================================
    //  LISTENER - ZVEDANI ITEMU
    // ======================================================

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        Item item = event.getItem();
        ItemMeta meta = item.getItemStack().getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Zkontrolovat, zda jde o nas chraneny airdrop item
        if (!pdc.has(PDC_AIRDROP_ID, PersistentDataType.STRING)) return;

        // Vzdy zrusime vychozi pickup event - my sami rizime, co se stane
        event.setCancelled(true);

        String stampId = pdc.get(PDC_AIRDROP_ID, PersistentDataType.STRING);
        Integer slots = pdc.get(PDC_AIRDROP_SLOTS, PersistentDataType.INTEGER);
        if (stampId == null || slots == null) return;

        // Zkontrolovat, zda hrac uz tuto znamku nema
        plugin.getDatabaseManager().hasStamp(player.getUniqueId(), stampId).thenAccept(hasIt -> {
            if (hasIt) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(plugin.getMessageManager().getMessage("stamp_already_owned")));
                return;
            }

            StampData stamp = plugin.getConfigManager().getStamp(stampId);
            String stampName = stamp != null ? stamp.getName() : stampId;

            // Zapsat znamku do DB
            plugin.getDatabaseManager().addStamp(player.getUniqueId(), stampId).thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Spustit efekty a zpravy
                    player.sendMessage(plugin.getMessageManager().getMessage("stamp_received").replace("{stamp_name}", stampName));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                    player.spawnParticle(Particle.TOTEM, player.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);

                    // Snizit pocet volnych slotu na itemu
                    int remaining = slots - 1;

                    if (remaining <= 0) {
                        // Vsechny sloty vyuzity -> odstranim item
                        for (Map.Entry<String, ActiveAirdrop> entry : activeAirdrops.entrySet()) {
                            if (entry.getValue().itemEntityUUID.equals(item.getUniqueId())) {
                                activeAirdrops.remove(entry.getKey());
                                break;
                            }
                        }
                        item.remove();
                    } else {
                        // Aktualizovat pocet slotu v PDC
                        ItemStack stack = item.getItemStack();
                        ItemMeta newMeta = stack.getItemMeta();
                        if (newMeta != null) {
                            newMeta.getPersistentDataContainer().set(PDC_AIRDROP_SLOTS, PersistentDataType.INTEGER, remaining);
                            stack.setItemMeta(newMeta);
                            item.setItemStack(stack);
                        }
                    }

                    // Server first + Milniky
                    plugin.getServerFirstManager().handleFirstDiscovery(player, stampId, stampName);
                    plugin.getDatabaseManager().getUnlockedStamps(player.getUniqueId()).thenAccept(stamps ->
                            plugin.getMilestoneManager().checkMilestones(player, stamps.size()));
                });
            });
        });
    }

    // ======================================================
    //  VNITRNI DATA CLASS
    // ======================================================

    private static class ActiveAirdrop {
        final String id;
        final UUID itemEntityUUID;
        final Location location;
        final LocalDateTime expireTime;
        final StampData stamp;

        ActiveAirdrop(String id, UUID itemEntityUUID, Location location, LocalDateTime expireTime, StampData stamp) {
            this.id = id;
            this.itemEntityUUID = itemEntityUUID;
            this.location = location;
            this.expireTime = expireTime;
            this.stamp = stamp;
        }
    }
}
