package cz.basicland.turistika.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Sada statickych metod pro premium vizual zprav v chatu.
 * Vsechny "tezke" vizualni sestavy prikazu by mely pouzivat tuto tridu
 * misto rucniho sestavovani retezcu.
 */
public final class ChatUtil {

    // Konstanty pro vizualni styl
    private static final String LINE = "&8&m                                               ";
    private static final String PREFIX_ADMIN = "&8[&4A&8] &7";
    private static final String PREFIX_INFO  = "&8[&bℹ&8] &7";
    private static final String PREFIX_OK    = "&8[&a✔&8] &7";
    private static final String PREFIX_ERR   = "&8[&c✘&8] &7";

    private ChatUtil() {}

    public static String line() {
        return colorize(LINE);
    }

    public static String ok(String msg) {
        return colorize(PREFIX_OK + msg);
    }

    public static String error(String msg) {
        return colorize(PREFIX_ERR + msg);
    }

    public static String info(String msg) {
        return colorize(PREFIX_INFO + msg);
    }

    public static String admin(String msg) {
        return colorize(PREFIX_ADMIN + msg);
    }

    // Alias
    public static String colorize(String str) {
        return cz.basicland.turistika.config.MessageManager.colorize(str);
    }

    /**
     * Odesle hracovi/senderovi skupinu radku s oddelovaci.
     */
    public static void sendBox(CommandSender sender, String title, String... lines) {
        sender.sendMessage(colorize("\n" + LINE));
        sender.sendMessage(colorize("  &b&l" + title));
        sender.sendMessage(colorize(LINE));
        for (String line : lines) {
            sender.sendMessage(colorize("  " + line));
        }
        sender.sendMessage(colorize(LINE + "\n"));
    }

    /**
     * Odesle stylizovanou radku zebricku s poradim, jmenem a skore.
     */
    public static String leaderboardLine(int rank, String name, int score, String unit) {
        String rankStr;
        switch (rank) {
            case 1: rankStr = "&e&l#1 ✦"; break;
            case 2: rankStr = "&f&l#2 ✦"; break;
            case 3: rankStr = "&6&l#3 ✦"; break;
            default: rankStr = "&7#" + rank; break;
        }
        return colorize(rankStr + " &a" + name + " &8» &e" + score + " &7" + unit);
    }
}
