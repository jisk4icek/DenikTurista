package cz.basicland.turistika.gui;

import cz.basicland.turistika.BasicLandTuristika;
import cz.basicland.turistika.config.ConfigManager;
import cz.basicland.turistika.config.ConfigManager.StampData;
import cz.basicland.turistika.config.ConfigManager.StampStatus;
import cz.basicland.turistika.config.MessageManager;
import cz.basicland.turistika.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * DenikGUI v2.0
 *
 * Novinky oproti v1:
 *  - 5 vizualnich stavu znamky: FOUND, AVAILABLE, LOCKED, EXPIRED, OUT_OF_WINDOW
 *  - Progress bar v navazaci titulku GUI
 *  - Lepsi vizualni design slotu (glint efekt na nalezene)
 *  - Slot 49 = info item (celkovy progres, streak info)
 */
public class DenikGUI extends Gui {

    private final BasicLandTuristika plugin;
    private final Player player;
    private final Set<String> unlockedStamps;
    private int page;

    public DenikGUI(BasicLandTuristika plugin, Player player, Set<String> unlockedStamps, int page) {
        super(54, plugin.getConfigManager().getGuiTitle());
        this.plugin = plugin;
        this.player = player;
        this.unlockedStamps = unlockedStamps;
        this.page = page;
        setupItems();
    }

    private void setupItems() {
        getInventory().clear();

        List<StampData> allStamps = new ArrayList<>(plugin.getConfigManager().getStamps().values());
        int total      = allStamps.size();
        int found      = unlockedStamps.size();
        int itemsPerPage = 44; // Slot 44 = info item
        int maxPages   = (int) Math.ceil((double) total / itemsPerPage);
        if (maxPages == 0) maxPages = 1;
        page = Math.max(0, Math.min(page, maxPages - 1));

        int start = page * itemsPerPage;
        int end   = Math.min(start + itemsPerPage, total);

        for (int i = start; i < end; i++) {
            int slot = i - start;
            StampData stamp = allStamps.get(i);
            StampStatus status = stamp.getStatus(unlockedStamps.contains(stamp.getId()));
            getInventory().setItem(slot, buildStampItem(stamp, status));
        }

        // === SLOT 44 – PROGRESS INFO ===
        int pct = total > 0 ? (found * 100) / total : 0;
        String progressBar = buildProgressBar(found, total, 20);
        getInventory().setItem(44, new ItemBuilder(Material.BOOK)
                .setName("&b&l✦ Tvůj Postup ✦")
                .setLore(Arrays.asList(
                        "&7známky: &e" + found + " &8/ &e" + total + " &8(" + pct + "%)",
                        "&7Progress: " + progressBar,
                        "",
                        "&7Strana: &e" + (page + 1) + " &8/ &e" + maxPages,
                        "",
                        "&8[LEGENDA]",
                        "&a✔ &7Nalezeno   &c? &7Nedostupno",
                        "&8⏳ &7Zamčeno    &4✖ &7Expirováno",
                        "&6⏰ &7Mimo čas"
                )).build());

        // === NAVIGACE ===
        if (page > 0) {
            getInventory().setItem(45, new ItemBuilder(Material.ARROW)
                    .setName(plugin.getMessageManager().getRawMessage("gui_previous_page")).build());
            setAction(45, p -> new DenikGUI(plugin, p, unlockedStamps, page - 1).open(p));
        }

        if (page < maxPages - 1) {
            getInventory().setItem(53, new ItemBuilder(Material.ARROW)
                    .setName(plugin.getMessageManager().getRawMessage("gui_next_page")).build());
            setAction(53, p -> new DenikGUI(plugin, p, unlockedStamps, page + 1).open(p));
        }
    }

    // ======================================================
    //  VIZUAL STAVU ZNAMKY
    // ======================================================

    private org.bukkit.inventory.ItemStack buildStampItem(StampData stamp, StampStatus status) {
        switch (status) {

            case FOUND: {
                // Znamka nalezena – originalni vizual + enchant glint
                ItemBuilder b = new ItemBuilder(stamp.getMaterial())
                        .setName("&a✔ " + stamp.getName())
                        .setLore(addFoundLore(stamp.getLore()))
                        .setCustomModelData(stamp.getCustomModelData())
                        .setGlint(true);
                return b.build();
            }

            case LOCKED: {
                // Casovy zamek
                String matStr = plugin.getConfig().getString("gui.locked_stamp.material", "CLOCK");
                Material mat  = orDefault(matStr, Material.CLOCK);
                String name   = plugin.getConfig().getString("gui.locked_stamp.name", "&8⏳ Uzamčená");
                List<String> lore = new ArrayList<>(plugin.getConfig().getStringList("gui.locked_stamp.lore"));
                String dateStr = stamp.getUnlockDate().format(ConfigManager.DISPLAY_FORMAT);
                lore.replaceAll(l -> l.replace("{unlock_date}", dateStr).replace("{name}", stripColor(stamp.getName())));
                return new ItemBuilder(mat).setName(name).setLore(lore).build();
            }

            case EXPIRED: {
                // Expirovala
                return new ItemBuilder(Material.BARRIER)
                        .setName("&4✖ Expirovaná Edice")
                        .setLore(Arrays.asList(
                                "&7Tato známka již není dostupná.",
                                "&7Datum platnosti: &c" + stamp.getExpireDate().format(ConfigManager.DISPLAY_FORMAT),
                                "",
                                "&8Tuto edici jsi nestihl/a."
                        )).build();
            }

            case OUT_OF_WINDOW: {
                // Mimo denni casove okno
                return new ItemBuilder(Material.COMPARATOR)
                        .setName("&6⏰ Mimo Čas")
                        .setLore(Arrays.asList(
                                "&7známka &e" + stripColor(stamp.getName()),
                                "&7je dostupná jen v čas:",
                                "&e" + stamp.getWindowString(),
                                "",
                                "&7Přijď v tento čas na lokaci!"
                        )).build();
            }

            case AVAILABLE:
            default: {
                // Nenalezena, dostupna
                String matStr = plugin.getConfig().getString("gui.unknown_stamp.material", "MAP");
                Material mat  = orDefault(matStr, Material.MAP);
                String name   = plugin.getConfig().getString("gui.unknown_stamp.name", "&c? Neobjevená");
                List<String> lore = plugin.getConfig().getStringList("gui.unknown_stamp.lore");
                return new ItemBuilder(mat).setName(name).setLore(lore).build();
            }
        }
    }

    private List<String> addFoundLore(List<String> originalLore) {
        List<String> lore = new ArrayList<>(originalLore);
        lore.add("");
        lore.add("&a&l✔ NALEZENO");
        return lore;
    }

    private String buildProgressBar(int done, int total, int bars) {
        if (total == 0) return "&8" + "█".repeat(bars);
        int filled = (int) Math.round((double) done / total * bars);
        return "&a" + "█".repeat(filled) + "&8" + "█".repeat(bars - filled);
    }

    private Material orDefault(String s, Material def) {
        if (s == null) return def;
        Material m = Material.matchMaterial(s);
        return m != null ? m : def;
    }

    private String stripColor(String s) {
        return s == null ? "" : s.replaceAll("(?i)&[0-9a-fk-or]", "");
    }

    @Override
    public void open(Player p) {
        p.openInventory(getInventory());
    }
}
