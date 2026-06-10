package cz.basicland.turistika.mechanics;

import cz.basicland.turistika.BasicLandTuristika;
import cz.basicland.turistika.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * RewardManager - centralni spravce vsech odmenovacich mechanik pluginu.
 *
 * Plugin podporuje 6 kategorii odmeny ktere admin muze nakonfigurovat v config.yml:
 *
 *  1. ITEMS       - /give prikaz (vanilla predmety)
 *  2. MONEY       - EssentialsX /eco give nebo Vault /pay prikaz
 *  3. RANKS       - LuckPerms /lp user %player% parent add <rank>
 *  4. COMMANDS    - Libovolny konzolovy prikaz s placeholderem %player%
 *  5. BROADCAST   - Zprava do celoseveroveho chatu
 *  6. TITLE       - /title prikaz (JSON title/subtitle)
 *
 * Vsechny prikazy jsou spousteny konzolou (bez opravneni hrace).
 * Spousteni probiha vzdy na hlavnim vlakne (main thread).
 */
public class RewardManager {

    private final BasicLandTuristika plugin;

    public RewardManager(BasicLandTuristika plugin) {
        this.plugin = plugin;
    }

    /**
     * Zpracuje a spusti seznam prikazu odmeny pro daneho hrace.
     * Prikazy jsou definovany v configu jako stringovy seznam.
     *
     * Specialni prikazy (nezasila se konzoli, ale resi se interně):
     *   "broadcast <zprava>"  → Bukkit.broadcastMessage()
     *   "title <title>|<subtitle>" → /title hrace pres Bukkit API
     *
     * @param player  Hrac, ktery dostava odmenu.
     * @param commands Seznam prikazu z config.yml.
     */
    public void executeRewards(Player player, List<String> commands) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String raw : commands) {
                String cmd = raw.replace("%player%", player.getName());

                if (cmd.startsWith("broadcast ")) {
                    Bukkit.broadcastMessage(MessageManager.colorize(cmd.substring(10)));

                } else if (cmd.startsWith("title ")) {
                    // Vlastni zkraceny format: title <title>|<subtitle>
                    // Napr: title &6Gratulujeme!|&7Dosahl jsi 10 znamek
                    String payload = cmd.substring(6);
                    String[] parts = payload.split("\\|", 2);
                    String title = parts.length > 0 ? parts[0] : "";
                    String subtitle = parts.length > 1 ? parts[1] : "";
                    player.sendTitle(
                            MessageManager.colorize(title),
                            MessageManager.colorize(subtitle),
                            10, 70, 20
                    );

                } else {
                    // Standardni konzolovy prikaz
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
            }
        });
    }

    /**
     * Pomocna metoda pro nacteni seznamu prikazu ze sekce configu.
     */
    public List<String> loadCommands(ConfigurationSection section, String path) {
        if (section == null) return new ArrayList<>();
        return section.getStringList(path + ".commands");
    }
}
