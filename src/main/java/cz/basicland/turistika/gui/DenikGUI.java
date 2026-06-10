package cz.basicland.turistika.gui;

import cz.basicland.turistika.BasicLandTuristika;
import cz.basicland.turistika.config.ConfigManager.StampData;
import cz.basicland.turistika.config.ConfigManager;
import cz.basicland.turistika.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * GUI turistickeho deniku s podporou strankovani.
 * Pouziva thread-safe Java Time API pro zobrazovani datumu zamku.
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

        int itemsPerPage = 45;
        int maxPages = (int) Math.ceil((double) allStamps.size() / itemsPerPage);
        if (maxPages == 0) maxPages = 1;
        if (page >= maxPages) page = maxPages - 1;
        if (page < 0) page = 0;

        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, allStamps.size());

        for (int i = start; i < end; i++) {
            int slot = i - start;
            StampData stamp = allStamps.get(i);

            if (unlockedStamps.contains(stamp.getId())) {
                // === NALEZENA ZNAMKA ===
                ItemBuilder builder = new ItemBuilder(stamp.getMaterial())
                        .setName(stamp.getName())
                        .setLore(stamp.getLore())
                        .setCustomModelData(stamp.getCustomModelData());
                getInventory().setItem(slot, builder.build());

            } else if (stamp.isLocked()) {
                // === CASOVE ZAMCENA ZNAMKA ===
                String matStr = plugin.getConfig().getString("gui.locked_stamp.material", "PAPER");
                String name = plugin.getConfig().getString("gui.locked_stamp.name", "&8Uzamceno");
                List<String> lore = plugin.getConfig().getStringList("gui.locked_stamp.lore");

                // Formátování pres thread-safe DateTimeFormatter
                String formattedDate = stamp.getUnlockDate().format(ConfigManager.DISPLAY_FORMAT);

                Material mat = Material.matchMaterial(matStr);
                if (mat == null) mat = Material.PAPER;

                ItemBuilder builder = new ItemBuilder(mat)
                        .setName(name)
                        .setLore(lore)
                        .replaceLore("{unlock_date}", formattedDate);
                getInventory().setItem(slot, builder.build());

            } else {
                // === NEOBJEVENA ZNAMKA ===
                String matStr = plugin.getConfig().getString("gui.unknown_stamp.material", "MAP");
                String name = plugin.getConfig().getString("gui.unknown_stamp.name", "&cNeobjeveno");
                List<String> lore = plugin.getConfig().getStringList("gui.unknown_stamp.lore");

                Material mat = Material.matchMaterial(matStr);
                if (mat == null) mat = Material.MAP;

                ItemBuilder builder = new ItemBuilder(mat)
                        .setName(name)
                        .setLore(lore);
                getInventory().setItem(slot, builder.build());
            }
        }

        // === STRANKOVACI TLACITKA ===
        if (page > 0) {
            ItemBuilder prev = new ItemBuilder(Material.ARROW)
                    .setName(plugin.getMessageManager().getRawMessage("gui_previous_page"));
            getInventory().setItem(45, prev.build());
            setAction(45, p -> new DenikGUI(plugin, p, unlockedStamps, page - 1).open(p));
        }

        if (page < maxPages - 1) {
            ItemBuilder next = new ItemBuilder(Material.ARROW)
                    .setName(plugin.getMessageManager().getRawMessage("gui_next_page"));
            getInventory().setItem(53, next.build());
            setAction(53, p -> new DenikGUI(plugin, p, unlockedStamps, page + 1).open(p));
        }
    }

    @Override
    public void open(Player p) {
        p.openInventory(getInventory());
    }
}
