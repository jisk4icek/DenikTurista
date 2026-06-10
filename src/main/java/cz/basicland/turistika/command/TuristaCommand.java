package cz.basicland.turistika.command;

import cz.basicland.turistika.BasicLandTuristika;
import cz.basicland.turistika.config.ConfigManager.StampData;
import cz.basicland.turistika.config.MessageManager;
import cz.basicland.turistika.mechanics.HologramManager;
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
 *  /turista give <hrac> <id>        - Prida znamku hracovi
 *  /turista top                     - Vypise TOP 10 do chatu
 *  /turista hologram spawn          - Spawnuje TextDisplay hologram na pozici hrace
 *  /turista hologram remove         - Odstrani leaderboard hologram
 *  /turista reload                  - Reload config + messages
 *  /turista list                    - Vypise vsechny znamky z configu
 *  /turista info <hrac>             - Zobrazi progres hrace
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
            case "give":      handleGive(sender, args);      break;
            case "top":       handleTop(sender);              break;
            case "hologram":  handleHologram(sender, args);  break;
            case "reload":    handleReload(sender);           break;
            case "list":      handleList(sender);             break;
            case "info":      handleInfo(sender, args);       break;
            default:          sendHelp(sender);               break;
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
        String stampId    = args[2];

        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(ChatUtil.error("Hrac &e" + targetName + " &cnen&i online."));
            return;
        }

        StampData stamp = plugin.getConfigManager().getStamp(stampId);
        if (stamp == null) {
            sender.sendMessage(ChatUtil.error("Znamka &e" + stampId + " &cneexistuje v configu."));
            sender.sendMessage(ChatUtil.info("Pouzij &e/turista list &7pro seznam vsech znamek."));
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
                    lines.add("&7Žádní hráči zatím nebyli nalezeni.");
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
                    sender.sendMessage(plugin.getMessageManager().getMessage("player_only"));
                    return;
                }
                Player p = (Player) sender;
                plugin.getHologramManager().createHologram(
                        p.getLocation().add(0, 0.5, 0),
                        HologramManager.LEADERBOARD_HOLOGRAM_ID
                );
                sender.sendMessage(ChatUtil.ok("Leaderboard hologram &aspawnut na tve pozici."));
                break;

            case "remove":
                plugin.getHologramManager().removeHologram(HologramManager.LEADERBOARD_HOLOGRAM_ID);
                sender.sendMessage(ChatUtil.ok("Leaderboard hologram &aodstraneny."));
                break;

            default:
                sender.sendMessage(ChatUtil.error("Pouziti: /turista hologram <spawn|remove>"));
                break;
        }
    }

    // ======================================================
    //  /turista reload
    // ======================================================

    private void handleReload(CommandSender sender) {
        plugin.getConfigManager().reload();
        plugin.getMessageManager().reload();
        ChatUtil.sendBox(sender, "RELOAD DOKONCEN",
                "&aconfig.yml &7✔",
                "&amessages.yml &7✔",
                "&7Pocet nactenych znamek: &e" + plugin.getConfigManager().getStamps().size()
        );
    }

    // ======================================================
    //  /turista list
    // ======================================================

    private void handleList(CommandSender sender) {
        Map<String, StampData> stamps = plugin.getConfigManager().getStamps();
        List<String> lines = new ArrayList<>();
        lines.add("&7Celkem &e" + stamps.size() + " &7znamek v configu:");
        for (Map.Entry<String, StampData> e : stamps.entrySet()) {
            String lock = e.getValue().isLocked() ? "&c[LOCKED]" : "&a[OK]";
            lines.add(lock + " &e" + e.getKey() + " &8- &7" + MessageManager.colorize(e.getValue().getName()));
        }
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
                ChatUtil.sendBox(sender, "PROFIL HRACE: " + target.getName(),
                        "&7Odemceno: &e" + unlocked.size() + " &7/ &e" + total,
                        "&7Progress: &e" + pct + "%",
                        "&7Pro dokonceni: &e" + needed + " &7znamek"
                );
            });
        });
    }

    // ======================================================
    //  HELP
    // ======================================================

    private void sendHelp(CommandSender sender) {
        ChatUtil.sendBox(sender, "BasicLandTuristika - Napoveda",
                "&e/turista give <hrac> <id> &7» Prida znamku",
                "&e/turista top &7» TOP 10 hracu",
                "&e/turista list &7» Vsechny znamky",
                "&e/turista info <hrac> &7» Progres hrace",
                "&e/turista hologram spawn &7» Spawnuj hologram",
                "&e/turista hologram remove &7» Odeber hologram",
                "&e/turista reload &7» Reload konfigurace"
        );
    }

    // ======================================================
    //  TAB COMPLETER
    // ======================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("turista.admin")) return Collections.emptyList();

        if (args.length == 1) {
            return Arrays.asList("give", "top", "list", "info", "hologram", "reload").stream()
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
