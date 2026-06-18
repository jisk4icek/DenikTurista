package cz.basicland.turistika.utils;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder setName(String name) {
        if (meta != null) {
            meta.setDisplayName(cz.basicland.turistika.config.MessageManager.colorize(name));
        }
        return this;
    }

    public ItemBuilder setLore(List<String> lore) {
        if (meta != null) {
            List<String> coloredLore = new ArrayList<>();
            for (String s : lore) {
                coloredLore.add(cz.basicland.turistika.config.MessageManager.colorize(s));
            }
            meta.setLore(coloredLore);
        }
        return this;
    }

    public ItemBuilder setLore(String... lore) {
        return setLore(Arrays.asList(lore));
    }
    
    public ItemBuilder replaceLore(String target, String replacement) {
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            for (int i = 0; i < lore.size(); i++) {
                lore.set(i, lore.get(i).replace(target, replacement));
            }
            meta.setLore(lore);
        }
        return this;
    }

    public ItemBuilder setCustomModelData(int data) {
        if (meta != null && data > 0) {
            meta.setCustomModelData(data);
        }
        return this;
    }

    /** Prida enchant glint efekt (vizualni zarive na nalezenych znamkach) */
    public ItemBuilder setGlint(boolean glint) {
        if (meta != null && glint) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }
}
