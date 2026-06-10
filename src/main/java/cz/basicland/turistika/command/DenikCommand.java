package cz.basicland.turistika.command;

import cz.basicland.turistika.BasicLandTuristika;
import cz.basicland.turistika.gui.DenikGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DenikCommand implements CommandExecutor {

    private final BasicLandTuristika plugin;

    public DenikCommand(BasicLandTuristika plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().getMessage("player_only"));
            return true;
        }

        Player player = (Player) sender;
        
        // Nacteni odemcenych znamek asynchronne, a pak otevreni GUI synchronne
        plugin.getDatabaseManager().getUnlockedStamps(player.getUniqueId()).thenAccept(unlocked -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                DenikGUI gui = new DenikGUI(plugin, player, unlocked, 0);
                gui.open(player);
            });
        });

        return true;
    }
}
