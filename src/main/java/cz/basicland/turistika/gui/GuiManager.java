package cz.basicland.turistika.gui;

import cz.basicland.turistika.BasicLandTuristika;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Listener spravujici vsechna interaktivni GUI pluginu.
 * Blokuje klikani i tazeni itemu v nasich GUI, aby hrac nerozbil menu.
 */
public class GuiManager implements Listener {

    private final BasicLandTuristika plugin;

    public GuiManager(BasicLandTuristika plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (event.getInventory().getHolder() instanceof Gui) {
            event.setCancelled(true);

            // Reagovat jen na kliknuti uvnitr naseho GUI (ne dolni inventory hrace)
            if (event.getClickedInventory() != null
                    && event.getClickedInventory().getHolder() instanceof Gui) {
                Gui gui = (Gui) event.getInventory().getHolder();
                gui.executeAction(event.getSlot(), player);
            }
        }
    }

    /**
     * Ochranna vrstva proti tazeni (drag) itemu do GUI.
     * Bez tohoto listeneru by hrac mohl "nalozit" item do naseho inventare.
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Gui) {
            event.setCancelled(true);
        }
    }
}
