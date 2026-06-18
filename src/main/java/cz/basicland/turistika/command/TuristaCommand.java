package cz.basicland.turistika.command;

import cz.basicland.turistika.BasicLandTuristika;
import cz.basicland.turistika.config.ConfigManager.StampData;
import cz.basicland.turistika.config.MessageManager;
import cz.basicland.turistika.mechanics.HologramManager;
import cz.basicland.turistika.mechanics.LocationManager;
import cz.basicland.turistika.utils.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TuristaCommand v2.0
 *
 * Nové sub-příkazy:
 *  /turista npc spawn <id>       – Spawne NPC na tvé pozici
 *  /turista npc remove <id>      – Odstraní NPC
 *  /turista npc list             – Vypíše všechna NPC
 *  /turista tp <stamp_id>        – Admin teleport na lokaci zna mky
 *  /turista list                 – Nyní ukazuje i [NPC] indikátor
 */
public class TuristaCommand implements CommandExecutor, TabCompleter {

    private final BasicLandTuristika plugin;

    public TuristaCommand(BasicLandTuristika plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("turista.admin")) {
            sender.sendMessage(plugin.getMessageManager().getMessage("no_permission"));
            return true;
        }
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "give":           handleGive(sender, args);           break;
            case "top":            handleTop(sender);                   break;
            case "hologram":       handleHologram(sender, args);        break;
            case "reload":         handleReload(sender);                break;
            case "list":           handleList(sender);                  break;
            case "info":           handleInfo(sender, args);            break;
            case "setlocation":    handleSetLocation(sender, args);     break;
            case "removelocation": handleRemoveLocation(sender, args);  break;
            case "locations":      handleLocations(sender);             break;
            case "npc":            handleNpc(sender, args);             break;
            case "marker":         handleMarker(sender, args);          break;
            case "board":          handleBoard(sender, args);           break;
            case "tp":             handleTp(sender, args);              break;
            default:               sendHelp(sender);
        }
        return true;
    }

    // ======================================================
    //  /turista give <hrac> <id>
    // ======================================================

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatUtil.error("Pouziti: &e/turista give <hrac> <id_znamky>")); return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { sender.sendMessage(ChatUtil.error("Hrac &e" + args[1] + " &cneni online.")); return; }

        StampData stamp = plugin.getConfigManager().getStamp(args[2]);
        if (stamp == null) {
            sender.sendMessage(ChatUtil.error("Znamka &e" + args[2] + " &cneexistuje."));
            sender.sendMessage(ChatUtil.info("Pouzij &e/turista list &7pro vsechny znamky."));
            return;
        }

        plugin.getDatabaseManager().hasStamp(target.getUniqueId(), args[2]).thenAccept(has -> {
            if (has) { sender.sendMessage(ChatUtil.error("Hrac &e" + target.getName() + " &cuz tuto znamku ma.")); return; }
            plugin.getDatabaseManager().addStamp(target.getUniqueId(), args[2]).thenRun(() ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    target.sendMessage(plugin.getMessageManager().getMessage("stamp_received")
                            .replace("{stamp_name}", stamp.getName()));
                    sender.sendMessage(ChatUtil.ok("Znamka &e" + args[2] + " &audélena hraci &e" + target.getName() + "&a."));
                    plugin.getServerFirstManager().handleFirstDiscovery(target, args[2], stamp.getName());
                    plugin.getDatabaseManager().getUnlockedStamps(target.getUniqueId())
                            .thenAccept(s -> plugin.getMilestoneManager().checkMilestones(target, s.size()));
                })
            );
        });
    }

    // ======================================================
    //  /turista top
    // ======================================================

    private void handleTop(CommandSender sender) {
        sender.sendMessage(ChatUtil.info("Načítám žebříček..."));
        int total = plugin.getConfigManager().getStamps().size();
        plugin.getDatabaseManager().getTopPlayers(10).thenAccept(top ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                List<String> lines = new ArrayList<>();
                if (top.isEmpty()) {
                    lines.add("&7Zatím žádní hráči.");
                } else {
                    String[] medals = {"&6★ 1.", "&f★ 2.", "&c★ 3."};
                    for (int i = 0; i < top.size(); i++) {
                        String rank = i < 3 ? medals[i] : "&7" + (i+1) + ".";
                        int pct = total > 0 ? (top.get(i).getValue() * 100) / total : 0;
                        lines.add(rank + " &e" + top.get(i).getKey() +
                                " &8» &a" + top.get(i).getValue() + " &7zn. &8(&e" + pct + "%&8)");
                    }
                }
                lines.add("");
                lines.add("&7Celkem zna mek v eventu: &e" + total);
                ChatUtil.sendBox(sender, "TOP TURISTÉ ✦", lines.toArray(new String[0]));
            })
        );
    }

    // ======================================================
    //  /turista npc <spawn|remove|list> [id]
    // ======================================================

    private void handleNpc(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatUtil.error("Pouziti: /turista npc <spawn|remove|list> [id]")); return;
        }
        switch (args[1].toLowerCase()) {
            case "spawn": {
                if (!(sender instanceof Player)) { sender.sendMessage(plugin.getMessageManager().getMessage("player_only")); return; }
                if (args.length < 3) { sender.sendMessage(ChatUtil.error("Pouziti: /turista npc spawn <stamp_id>")); return; }
                String stampId = args[2];
                if (plugin.getConfigManager().getStamp(stampId) == null) {
                    sender.sendMessage(ChatUtil.error("Znamka &e" + stampId + " &cneexistuje.")); return;
                }
                Player p = (Player) sender;
                boolean ok = plugin.getNpcManager().spawnNpc(stampId, p.getLocation());
                if (ok) {
                    ChatUtil.sendBox(sender, "NPC SPAWNUT",
                            "&7Znamka:  &e" + stampId,
                            "&7Svet:    &e" + p.getWorld().getName(),
                            "&7Pozice:  &e" + (int)p.getX() + " " + (int)p.getY() + " " + (int)p.getZ(),
                            "",
                            "&7Hrac klikne (pravym) na NPC a dostane znamku.");
                } else {
                    sender.sendMessage(ChatUtil.error("Nepodarilo se spawnut NPC."));
                }
                break;
            }
            case "remove": {
                if (args.length < 3) { sender.sendMessage(ChatUtil.error("Pouziti: /turista npc remove <stamp_id>")); return; }
                String stampId = args[2];
                plugin.getNpcManager().removeNpc(stampId);
                sender.sendMessage(ChatUtil.ok("NPC pro znamku &e" + stampId + " &abyl odstranen."));
                break;
            }
            case "list": {
                Map<String, java.util.UUID> npcs = plugin.getNpcManager().getActiveNpcs();
                List<String> lines = new ArrayList<>();
                if (npcs.isEmpty()) {
                    lines.add("&7Zadna NPC nejsou aktivni.");
                    lines.add("&7Pouzij &e/turista npc spawn <id>");
                } else {
                    npcs.forEach((id, uuid) -> lines.add("&e" + id + " &8» &7UUID entity: &e" + uuid.toString().substring(0, 8) + "..."));
                }
                ChatUtil.sendBox(sender, "AKTIVNI NPC (" + npcs.size() + ")", lines.toArray(new String[0]));
                break;
            }
            default:
                sender.sendMessage(ChatUtil.error("Pouziti: /turista npc <spawn|remove|list>"));
        }
    }

    // ======================================================
    //  /turista tp <stamp_id>  – Admin teleport na lokaci
    // ======================================================

    private void handleTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getMessageManager().getMessage("player_only")); return; }
        if (args.length < 2) { sender.sendMessage(ChatUtil.error("Pouziti: /turista tp <stamp_id>")); return; }

        String stampId = args[1];
        LocationManager.StampLocation sl = plugin.getLocationManager().getStampLocations().get(stampId);
        if (sl == null) {
            sender.sendMessage(ChatUtil.error("Znamka &e" + stampId + " &cnema nastavenou lokaci."));
            sender.sendMessage(ChatUtil.info("Nastav ji pres &e/turista setlocation &7nebo &e/turista npc list"));
            return;
        }

        org.bukkit.World world = Bukkit.getWorld(sl.worldName);
        if (world == null) { sender.sendMessage(ChatUtil.error("Svet &e" + sl.worldName + " &cneni nacteny.")); return; }

        Player p = (Player) sender;
        p.teleport(new org.bukkit.Location(world, sl.x, sl.y + 0.5, sl.z));
        sender.sendMessage(ChatUtil.ok("Teleportovan na lokaci znamky &e" + stampId + "&a."));
    }

    // ======================================================
    //  /turista marker <spawn|remove|list> [id]
    // ======================================================

    private void handleMarker(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatUtil.error("Pouziti: /turista marker <spawn|remove|list> [id]"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "spawn": {
                if (!(sender instanceof Player)) { sender.sendMessage(plugin.getMessageManager().getMessage("player_only")); return; }
                if (args.length < 3) { sender.sendMessage(ChatUtil.error("Pouziti: /turista marker spawn <stamp_id>")); return; }
                String stampId = args[2];
                if (plugin.getConfigManager().getStamp(stampId) == null) {
                    sender.sendMessage(ChatUtil.error("Znamka &e" + stampId + " &cneexistuje.")); return;
                }
                Player p = (Player) sender;
                boolean ok = plugin.getMarkerManager().spawnMarker(stampId, p.getLocation());
                if (ok) {
                    ChatUtil.sendBox(sender, "ARMORSTAND MARKER SPAWNUT",
                            "&7Znamka:  &e" + stampId,
                            "&7Pozice:  &e" + (int)p.getX() + " " + (int)p.getY() + " " + (int)p.getZ(),
                            "",
                            "&a✔ Hrac klikne (pravym) na ArmorStand a dostane znamku.",
                            "&7Marker sviti (Glowing) pro lepsi viditelnost."
                    );
                } else {
                    sender.sendMessage(ChatUtil.error("Nepodarilo se spawnut Marker."));
                }
                break;
            }
            case "remove": {
                if (args.length < 3) { sender.sendMessage(ChatUtil.error("Pouziti: /turista marker remove <stamp_id>")); return; }
                plugin.getMarkerManager().removeMarker(args[2]);
                sender.sendMessage(ChatUtil.ok("Marker pro znamku &e" + args[2] + " &aodstranen."));
                break;
            }
            case "list": {
                Map<String, java.util.UUID> markers = plugin.getMarkerManager().getActiveMarkers();
                List<String> lines = new ArrayList<>();
                if (markers.isEmpty()) {
                    lines.add("&7Zadne ArmorStand markery nejsou aktivni.");
                    lines.add("&7Nastav lokaci pres &e/turista setlocation <id>");
                    lines.add("&7(marker se spawne automaticky)");
                } else {
                    markers.forEach((id, uuid) -> {
                        var loc = plugin.getLocationManager().getStampLocations().get(id);
                        String pos = loc != null ? (int)loc.x + " " + (int)loc.y + " " + (int)loc.z : "neznama";
                        lines.add("&e" + id + " &8» &7" + pos);
                    });
                }
                ChatUtil.sendBox(sender, "ARMORSTAND MARKERY (" + markers.size() + ")", lines.toArray(new String[0]));
                break;
            }
            default:
                sender.sendMessage(ChatUtil.error("Pouziti: /turista marker <spawn|remove|list>"));
        }
    }

    // ======================================================
    //  /turista hologram [spawn|remove]
    // ======================================================

    private void handleHologram(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(ChatUtil.error("Pouziti: /turista hologram <spawn|remove>")); return; }
        switch (args[1].toLowerCase()) {
            case "spawn":
                if (!(sender instanceof Player)) { sender.sendMessage(plugin.getMessageManager().getMessage("player_only")); return; }
                Player p = (Player) sender;
                plugin.getHologramManager().createHologram(p.getLocation().add(0, 0.5, 0), HologramManager.LEADERBOARD_HOLOGRAM_ID);
                sender.sendMessage(ChatUtil.ok("Leaderboard hologram spawnut na tve pozici."));
                break;
            case "remove":
                plugin.getHologramManager().removeHologram(HologramManager.LEADERBOARD_HOLOGRAM_ID);
                sender.sendMessage(ChatUtil.ok("Leaderboard hologram odstranen."));
                break;
            default:
                sender.sendMessage(ChatUtil.error("Pouziti: /turista hologram <spawn|remove>"));
        }
    }

    // ======================================================
    //  /turista reload
    // ======================================================

    private void handleReload(CommandSender sender) {
        plugin.getConfigManager().reload();
        plugin.getMessageManager().reload();
        plugin.getLocationManager().loadLocations();
        ChatUtil.sendBox(sender, "RELOAD DOKONCEN",
                "&aconfig.yml           &7✔",
                "&amessages.yml         &7✔",
                "&aLokace znamek        &7✔",
                "&7Znamek:   &e" + plugin.getConfigManager().getStamps().size(),
                "&7Lokaci:   &e" + plugin.getLocationManager().getStampLocations().size(),
                "&7Markeru:  &e" + plugin.getMarkerManager().getActiveMarkers().size(),
                "&7NPC:      &e" + plugin.getNpcManager().getActiveNpcs().size()
        );
    }

    // ======================================================
    //  /turista list
    // ======================================================

    private void handleList(CommandSender sender) {
        Map<String, StampData> stamps = plugin.getConfigManager().getStamps();
        Map<String, LocationManager.StampLocation> locs = plugin.getLocationManager().getStampLocations();
        Map<String, java.util.UUID> npcs = plugin.getNpcManager().getActiveNpcs();
        List<String> lines = new ArrayList<>();
        lines.add("&7Celkem &e" + stamps.size() + " &7znamek:");
        lines.add("&8(status) [LOC] [NPC] ID");
        for (Map.Entry<String, StampData> e : stamps.entrySet()) {
            StampData s = e.getValue();
            String status = s.isLocked() ? "&c[LOCK]" : (s.isExpired() ? "&4[EXP]" : s.isOutOfWindow() ? "&6[WIN]" : "&a[OK] ");
            String loc = locs.containsKey(e.getKey()) ? "&b[LOC]" : "&8[---]";
            String npc = npcs.containsKey(e.getKey()) ? "&d[NPC]" : "&8[---]";
            lines.add(status + " " + loc + " " + npc + " &e" + e.getKey());
        }
        lines.add("");
        lines.add("&a[OK] &7dostupna  &c[LOCK] &7zamcena  &6[WIN] &7mimo cas  &4[EXP] &7expirovala");
        lines.add("&b[LOC] &7ma lokaci  &d[NPC] &7ma NPC");
        ChatUtil.sendBox(sender, "SEZNAM ZNAMEK", lines.toArray(new String[0]));
    }

    // ======================================================
    //  /turista info <hrac>
    // ======================================================

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(ChatUtil.error("Pouziti: /turista info <hrac>")); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { sender.sendMessage(ChatUtil.error("Hrac &e" + args[1] + " &cneni online.")); return; }
        plugin.getDatabaseManager().getUnlockedStamps(target.getUniqueId()).thenAccept(unlocked ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                int total  = plugin.getConfigManager().getStamps().size();
                int pct    = total > 0 ? (unlocked.size() * 100) / total : 0;
                int filled = (int) ((pct / 100.0) * 20);
                String bar = "&a" + "█".repeat(filled) + "&8" + "█".repeat(20 - filled);
                ChatUtil.sendBox(sender, "PROFIL: " + target.getName(),
                        "&7Znamky:   &e" + unlocked.size() + " &8/ &e" + total + " &8(" + pct + "%)",
                        "&7Progress: " + bar,
                        "&7Chybi:    &e" + (total - unlocked.size()) + " &7znamek do dokonceni"
                );
            })
        );
    }

    // ======================================================
    //  /turista setlocation <stamp_id> [radius]
    // ======================================================

    private void handleSetLocation(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage(plugin.getMessageManager().getMessage("player_only")); return; }
        if (args.length < 2) { sender.sendMessage(ChatUtil.error("Pouziti: /turista setlocation <id> [radius]")); return; }
        String stampId = args[1];
        if (plugin.getConfigManager().getStamp(stampId) == null) {
            sender.sendMessage(ChatUtil.error("Znamka &e" + stampId + " &cneexistuje v config.yml.")); return;
        }
        double radius = 5.0;
        if (args.length >= 3) {
            try { radius = Double.parseDouble(args[2]); if (radius <= 0) throw new NumberFormatException(); }
            catch (NumberFormatException e) { sender.sendMessage(ChatUtil.error("Radius musi byt kladne cislo.")); return; }
        }
        Player p = (Player) sender;
        plugin.getLocationManager().saveLocation(stampId, p.getLocation(), radius);

        // === AUTO-SPAWN ARMORSTAND MARKERU ===
        boolean markerSpawned = plugin.getMarkerManager().spawnMarker(stampId, p.getLocation());

        ChatUtil.sendBox(sender, "LOKACE + MARKER NASTAVEN",
                "&7Znamka:  &e" + stampId,
                "&7Svet:    &e" + p.getWorld().getName(),
                "&7X Y Z:   &e" + (int)p.getX() + " " + (int)p.getY() + " " + (int)p.getZ(),
                "&7Radius:  &e" + radius + "m",
                "",
                markerSpawned ? "&a✔ ArmorStand Marker spawnut na te pozici!" : "&cMarker se nepodarilo spawnut.",
                "&7Hrac klikne na marker NEBO vejde do okruhu &e" + radius + "m",
                "&7Pro NPC (Villager): &e/turista npc spawn &7" + stampId,
                "&7Pro presun markeru: &e/turista marker spawn &7" + stampId
        );
    }

    private void handleRemoveLocation(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(ChatUtil.error("Pouziti: /turista removelocation <id>")); return; }
        if (!plugin.getLocationManager().getStampLocations().containsKey(args[1])) {
            sender.sendMessage(ChatUtil.error("Znamka &e" + args[1] + " &cnema nastavenou lokaci.")); return;
        }
        plugin.getLocationManager().removeLocation(args[1]);
        // Odstran i ArmorStand marker (pokud existuje)
        plugin.getMarkerManager().removeMarker(args[1]);
        sender.sendMessage(ChatUtil.ok("Lokace + Marker pro znamku &e" + args[1] + " &abyla odstraneneny."));
    }

    private void handleLocations(CommandSender sender) {
        Map<String, LocationManager.StampLocation> locs = plugin.getLocationManager().getStampLocations();
        List<String> lines = new ArrayList<>();
        if (locs.isEmpty()) {
            lines.add("&7Zadne lokace nejsou nastaveny."); lines.add("&7Pouzij &e/turista setlocation <id>");
        } else {
            locs.forEach((id, sl) -> lines.add("&e" + id + " &8» &7" + sl.worldName +
                    " &8[&7" + (int)sl.x + " " + (int)sl.y + " " + (int)sl.z + "&8] &7r=&e" + sl.radius + "m"));
        }
        ChatUtil.sendBox(sender, "NASTAVENE LOKACE (" + locs.size() + ")", lines.toArray(new String[0]));
    }

    // ======================================================
    //  /turista board <spawn|remove|list|refresh> [id]
    // ======================================================

    private void handleBoard(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatUtil.error("Pouziti: /turista board <spawn|remove|list|refresh> [id]"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "spawn": {
                if (!(sender instanceof Player)) { sender.sendMessage(plugin.getMessageManager().getMessage("player_only")); return; }
                Player p = (Player) sender;
                // ID: použij zadané nebo auto-generuj
                String boardId = args.length >= 3 ? args[2] : plugin.getBoardManager().generateBoardId();
                boolean ok = plugin.getBoardManager().spawnBoard(boardId, p.getLocation());
                if (ok) {
                    ChatUtil.sendBox(sender, "NÁSTĚNKA SPAWNUTA",
                            "&7ID:      &e" + boardId,
                            "&7Svět:    &e" + p.getWorld().getName(),
                            "&7X Y Z:   &e" + (int)p.getX() + " " + (int)p.getY() + " " + (int)p.getZ(),
                            "&7Směr:    &e" + Math.round(p.getYaw()) + "°",
                            "",
                            "&a✔ Fyzická nástěnka zobrazuje TOP 3 hráče!",
                            "&7Auto-refresh každé 2 minuty.",
                            "&7Pro refresh: &e/turista board refresh"
                    );
                } else {
                    sender.sendMessage(ChatUtil.error("Nepodarilo se spawnut nástěnku."));
                }
                break;
            }
            case "remove": {
                if (args.length < 3) { sender.sendMessage(ChatUtil.error("Pouziti: /turista board remove <id>")); return; }
                plugin.getBoardManager().removeBoard(args[2]);
                sender.sendMessage(ChatUtil.ok("Nástěnka &e" + args[2] + " &abyla odstraněna."));
                break;
            }
            case "list": {
                Map<String, java.util.UUID> boards = plugin.getBoardManager().getActiveBoards();
                List<String> lines = new ArrayList<>();
                if (boards.isEmpty()) {
                    lines.add("&7Žádné nástěnky nejsou aktivní.");
                    lines.add("&7Spawnuj: &e/turista board spawn [id]");
                } else {
                    boards.forEach((id, uuid) -> lines.add("&e" + id + " &8» &7UUID: &e" + uuid.toString().substring(0, 8) + "..."));
                }
                ChatUtil.sendBox(sender, "TURISTICKÉ NÁSTĚNKY (" + boards.size() + ")", lines.toArray(new String[0]));
                break;
            }
            case "refresh": {
                plugin.getBoardManager().refreshAll();
                sender.sendMessage(ChatUtil.ok("Všechny nástěnky (&e" + plugin.getBoardManager().getActiveBoards().size() + "&a) byly aktualizovány."));
                break;
            }
            default:
                sender.sendMessage(ChatUtil.error("Pouziti: /turista board <spawn|remove|list|refresh> [id]"));
        }
    }

    // ======================================================
    //  HELP
    // ======================================================

    private void sendHelp(CommandSender sender) {
        ChatUtil.sendBox(sender, "BasicLandTuristika v2.2 – Napoveda",
                "&e/turista give <hrac> <id>         &7» Udeli znamku",
                "&e/turista top                       &7» TOP 10 hracu s %",
                "&e/turista list                      &7» Vsechny znamky + stavy",
                "&e/turista info <hrac>               &7» Progres hrace",
                "&e/turista setlocation <id> [r]     &7» Lokace + auto-spawn Markeru",
                "&e/turista removelocation <id>       &7» Odeber lokaci + Marker",
                "&e/turista locations                 &7» Seznam lokaci",
                "&e/turista tp <id>                   &7» Admin TP na lokaci",
                "&6&l--- ArmorStand Průvodce ---",
                "&e/turista marker spawn <id>         &7» (Re)spawn průvodce",
                "&e/turista marker remove <id>        &7» Odstran průvodce",
                "&e/turista marker list               &7» Vsechni průvodci",
                "&6&l--- Fyzická Nástěnka ---",
                "&e/turista board spawn [id]          &7» Spawn nástěnky na pozici",
                "&e/turista board remove <id>         &7» Odstran nástěnku",
                "&e/turista board list                &7» Vsechny nástěnky",
                "&e/turista board refresh             &7» Manuální refresh",
                "&b&l--- Villager NPC ---",
                "&e/turista npc spawn <id>            &7» Spawne Villager (s AI)",
                "&e/turista npc remove <id>           &7» Odstran NPC",
                "&e/turista npc list                  &7» Vsechna NPC",
                "&b&l--- Ostatni ---",
                "&e/turista hologram spawn/remove     &7» Floating hologram",
                "&e/turista reload                    &7» Reload konfigurace"
        );
    }

    // ======================================================
    //  TAB COMPLETER
    // ======================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("turista.admin")) return Collections.emptyList();

        List<String> sub = Arrays.asList("give","top","list","info","setlocation","removelocation",
                "locations","hologram","reload","npc","marker","board","tp");

        if (args.length == 1) return sub.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "give": case "info":
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                case "setlocation": case "tp":
                    return plugin.getConfigManager().getStamps().keySet().stream()
                            .filter(id -> id.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                case "removelocation":
                    return plugin.getLocationManager().getStampLocations().keySet().stream()
                            .filter(id -> id.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                case "hologram":
                    return Arrays.asList("spawn","remove").stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                case "npc": case "marker":
                    return Arrays.asList("spawn","remove","list").stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                case "board":
                    return Arrays.asList("spawn","remove","list","refresh").stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "give":
                    return plugin.getConfigManager().getStamps().keySet().stream()
                            .filter(id -> id.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
                case "npc":
                    if (args[1].equalsIgnoreCase("spawn"))
                        return plugin.getConfigManager().getStamps().keySet().stream()
                                .filter(id -> id.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
                    if (args[1].equalsIgnoreCase("remove"))
                        return new ArrayList<>(plugin.getNpcManager().getActiveNpcs().keySet()).stream()
                                .filter(id -> id.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
                    break;
                case "marker":
                    if (args[1].equalsIgnoreCase("spawn"))
                        return plugin.getConfigManager().getStamps().keySet().stream()
                                .filter(id -> id.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
                    if (args[1].equalsIgnoreCase("remove"))
                        return new ArrayList<>(plugin.getMarkerManager().getActiveMarkers().keySet()).stream()
                                .filter(id -> id.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }
}
