package cz.basicland.turistika.mechanics;

import cz.basicland.turistika.BasicLandTuristika;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;

public class ServerFirstManager {

    private final BasicLandTuristika plugin;

    public ServerFirstManager(BasicLandTuristika plugin) {
        this.plugin = plugin;
    }

    public void handleFirstDiscovery(Player player, String stampId, String stampName) {
        plugin.getDatabaseManager().registerServerFirst(stampId, player.getUniqueId(), player.getName()).thenAccept(success -> {
            if (success) {
                // Bylo to prvni objeveni teto znamky na serveru!
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String msg = plugin.getMessageManager().getRawMessage("first_discovery")
                            .replace("{player}", player.getName())
                            .replace("{stamp_name}", stampName);
                    Bukkit.broadcastMessage(msg);
                });
            }
        });
    }

    public void handleCompletion(Player player) {
        plugin.getDatabaseManager().registerMasterFirst(player.getUniqueId(), player.getName()).thenAccept(rank -> {
            if (rank > 0 && rank <= 3) {
                // Vykonat odmeny
                ConfigurationSection rewards = plugin.getConfig().getConfigurationSection("server_first_rewards." + rank);
                if (rewards != null) {
                    List<String> commands = rewards.getStringList("commands");
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (String cmd : commands) {
                            String parsedCmd = cmd.replace("%player%", player.getName());
                            if (parsedCmd.startsWith("broadcast ")) {
                                String msg = parsedCmd.substring(10);
                                Bukkit.broadcastMessage(cz.basicland.turistika.config.MessageManager.colorize(msg));
                            } else {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCmd);
                            }
                        }
                    });
                }
            }
        });
    }
}
