package cz.basicland.turistika.mechanics;

import cz.basicland.turistika.BasicLandTuristika;
import cz.basicland.turistika.config.MessageManager;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * BoardManager v1.0 – Fyzická turistická nástěnka (TextDisplay entita)
 *
 * Na rozdíl od HologramManageru (Billboard.CENTER = vždy otočeno k hráči),
 * Board je Billboard.FIXED = stojí jako fyzická deska ve světě, hráč ji
 * vidí jen z přední strany. Ideální pro umístění na zeď nebo stůl v itemframe.
 *
 * Vzhled:
 *  ┌─────────────────────────────┐
 *  │ ★ TURISTICKÁ NÁSTĚNKA ★     │  ← zlatá hlavička
 *  │ ━━━━━━━━━━━━━━━━━━━━━━━━━   │
 *  │ 🥇 Hráč1          10 ✦ 100% │  ← TOP 3 s medailemi
 *  │ 🥈 Hráč2           8 ✦  80% │
 *  │ 🥉 Hráč3           6 ✦  60% │
 *  │ ━━━━━━━━━━━━━━━━━━━━━━━━━   │
 *  │ 📊 Celkem známek: 12        │  ← statistiky
 *  │ 🔥 Aktivní hráči: 47         │
 *  └─────────────────────────────┘
 *
 * Příkazy:
 *  /turista board spawn [id]  – Spawne board na pozici admina (yaw = směr pohledu)
 *  /turista board remove <id> – Odstraní board
 *  /turista board list        – Vypíše všechny boardy
 *  /turista board refresh     – Manuální refresh obsahu
 *
 * Admin může mít více boardů (každý má unikátní ID).
 * Obsah se auto-refreshuje každé 2 minuty.
 * Pozice persistovány v SQLite.
 */
public class BoardManager {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // PDC klíče pro identifikaci board entit
    public static final NamespacedKey BOARD_ID_KEY = new NamespacedKey("turistika", "board_id");
    public static final NamespacedKey BOARD_TAG    = new NamespacedKey("turistika", "turistika_board");

    private final BasicLandTuristika plugin;
    // boardId -> UUID TextDisplay entity
    private final Map<String, UUID> activeBoards = new HashMap<>();
    private BukkitTask refreshTask;
    private int boardCounter = 1;

    public BoardManager(BasicLandTuristika plugin) {
        this.plugin = plugin;
    }

    // ======================================================
    //  SPAWN / REMOVE
    // ======================================================

