package cz.basicland.turistika.command;

import cz.basicland.turistika.BasicLandTuristika;
import cz.basicland.turistika.config.ConfigManager.StampData;
import cz.basicland.turistika.config.MessageManager;
import cz.basicland.turistika.mechanics.HologramManager;
import cz.basicland.turistika.mechanics.LocationManager;
import cz.basicland.turistika.utils.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hlavni admin prikaz /turista.
 *
 * Sub-prikazy:
 *  /turista give <hrac> <id>           - Prida znamku hracovi
 *  /turista top                         - Vypise TOP 10 do chatu
 *  /turista list                        - Vypise vsechny znamky z configu
 *  /turista info <hrac>                 - Zobrazi progres hrace
 *  /turista setlocation <id> [radius]   - Nastavi lokaci znamky na sve misto
 *  /turista removelocation <id>         - Odstrani lokaci znamky
 *  /turista locations                   - Vypise vsechny nastavene lokace
 *  /turista hologram spawn              - Spawnuje TextDisplay hologram
 *  /turista hologram remove             - Odstrani leaderboard hologram
 *  /turista reload                      - Reload config + messages
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

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

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
            default:               sendHelp(sender);                    break;
        }

        return true;
    }

    // ======================================================
    //  /turista give <hrac> <id>
    // ======================================================

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatUtil.error("Pouziti: &e/turista give <hrac> <id_znamky>"));
            return;
        }
        String targetName = args[1];
        String stampId = args[2];

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(ChatUtil.error("Hrac &e" + targetName + " &cneni online."));
            return;
        }

        StampData stamp = plugin.getConfigManager().getStamp(stampId);
        if (stamp == null) {
            sender.sendMessage(ChatUtil.error("Znamka &e" + stampId + " &cneexistuje."));
            sender.sendMessage(ChatUtil.info("Pouzij &e/turista list &7pro vsechny znamky."));
            return;
        }

        plugin.getDatabaseManager().hasStamp(target.getUniqueId(), stampId).thenAccept(has -> {
            if (has) {
                sender.sendMessage(ChatUtil.error("Hrac &e" + target.getName() + " &cuz tuto znamku ma."));
                return;
            }
            plugin.getDatabaseManager().addStamp(target.getUniqueId(), stampId).thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    target.sendMessage(plugin.getMessageManager().getMessage("stamp_received")
                            .replace("{stamp_name}", stamp.getName()));
                    sender.sendMessage(ChatUtil.ok("Znamka &e" + stampId + " &audělena hráči &e" + target.getName() + "&a."));
                    plugin.getServerFirstManager().handleFirstDiscovery(target, stampId, stamp.getName());
                    plugin.getDatabaseManager().getUnlockedStamps(target.getUniqueId()).thenAccept(stamps ->
                            plugin.getMilestoneManager().checkMilestones(target, stamps.size()));
                });
            });
        });
    }

    // ======================================================
    //  /turista top
    // ======================================================

    private void handleTop(CommandSender sender) {
        sender.sendMessage(ChatUtil.info("Načítám žebříček..."));
        plugin.getDatabaseManager().getTopPlayers(10).thenAccept(top -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                List<String> lines = new ArrayList<>();
                if (top.isEmpty()) {
                    lines.add("&7Zatím žádní hráči.");
                } else {
                    for (int i = 0; i < top.size(); i++) {
                        lines.add(ChatUtil.leaderboardLine(i + 1, top.get(i).getKey(), top.get(i).getValue(), "známek"));
                    }
                }
                ChatUtil.sendBox(sender, "TOP TURISTÉ ✦", lines.toArray(new String[0]));
            });
        });
    }

    // ======================================================
    //  /turista hologram [spawn|remove]
    // ======================================================

    private void handleHologram(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatUtil.error("Pouziti: /turista hologram <spawn|remove>"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "spawn":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getMessageManager().getMessage("player_only")); return;
                }
                Player p = (Player) sender;
                plugin.getHologramManager().createHologram(
                        p.getLocation().add(0, 0.5, 0),
                        HologramManager.LEADERBOARD_HOLOGRAM_ID
                );
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
                "&aconfig.yml        &7✔",
                "&amessages.yml      &7✔",
                "&aLokace znamek     &7✔",
                "&7Znamek: &e" + plugin.getConfigManager().getStamps().size(),
                "&7Lokaci: &e" + plugin.getLocationManager().getStampLocations().size()
        );
    }

    // ======================================================
    //  /turista list
    // ======================================================

    private void handleList(CommandSender sender) {
        Map<String, StampData> stamps = plugin.getConfigManager().getStamps();
        Map<String, LocationManager.StampLocation> locs = plugin.getLocationManager().getStampLocations();
        List<String> lines = new ArrayList<>();
        lines.add("&7Celkem &e" + stamps.size() + " &7znamek:");
        for (Map.Entry<String, StampData> e : stamps.entrySet()) {
            String lock = e.getValue().isLocked() ? "&c[LOCKED]" : "&a[OK]";
            String loc = locs.containsKey(e.getKey()) ? "&b[LOC]" : "&8[---]";
            lines.add(lock + " " + loc + " &e" + e.getKey());
        }
        lines.add("");
        lines.add("&b[LOC] &7= ma nastavenu lokaci  &8[---] &7= bez lokace");
        ChatUtil.sendBox(sender, "SEZNAM ZNAMEK", lines.toArray(new String[0]));
    }

    // ======================================================
    //  /turista info <hrac>
    // ======================================================

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatUtil.error("Pouziti: /turista info <hrac>"));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatUtil.error("Hrac &e" + args[1] + " &cneni online."));
            return;
        }
        plugin.getDatabaseManager().getUnlockedStamps(target.getUniqueId()).thenAccept(unlocked -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int total = plugin.getConfigManager().getStamps().size();
                int needed = plugin.getConfig().getInt("total_stamps_for_completion", total);
                int pct = total > 0 ? (unlocked.size() * 100) / total : 0;
                // Vizualni progress bar
                int bars = 20;
                int filled = (int) ((pct / 100.0) * bars);
                String bar = "&a" + "█".repeat(filled) + "&8" + "█".repeat(bars - filled);
                ChatUtil.sendBox(sender, "PROFIL: " + target.getName(),
                        "&7Znamky:   &e" + unlocked.size() + " &8/ &e" + total,
                        "&7Progress: " + bar + " &e" + pct + "%",
                        "&7Potreba pro dokonceni: &e" + needed
                );
            });
        });
    }

    // ======================================================
    //  /turista setlocation <stamp_id> [radius]
    // ======================================================

    private void handleSetLocation(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("player_only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatUtil.error("Pouziti: /turista setlocation <id_znamky> [radius]"));
            return;
        }

        String stampId = args[1];
        StampData stamp = plugin.getConfigManager().getStamp(stampId);
        if (stamp == null) {
            sender.sendMessage(ChatUtil.error("Znamka &e" + stampId + " &cneexistuje v config.yml."));
            return;
        }

        double radius = 5.0;
        if (args.length >= 3) {
            try {
                radius = Double.parseDouble(args[2]);
                if (radius <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatUtil.error("Radius musi byt kladne cislo. Napr: &e5.0"));
                return;
            }
        }

        Player player = (Player) sender;
        plugin.getLocationManager().saveLocation(stampId, player.getLocation(), radius);

        ChatUtil.sendBox(sender, "LOKACE NASTAVENA",
                "&7Znamka:  &e" + stampId,
                "&7Svet:    &e" + player.getWorld().getName(),
                "&7X Y Z:   &e" + (int)player.getX() + " " + (int)player.getY() + " " + (int)player.getZ(),
                "&7Radius:  &e" + radius + "m",
                "",
                "&7Hrac dostane znamku, kdyz vejde do tohoto okruhu."
        );
    }

    // ======================================================
    //  /turista removelocation <stamp_id>
    // ======================================================

    private void handleRemoveLocation(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatUtil.error("Pouziti: /turista removelocation <id_znamky>"));
            return;
        }
        String stampId = args[1];
        if (!plugin.getLocationManager().getStampLocations().containsKey(stampId)) {
            sender.sendMessage(ChatUtil.error("Znamka &e" + stampId + " &cnema nastavenu zadnou lokaci."));
            return;
        }
        plugin.getLocationManager().removeLocation(stampId);
        sender.sendMessage(ChatUtil.ok("Lokace pro znamku &e" + stampId + " &abyla odstranenena."));
    }

    // ======================================================
    //  /turista locations
    // ======================================================

    private void handleLocations(CommandSender sender) {
        Map<String, LocationManager.StampLocation> locs = plugin.getLocationManager().getStampLocations();
        List<String> lines = new ArrayList<>();
        if (locs.isEmpty()) {
            lines.add("&7Zadne lokace nejsou nastaveny.");
            lines.add("&7Pouzij &e/turista setlocation <id>");
        } else {
            for (Map.Entry<String, LocationManager.StampLocation> e : locs.entrySet()) {
                LocationManager.StampLocation sl = e.getValue();
                lines.add("&e" + e.getKey() + " &8» &7" + sl.worldName +
                        " &8[&7" + (int)sl.x + " " + (int)sl.y + " " + (int)sl.z +
                        "&8] &7r=&e" + sl.radius + "m");
            }
        }
        ChatUtil.sendBox(sender, "NASTAVENE LOKACE (" + locs.size() + ")", lines.toArray(new String[0]));
    }

    // ======================================================
    //  HELP
    // ======================================================

    private void sendHelp(CommandSender sender) {
        ChatUtil.sendBox(sender, "BasicLandTuristika - Nápověda",
                "&e/turista give <hrac> <id>           &7» Udeli znamku",
                "&e/turista top                         &7» TOP 10 hracu",
                "&e/turista list                        &7» Vsechny znamky",
                "&e/turista info <hrac>                 &7» Progres hrace",
                "&e/turista setlocation <id> [r]        &7» Nastav lokaci znamky",
                "&e/turista removelocation <id>         &7» Odeber lokaci",
                "&e/turista locations                   &7» Seznam lokaci",
                "&e/turista hologram spawn/remove       &7» Sprava hologramu",
                "&e/turista reload                      &7» Reload konfigurace"
        );
    }

    // ======================================================
    //  TAB COMPLETER
    // ======================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("turista.admin")) return Collections.emptyList();

        List<String> subCmds = Arrays.asList("give", "top", "list", "info",
                "setlocation", "removelocation", "locations", "hologram", "reload");

        if (args.length == 1) {
            return subCmds.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "give":
                case "info":
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                case "setlocation":
                    return plugin.getConfigManager().getStamps().keySet().stream()
                            .filter(id -> id.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                case "removelocation":
                    return plugin.getLocationManager().getStampLocations().keySet().stream()
                            .filter(id -> id.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                case "hologram":
                    return Arrays.asList("spawn", "remove").stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return plugin.getConfigManager().getStamps().keySet().stream()
                    .filter(id -> id.startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
