package cz.basicland.turistika.gui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public abstract class Gui implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, Consumer<Player>> actions = new HashMap<>();

    public Gui(int size, String title) {
        this.inventory = Bukkit.createInventory(this, size, cz.basicland.turistika.config.MessageManager.colorize(title));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setAction(int slot, Consumer<Player> action) {
        actions.put(slot, action);
    }

    public void executeAction(int slot, Player player) {
        Consumer<Player> action = actions.get(slot);
        if (action != null) {
            action.accept(player);
        }
    }
    
    public abstract void open(Player player);
}
