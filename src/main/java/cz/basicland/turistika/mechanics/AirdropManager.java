package cz.basicland.turistika.mechanics;

import cz.basicland.turistika.BasicLandTuristika;
import cz.basicland.turistika.config.ConfigManager;
import cz.basicland.turistika.config.ConfigManager.StampData;
import cz.basicland.turistika.config.MessageManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * AirdropManager v2.0
 *
 * Novinky oproti v1:
 *  - Podpora spawn_region: nahodny bezpecny povrch v definovane oblasti mapy
 *  - Protected zones: admin muze definovat oblasti kam airdropy nespadnou
 *  - Broadcast obsahuje /tpa hint pro online hrace
 *  - Safe surface detection: world.getHighestBlockYAt(), kontrola liquid/void
 */
public class AirdropManager implements Listener {

    public static final NamespacedKey PDC_AIRDROP_ID    = new NamespacedKey("turistika", "airdrop_id");
    public static final NamespacedKey PDC_AIRDROP_SLOTS = new NamespacedKey("turistika", "airdrop_slots");

    private final BasicLandTuristika plugin;
    private final Map<String, ActiveAirdrop> activeAirdrops = new HashMap<>();
    private BukkitTask schedulerTask;
    private final Random random = new Random();

    public AirdropManager(BasicLandTuristika plugin) {
        this.plugin = plugin;
    }

