package cz.basicland.turistika;

import cz.basicland.turistika.command.DenikCommand;
import cz.basicland.turistika.command.TuristaCommand;
import cz.basicland.turistika.config.ConfigManager;
import cz.basicland.turistika.config.MessageManager;
import cz.basicland.turistika.database.DatabaseManager;
import cz.basicland.turistika.gui.GuiManager;
import cz.basicland.turistika.mechanics.AirdropManager;
import cz.basicland.turistika.mechanics.HologramManager;
import cz.basicland.turistika.mechanics.LocationManager;
import cz.basicland.turistika.mechanics.MilestoneManager;
import cz.basicland.turistika.mechanics.ServerFirstManager;
import org.bukkit.plugin.java.JavaPlugin;

public class BasicLandTuristika extends JavaPlugin {

    private static BasicLandTuristika instance;

    private ConfigManager configManager;
    private MessageManager messageManager;
    private DatabaseManager databaseManager;
    private GuiManager guiManager;
    private MilestoneManager milestoneManager;
    private ServerFirstManager serverFirstManager;
    private AirdropManager airdropManager;
    private HologramManager hologramManager;
    private LocationManager locationManager;

    @Override
    public void onEnable() {
        instance = this;
        printBanner();

        // --- Konfigurace ---
        saveDefaultConfig();
        this.configManager = new ConfigManager(this);
        this.messageManager = new MessageManager(this);

        // --- Databaze ---
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.connect();
        this.databaseManager.createTables();

        // --- Managery ---
        this.guiManager = new GuiManager(this);
        this.milestoneManager = new MilestoneManager(this);
        this.serverFirstManager = new ServerFirstManager(this);
        this.airdropManager = new AirdropManager(this);
        this.hologramManager = new HologramManager(this);
        this.locationManager = new LocationManager(this);
        this.locationManager.loadLocations();

        // --- Prikazy ---
        TuristaCommand turistaCmd = new TuristaCommand(this);
        getCommand("turista").setExecutor(turistaCmd);
        getCommand("turista").setTabCompleter(turistaCmd);
        getCommand("denik").setExecutor(new DenikCommand(this));

        // --- Listenery ---
        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(airdropManager, this);

        // --- Spustit planovane tasky (s malym zpozdenim pro nacteni svetu) ---
        getServer().getScheduler().runTaskLater(this, () -> {
            airdropManager.startScheduler();
            hologramManager.loadFromDatabase();
            locationManager.startCheckTask();
        }, 60L); // 3s po startu

        getLogger().info("Plugin uspesne nacten a pripraven!");
    }

    @Override
    public void onDisable() {
        if (locationManager != null) locationManager.stopCheckTask();
        if (airdropManager != null) airdropManager.stopScheduler();
        if (hologramManager != null) hologramManager.shutdown();
        if (databaseManager != null) databaseManager.disconnect();
        getLogger().info("Plugin uspesne vypnut. Nashledanou!");
    }

    private void printBanner() {
        getLogger().info("§b");
        getLogger().info("§b  ██████╗ ███████╗███╗  ██╗██╗██╗  ██╗");
        getLogger().info("§b  ██╔══██╗██╔════╝████╗ ██║██║██║ ██╔╝");
        getLogger().info("§b  ██║  ██║█████╗  ██╔██╗██║██║█████╔╝ ");
        getLogger().info("§b  ██║  ██║██╔══╝  ██║╚████║██║██╔═██╗ ");
        getLogger().info("§b  ██████╔╝███████╗██║ ╚███║██║██║ ╚██╗");
        getLogger().info("§b  ╚═════╝ ╚══════╝╚═╝  ╚══╝╚═╝╚═╝  ╚═╝");
        getLogger().info("§b");
        getLogger().info("§b  BasicLand Turistika  v" + getDescription().getVersion());
        getLogger().info("§b  Paper 1.20+  |  SQLite  |  Zero Dependencies");
        getLogger().info("§b");
    }

    // ======================================================
    //  GETTERY
    // ======================================================

    public static BasicLandTuristika getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public MessageManager getMessageManager() { return messageManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public GuiManager getGuiManager() { return guiManager; }
    public MilestoneManager getMilestoneManager() { return milestoneManager; }
    public ServerFirstManager getServerFirstManager() { return serverFirstManager; }
    public AirdropManager getAirdropManager() { return airdropManager; }
    public HologramManager getHologramManager() { return hologramManager; }
    public LocationManager getLocationManager() { return locationManager; }
}