    /**
     * Spawne fyzický board na dané lokaci.
     * Yaw lokace určuje, kam board "koukí" – admin stojí čelem k budoucí stěně.
     *
     * @param boardId  unikátní ID boardu (např. "board_1", "spawn_board")
     * @param loc      lokace + yaw (směr pohledu admina)
     */
    public boolean spawnBoard(String boardId, Location loc) {
        removeBoard(boardId);

        TextDisplay td = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            // ─── FYZICKÉ vlastnosti ─────────────────────────────────────
            d.setBillboard(Display.Billboard.FIXED);  // Stojí na místě jako deska
            d.setPersistent(true);
            // TextDisplay entita nemá setRemoveWhenFarAway – je persistent

            // ─── VELIKOST – jako A3 nástěnka ────────────────────────────
            // Zvětšení pomoci Transformation scale
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new Quaternionf(),
                    new Vector3f(0.55f, 0.55f, 0.55f),
                    new Quaternionf()
            ));

            // ─── STYL textu ─────────────────────────────────────────────
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(210, 30, 20, 10)); // Tmavě hnědá – dřevo
            d.setShadowed(true);
            d.setAlignment(TextDisplay.TextAlignment.LEFT);
            d.setLineWidth(260); // Šířka "desky"
            d.setTextOpacity((byte) 255);

            // ─── PDC tag ─────────────────────────────────────────────────
            PersistentDataContainer pdc = d.getPersistentDataContainer();
            pdc.set(BOARD_ID_KEY, PersistentDataType.STRING, boardId);
            pdc.set(BOARD_TAG,    PersistentDataType.BYTE,   (byte) 1);

            // Placeholder text při spawnu
            d.setText(MessageManager.colorize("&e&lNástěnka se načítá..."));
        });

        activeBoards.put(boardId, td.getUniqueId());
        plugin.getDatabaseManager().saveBoard(boardId, loc, td.getUniqueId());
        plugin.getLogger().info("Board '" + boardId + "' spawnut.");

        // Ihned načti obsah
        updateBoardContent(boardId, td.getUniqueId());
        return true;
    }

    public void removeBoard(String boardId) {
        UUID uuid = activeBoards.remove(boardId);
        if (uuid != null) {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null) e.remove();
        }
        plugin.getDatabaseManager().deleteBoard(boardId);
    }

    public void loadFromDatabase() {
        plugin.getDatabaseManager().loadBoards().thenAccept(list -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (var data : list) {
                    World world = Bukkit.getWorld(data.world);
                    if (world == null) continue;
                    if (data.entityUUID != null) {
                        Entity found = Bukkit.getEntity(data.entityUUID);
                        if (found instanceof TextDisplay && !found.isDead()) {
                            activeBoards.put(data.id, found.getUniqueId());
                            updateBoardContent(data.id, found.getUniqueId());
                            continue;
                        }
                    }
                    Location loc = new Location(world, data.x, data.y, data.z, data.yaw, 0);
                    spawnBoard(data.id, loc);
                }
                // Aktualizuj čítač aby nové boardy měly unikátní ID
                boardCounter = list.size() + 1;
                plugin.getLogger().info("Nacteno " + list.size() + " turistickych nastenky.");
            });
        });
    }

    /** Spustí auto-refresh každé 2 minuty (120s). */
    public void startRefreshTask() {
        refreshTask = new BukkitRunnable() {
            @Override public void run() {
                activeBoards.forEach((id, uuid) -> updateBoardContent(id, uuid));
            }
        }.runTaskTimer(plugin, 20L * 120, 20L * 120);
    }

    public void stopRefreshTask() {
        if (refreshTask != null && !refreshTask.isCancelled()) refreshTask.cancel();
    }

    /** Manuálně refreshne všechny boardy (admin příkaz). */
    public void refreshAll() {
        activeBoards.forEach((id, uuid) -> updateBoardContent(id, uuid));
    }

    public Map<String, UUID> getActiveBoards() { return Collections.unmodifiableMap(activeBoards); }

    /** Generuje nové auto-ID pro board (board_1, board_2, ...) */
    public String generateBoardId() {
        return "board_" + boardCounter++;
    }

    public void shutdown() { activeBoards.clear(); }

    // ======================================================
    //  OBSAH NÁSTĚNKY
    // ======================================================

    /**
     * Načte data z DB a sestaví obsah nástěnky.
     * Volá se async (DB) + sync (entity update).
     */
    private void updateBoardContent(String boardId, UUID entityUUID) {
        int totalStamps = plugin.getConfigManager().getStamps().size();
        long onlineCount = Bukkit.getOnlinePlayers().size();

        plugin.getDatabaseManager().getTopPlayers(3).thenAccept(top -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Entity entity = Bukkit.getEntity(entityUUID);
                if (!(entity instanceof TextDisplay td)) return;

                String content = buildBoardContent(top, totalStamps, (int) onlineCount);
                td.setText(content);
            });
        });
    }

    /**
     * Sestaví formátovaný text nástěnky.
     * Používá pseudo-grafické znaky pro efekt dřevěné desky.
     */
    private String buildBoardContent(List<Map.Entry<String, Integer>> top, int totalStamps, int onlineCount) {
        StringBuilder sb = new StringBuilder();

        // ─── ZÁHLAVÍ ────────────────────────────────────────────────────
        sb.append(MessageManager.colorize("&6&l ★ TURISTICKÁ NÁSTĚNKA ★")).append("\n");
        sb.append(MessageManager.colorize("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━")).append("\n");

        // ─── TOP 3 HRÁČI ────────────────────────────────────────────────
        sb.append(MessageManager.colorize("&e&l TOP 3 TURISTÉ:")).append("\n");

        String[] medals = {"&6✦ #1", "&f✦ #2", "&c✦ #3"};
        String[] bg     = {"&6",     "&f",      "&c"};

        if (top.isEmpty()) {
            sb.append(MessageManager.colorize("  &8Zatím žádní turisté...\n"));
        } else {
            for (int i = 0; i < Math.min(top.size(), 3); i++) {
                String medal = MessageManager.colorize(medals[i]);
                String name  = top.get(i).getKey();
                int    count = top.get(i).getValue();
                int    pct   = totalStamps > 0 ? (count * 100) / totalStamps : 0;
                String bar   = buildMiniBar(count, totalStamps, 8);

                sb.append(MessageManager.colorize(
                        medals[i] + " " + bg[i] + padRight(name, 12) +
                        " &8» " + bg[i] + count + " ✦ " +
                        bar + " " + pct + "%"
                )).append("\n");
            }
        }

        // Zbývající místa (pokud méně než 3 hráči)
        for (int i = top.size(); i < 3; i++) {
            sb.append(MessageManager.colorize("  &8" + (i+1) + ". ─────────────────")).append("\n");
        }

        sb.append(MessageManager.colorize("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━")).append("\n");

        // ─── STATISTIKY ─────────────────────────────────────────────────
        sb.append(MessageManager.colorize("&b&l STATISTIKY:")).append("\n");
        sb.append(MessageManager.colorize("  &7známek v eventu: &e" + totalStamps)).append("\n");
        sb.append(MessageManager.colorize("  &7Online hráčů:     &a" + onlineCount)).append("\n");

        // Celkový globální progres (průměr)
        if (!top.isEmpty()) {
            int totalCollected = top.stream().mapToInt(Map.Entry::getValue).sum();
            sb.append(MessageManager.colorize("  &7Top 3 sesbírali: &e" + totalCollected + " &7známek")).append("\n");
        }

        sb.append(MessageManager.colorize("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━")).append("\n");
        sb.append(MessageManager.colorize("&8 Aktualizováno: &7" + LocalDateTime.now().format(TIME_FMT)));

        return sb.toString();
    }

    /** Miniaturní progress bar pro board (8 znaků). */
    private String buildMiniBar(int done, int total, int len) {
        if (total == 0) return "&8" + "░".repeat(len);
        int filled = Math.min(len, (int) Math.round((double) done / total * len));
        return "&a" + "█".repeat(filled) + "&8" + "░".repeat(len - filled);
    }

    /** Zarovná string na délku paddingem (pro zarovnání sloupců). */
    private String padRight(String s, int len) {
        if (s.length() >= len) return s.substring(0, len);
        return s + " ".repeat(len - s.length());
    }
}