    public void startScheduler() {
        schedulerTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkForNewAirdrops();
                tickActiveAirdrops();
            }
        }.runTaskTimer(plugin, 20L * 30, 20L * 60);
    }

    public void stopScheduler() {
        if (schedulerTask != null && !schedulerTask.isCancelled()) schedulerTask.cancel();
        activeAirdrops.values().forEach(ad -> removeAirdrop(ad, false));
        activeAirdrops.clear();
    }

    // ======================================================
    //  CHECK A SPAWN
    // ======================================================

    private void checkForNewAirdrops() {
        ConfigurationSection airdrops = plugin.getConfig().getConfigurationSection("airdrops");
        if (airdrops == null) return;
        LocalDateTime now = LocalDateTime.now();

        for (String key : airdrops.getKeys(false)) {
            if (activeAirdrops.containsKey(key)) continue;

            String spawnDateStr = airdrops.getString(key + ".spawn_time");
            if (spawnDateStr == null) continue;

            LocalDateTime spawnTime;
            try {
                spawnTime = LocalDateTime.parse(spawnDateStr, ConfigManager.DATE_FORMAT);
            } catch (DateTimeParseException e) {
                plugin.getLogger().warning("Spatny format spawn_time pro airdrop '" + key + "'");
                continue;
            }

            if (!now.isBefore(spawnTime) && now.isBefore(spawnTime.plusMinutes(65))) {
                String stampId = airdrops.getString(key + ".stamp_id");
                StampData stamp = plugin.getConfigManager().getStamp(stampId);
                if (stamp == null) continue;

                int maxPickups       = airdrops.getInt(key + ".max_pickups", 1);
                int lifetimeMinutes  = airdrops.getInt(key + ".lifetime_minutes", 60);

                // === v2.0: Podpora spawn_region ===
                Location spawnLoc;
                if (airdrops.contains(key + ".spawn_region")) {
                    spawnLoc = findRegionLocation(airdrops.getConfigurationSection(key + ".spawn_region"));
                    if (spawnLoc == null) {
                        plugin.getLogger().warning("Airdrop '" + key + "': nelze najit bezpecne misto v regionu!");
                        continue;
                    }
                } else {
                    // Klasicky pevne souradnice
                    String worldName = airdrops.getString(key + ".world", "world");
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) continue;
                    double x = airdrops.getDouble(key + ".x");
                    double y = airdrops.getDouble(key + ".y");
                    double z = airdrops.getDouble(key + ".z");
                    spawnLoc = new Location(world, x, y, z);
                }

                spawnAirdrop(key, stamp, spawnLoc, maxPickups, lifetimeMinutes);
            }
        }
    }

    /**
     * v2.1: Najde nahodnou bezpecnou lokaci na POVRCHU v danem regionu.
     *
     * Kontroly (v poradi):
     *  1. WorldBorder – lokace musi byt uvnitr borderu sveta
     *  2. HighestBlockY – nejvyssi pevny blok (ne vzduch, ne stromy nad tim)
     *  3. Povrch – solid blok, bez liquid (voda, lava)
     *  4. Headspace – 2 bloky volneho prostoru nad povrchem (hrac tam musi stat)
     *  5. Protected zones – admin-definovane zakazove oblasti
     *
     * Pokud nenajde misto za 80 pokusu, vrati null (do logu varuje).
     */
    private Location findRegionLocation(ConfigurationSection region) {
        if (region == null) return null;
        String worldName = region.getString("world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        WorldBorder border = world.getWorldBorder();
        double borderRadius = border.getSize() / 2.0;
        double borderCX = border.getCenter().getX();
        double borderCZ = border.getCenter().getZ();

        int minX = region.getInt("min_x", -500);
        int maxX = region.getInt("max_x",  500);
        int minZ = region.getInt("min_z", -500);
        int maxZ = region.getInt("max_z",  500);

        // Orez region na WorldBorder (prutih k doln/horn mezim)
        minX = (int) Math.max(minX, borderCX - borderRadius + 5);
        maxX = (int) Math.min(maxX, borderCX + borderRadius - 5);
        minZ = (int) Math.max(minZ, borderCZ - borderRadius + 5);
        maxZ = (int) Math.min(maxZ, borderCZ + borderRadius - 5);

        if (minX >= maxX || minZ >= maxZ) {
            plugin.getLogger().warning("Airdrop region je prilis maly nebo mimo WorldBorder!");
            return null;
        }

        for (int attempt = 0; attempt < 80; attempt++) {
            int x = minX + random.nextInt(maxX - minX);
            int z = minZ + random.nextInt(maxZ - minZ);

            // === 1. WorldBorder check ===
            Location checkLoc = new Location(world, x + 0.5, 64, z + 0.5);
            if (!border.isInside(checkLoc)) continue;

            // === 2. Nejvyssi blok ===
            int y = world.getHighestBlockYAt(x, z);
            if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 3) continue;

            Block surface = world.getBlockAt(x, y, z);

            // === 3. Povrch – solid, bez vody/lavy ===
            if (surface.getType().isAir())    continue;
            if (surface.isLiquid())           continue;
            if (!surface.getType().isSolid()) continue;

            // === 4. Headspace – 2 bloky volneho mista nad povrchem ===
            Block above1 = world.getBlockAt(x, y + 1, z);
            Block above2 = world.getBlockAt(x, y + 2, z);
            if (!above1.getType().isAir() && !above1.isPassable()) continue;
            if (!above2.getType().isAir() && !above2.isPassable()) continue;

            Location loc = new Location(world, x + 0.5, y + 1.0, z + 0.5);

            // === 5. Protected zones ===
            if (isInProtectedZone(loc)) continue;

            return loc;
        }

        plugin.getLogger().warning("[Turistika] Airdrop: po 80 pokusech nenalezeno bezpecne misto v regionu! " +
                "Zkontroluj spawn_region a protected_zones v config.yml.");
        return null;
    }

    /**
     * v2.0: Zkontroluje zda je lokace v protected zone definovane v config.yml.
     */
    private boolean isInProtectedZone(Location loc) {
        ConfigurationSection zones = plugin.getConfig().getConfigurationSection("protected_zones");
        if (zones == null) return false;
        for (String zoneKey : zones.getKeys(false)) {
            String worldName = zones.getString(zoneKey + ".world", "world");
            if (!loc.getWorld().getName().equals(worldName)) continue;
            int minX = zones.getInt(zoneKey + ".min_x", Integer.MIN_VALUE);
            int maxX = zones.getInt(zoneKey + ".max_x", Integer.MAX_VALUE);
            int minZ = zones.getInt(zoneKey + ".min_z", Integer.MIN_VALUE);
            int maxZ = zones.getInt(zoneKey + ".max_z", Integer.MAX_VALUE);
            int bx = loc.getBlockX();
            int bz = loc.getBlockZ();
            if (bx >= minX && bx <= maxX && bz >= minZ && bz <= maxZ) return true;
        }
        return false;
    }

    private void tickActiveAirdrops() {
        Iterator<Map.Entry<String, ActiveAirdrop>> it = activeAirdrops.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ActiveAirdrop> entry = it.next();
            ActiveAirdrop ad = entry.getValue();
            if (LocalDateTime.now().isAfter(ad.expireTime)) {
                removeAirdrop(ad, true);
                it.remove();
                plugin.getLogger().info("Airdrop '" + entry.getKey() + "' vyprsel.");
            }
        }
    }

    private void spawnAirdrop(String airdropId, StampData stamp, Location loc, int maxPickups, int lifetimeMinutes) {
        ItemStack item = buildAirdropItem(stamp, maxPickups);
        Item droppedItem = loc.getWorld().dropItem(loc, item);
        droppedItem.setVelocity(new Vector(0, 0, 0));
        droppedItem.setPickupDelay(20);
        droppedItem.setCustomName(MessageManager.colorize("&b&l✦ AIRDROP: " + stamp.getName() + " &b&l✦"));
        droppedItem.setCustomNameVisible(true);

        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(lifetimeMinutes);
        activeAirdrops.put(airdropId, new ActiveAirdrop(airdropId, droppedItem.getUniqueId(), loc, expireTime, stamp));

        // Broadcast s lokaci a /tpa hintem
        int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
        String worldName = loc.getWorld().getName();
        String broadcast = MessageManager.colorize(
                "&b&l[!] &bTURISTICKÝ AIRDROP &e" + stamp.getName() +
                " &7» &e" + bx + " " + by + " " + bz + " &7(svět: &e" + worldName + "&7)" +
                "\n&8&o Použij: /tppos " + bx + " " + by + " " + bz + " (nebo teleport na souřadnice)");
        Bukkit.broadcastMessage(broadcast);

        startParticleTask(droppedItem);
        plugin.getLogger().info("Spawnut airdrop '" + airdropId + "' na " + bx + " " + by + " " + bz);
    }

    private ItemStack buildAirdropItem(StampData stamp, int maxPickups) {
        ItemStack item = new ItemStack(stamp.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(MessageManager.colorize("&b&l✦ " + stamp.getName() + " &b&l✦"));
        List<String> lore = new ArrayList<>(stamp.getLore());
        lore.add(""); lore.add(MessageManager.colorize("&7Míst: &e" + maxPickups + " &7hráčů může sebrat!"));
        lore.add(MessageManager.colorize("&e&lSEBER CO NEJDŘÍVE!"));
        meta.setLore(lore);
        if (stamp.getCustomModelData() > 0) meta.setCustomModelData(stamp.getCustomModelData());
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(PDC_AIRDROP_ID,    PersistentDataType.STRING,  stamp.getId());
        pdc.set(PDC_AIRDROP_SLOTS, PersistentDataType.INTEGER, maxPickups);
        item.setItemMeta(meta);
        return item;
    }

    private void startParticleTask(Item droppedItem) {
        new BukkitRunnable() {
            @Override public void run() {
                if (droppedItem.isDead() || !droppedItem.isValid()) { cancel(); return; }
                Location loc = droppedItem.getLocation().add(0, 0.5, 0);
                loc.getWorld().spawnParticle(Particle.TOTEM, loc, 8, 0.3, 0.3, 0.3, 0.05);
                loc.getWorld().spawnParticle(Particle.END_ROD, loc, 3, 0.2, 0.2, 0.2, 0.02);
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private void removeAirdrop(ActiveAirdrop ad, boolean playEffect) {
        World world = ad.location.getWorld();
        if (world != null) {
            Entity entity = Bukkit.getEntity(ad.itemEntityUUID);
            if (entity != null && !entity.isDead()) {
                if (playEffect) {
                    world.playSound(ad.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 0.5f);
                    world.spawnParticle(Particle.EXPLOSION_LARGE, ad.location, 10, 0.5, 0.5, 0.5, 0.1);
                }
                entity.remove();
            }
        }
    }

    // ======================================================
    //  LISTENER – PICKUP
    // ======================================================

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        Item item = event.getItem();
        ItemMeta meta = item.getItemStack().getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(PDC_AIRDROP_ID, PersistentDataType.STRING)) return;
        event.setCancelled(true);

        String stampId = pdc.get(PDC_AIRDROP_ID, PersistentDataType.STRING);
        Integer slots  = pdc.get(PDC_AIRDROP_SLOTS, PersistentDataType.INTEGER);
        if (stampId == null || slots == null) return;

        plugin.getDatabaseManager().hasStamp(player.getUniqueId(), stampId).thenAccept(has -> {
            if (has) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(plugin.getMessageManager().getMessage("stamp_already_owned")));
                return;
            }

            StampData stamp = plugin.getConfigManager().getStamp(stampId);
            String stampName = stamp != null ? stamp.getName() : stampId;

            plugin.getDatabaseManager().addStamp(player.getUniqueId(), stampId).thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(plugin.getMessageManager().getMessage("stamp_received")
                            .replace("{stamp_name}", stampName));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                    player.spawnParticle(Particle.TOTEM, player.getLocation().add(0,1,0), 50, 0.5, 0.5, 0.5, 0.1);

                    int remaining = slots - 1;
                    if (remaining <= 0) {
                        activeAirdrops.entrySet().removeIf(e -> e.getValue().itemEntityUUID.equals(item.getUniqueId()));
                        item.remove();
                    } else {
                        ItemStack stack = item.getItemStack();
                        ItemMeta newMeta = stack.getItemMeta();
                        if (newMeta != null) {
                            newMeta.getPersistentDataContainer().set(PDC_AIRDROP_SLOTS, PersistentDataType.INTEGER, remaining);
                            stack.setItemMeta(newMeta);
                            item.setItemStack(stack);
                        }
                    }

                    plugin.getServerFirstManager().handleFirstDiscovery(player, stampId, stampName);
                    plugin.getDatabaseManager().getUnlockedStamps(player.getUniqueId())
                            .thenAccept(s -> plugin.getMilestoneManager().checkMilestones(player, s.size()));
                    plugin.getStreakManager().recordActivity(player);
                });
            });
        });
    }

    // ======================================================
    //  INNER CLASS
    // ======================================================

    private static class ActiveAirdrop {
        final String id;
        final UUID itemEntityUUID;
        final Location location;
        final LocalDateTime expireTime;
        final StampData stamp;

        ActiveAirdrop(String id, UUID uuid, Location loc, LocalDateTime exp, StampData stamp) {
            this.id = id; this.itemEntityUUID = uuid;
            this.location = loc; this.expireTime = exp; this.stamp = stamp;
        }
    }
}
