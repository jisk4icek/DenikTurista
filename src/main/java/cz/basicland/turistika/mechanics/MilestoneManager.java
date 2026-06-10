package cz.basicland.turistika.mechanics;

import cz.basicland.turistika.BasicLandTuristika;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.List;

public class MilestoneManager {

    private final BasicLandTuristika plugin;

    public MilestoneManager(BasicLandTuristika plugin) {
        this.plugin = plugin;
    }

    public void checkMilestones(Player player, int currentStamps) {
        ConfigurationSection milestones = plugin.getConfig().getConfigurationSection("milestones");
        if (milestones == null) return;

        String key = String.valueOf(currentStamps);
        if (milestones.contains(key)) {
            List<String> commands = milestones.getStringList(key + ".commands");
            
            // Execute commands on main thread
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
        
        // Check for server first complete
        int totalRequired = plugin.getConfig().getInt("total_stamps_for_completion", 0);
        if (totalRequired > 0 && currentStamps >= totalRequired) {
            plugin.getServerFirstManager().handleCompletion(player);
        }
    }
}
