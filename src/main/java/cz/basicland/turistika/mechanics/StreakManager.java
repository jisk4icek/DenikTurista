package cz.basicland.turistika.mechanics;

import cz.basicland.turistika.BasicLandTuristika;
import cz.basicland.turistika.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Streak systém – sleduje po sobě jdoucí dny, kdy hráč přidal alespoň 1 zna mku.
 *
 * Data se ukládají do SQLite (tabulka player_streaks).
 * Každý den odměna za streak je konfigurovatelná v config.yml.
 *
 * Logika:
 *  - Hráč sbírá zna mku → zavolá se recordActivity(player)
 *  - Systém zkontroluje, zda hráč byl aktivní VČERA
 *    → ANO: streak += 1, odměna dle config
 *    → NE a byl aktivní DNES: streak = stejný (nic se nemění)
 *    → NE a dnes poprvé: streak = 1 (reset)
 *
 * Cache se drží v paměti a asynchronně flushuje do DB.
 */
public class StreakManager {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final BasicLandTuristika plugin;
    // Cache: uuid -> [lastDate, streak]
    private final Map<UUID, StreakEntry> cache = new ConcurrentHashMap<>();

    public StreakManager(BasicLandTuristika plugin) {
        this.plugin = plugin;
    }

    /**
     * Zavolej po každém úspěšném získání zna mky.
     */
    public void recordActivity(Player player) {
        UUID uuid = player.getUniqueId();
        String today = LocalDate.now().format(DATE_FMT);
        String yesterday = LocalDate.now().minusDays(1).format(DATE_FMT);

        plugin.getDatabaseManager().getStreakData(uuid).thenAccept(entry -> {
            int newStreak;

            if (entry == null) {
                // Vůbec první zna mka
                newStreak = 1;
            } else if (today.equals(entry.lastDate)) {
                // Dnes už byl aktivní – streak se nemění
                return;
            } else if (yesterday.equals(entry.lastDate)) {
                // Byl aktivní včera – pokračuje streak
                newStreak = entry.streak + 1;
            } else {
                // Přerušení – reset na 1
                newStreak = 1;
                if (entry.streak >= 3) {
                    // Upozorni hráče na break streaku (pokud je online)
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            player.sendMessage(plugin.getMessageManager().getMessage("streak_broken")
                                    .replace("{streak}", String.valueOf(entry.streak)));
                        }
                    });
                }
            }

            final int finalStreak = newStreak;
            plugin.getDatabaseManager().saveStreakData(uuid, today, finalStreak).thenRun(() -> {
                cache.put(uuid, new StreakEntry(today, finalStreak));

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    // Zpráva o streaku
                    player.sendMessage(plugin.getMessageManager().getMessage("streak_update")
                            .replace("{streak}", String.valueOf(finalStreak)));

                    // Zvuk pro streak
                    if (finalStreak >= 3) {
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                    }

                    // Zkontroluj odměny za streak milníky
                    checkStreakReward(player, finalStreak);
                });
            });
        });
    }

    private void checkStreakReward(Player player, int streak) {
        ConfigurationSection streakRewards = plugin.getConfig().getConfigurationSection("streak_rewards");
        if (streakRewards == null) return;

        String key = String.valueOf(streak);
        if (streakRewards.contains(key)) {
            List<String> cmds = streakRewards.getStringList(key + ".commands");
            plugin.getRewardManager().executeRewards(player, cmds);
        }
    }

    // Datová třída pro cache
    public static class StreakEntry {
        public final String lastDate;
        public final int streak;
        public StreakEntry(String lastDate, int streak) {
            this.lastDate = lastDate;
            this.streak = streak;
        }
    }
}
