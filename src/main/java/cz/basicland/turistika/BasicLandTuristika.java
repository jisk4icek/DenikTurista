package cz.basicland.turistika;

import cz.basicland.turistika.command.DenikCommand;
import cz.basicland.turistika.command.TuristaCommand;
import cz.basicland.turistika.config.ConfigManager;
import cz.basicland.turistika.config.MessageManager;
import cz.basicland.turistika.database.DatabaseManager;
import cz.basicland.turistika.gui.GuiManager;
import cz.basicland.turistika.mechanics.*;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * BasicLandTuristika v2.2
 * + ArmorStand Turistický Průvodce (plná výbava + poza)
 * + Fyzická Nástěnka BoardManager (TextDisplay FIXED)
 * + WorldBorder airdrop check, headspace surface detection
 */
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
    private RewardManager rewardManager;
    private StreakManager streakManager;
    private NpcManager npcManager;
    private MarkerManager markerManager;
    private BoardManager boardManager;

    @Override
    public void onEnable() {
        instance = this;
        printBanner();

        saveDefaultConfig();
        this.configManager  = new ConfigManager(this);
        this.messageManager = new MessageManager(this);

        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.connect();
        this.databaseManager.createTables();
        this.databaseManager.createStreakTable();
        this.databaseManager.createNpcTable();
        this.databaseManager.createMarkerTable();
        this.databaseManager.createBoardTable();

        this.rewardManager      = new RewardManager(this);
        this.guiManager         = new GuiManager(this);
        this.milestoneManager   = new MilestoneManager(this);
        this.serverFirstManager = new ServerFirstManager(this);
        this.streakManager      = new StreakManager(this);
        this.airdropManager     = new AirdropManager(this);
        this.hologramManager    = new HologramManager(this);
        this.locationManager    = new LocationManager(this);
        this.locationManager.loadLocations();
        this.npcManager         = new NpcManager(this);
        this.markerManager      = new MarkerManager(this);
        this.boardManager       = new BoardManager(this);

        TuristaCommand turistaCmd = new TuristaCommand(this);
        getCommand("turista").setExecutor(turistaCmd);
        getCommand("turista").setTabCompleter(turistaCmd);
        getCommand("denik").setExecutor(new DenikCommand(this));

        getServer().getPluginManager().registerEvents(guiManager,   this);
        getServer().getPluginManager().registerEvents(airdropManager, this);
        getServer().getPluginManager().registerEvents(npcManager,   this);
        getServer().getPluginManager().registerEvents(markerManager, this);

        getServer().getScheduler().runTaskLater(this, () -> {
            airdropManager.startScheduler();
            hologramManager.loadFromDatabase();
            locationManager.startCheckTask();
            npcManager.loadFromDatabase();
            markerManager.loadFromDatabase();
            boardManager.loadFromDatabase();
            boardManager.startRefreshTask();
        }, 60L);

        getLogger().info("v2.2 uspesne nacten!");
    }

    @Override
    public void onDisable() {
        if (locationManager != null) locationManager.stopCheckTask();
        if (airdropManager  != null) airdropManager.stopScheduler();
        if (hologramManager != null) hologramManager.shutdown();
        if (npcManager      != null) npcManager.shutdown();
        if (markerManager   != null) markerManager.shutdown();
        if (boardManager    != null) { boardManager.stopRefreshTask(); boardManager.shutdown(); }
        if (databaseManager != null) databaseManager.disconnect();
        getLogger().info("Plugin ukoncen. Nashledanou!");
    }

    private void printBanner() {
        getLogger().info(" ");
        getLogger().info("  ██████╗ ███████╗███╗  ██╗██╗██╗  ██╗  v2.2");
        getLogger().info("  ██╔══██╗██╔════╝████╗ ██║██║██║ ██╔╝   Explorer ArmorStand");
        getLogger().info("  ██║  ██║█████╗  ██╔██╗██║██║█████╔╝    Physical Board");
        getLogger().info("  ██║  ██║██╔══╝  ██║╚████║██║██╔═██╗    WorldBorder Airdrop");
        getLogger().info("  ██████╔╝███████╗██║ ╚███║██║██║ ╚██╗");
        getLogger().info("  ╚═════╝ ╚══════╝╚═╝  ╚══╝╚═╝╚═╝  ╚═╝");
        getLogger().info(" ");
    }

    public static BasicLandTuristika getInstance()    { return instance; }
    public ConfigManager getConfigManager()           { return configManager; }
    public MessageManager getMessageManager()         { return messageManager; }
    public DatabaseManager getDatabaseManager()       { return databaseManager; }
    public GuiManager getGuiManager()                 { return guiManager; }
    public MilestoneManager getMilestoneManager()     { return milestoneManager; }
    public ServerFirstManager getServerFirstManager() { return serverFirstManager; }
    public AirdropManager getAirdropManager()         { return airdropManager; }
    public HologramManager getHologramManager()       { return hologramManager; }
    public LocationManager getLocationManager()       { return locationManager; }
    public RewardManager getRewardManager()           { return rewardManager; }
    public StreakManager getStreakManager()            { return streakManager; }
    public NpcManager getNpcManager()                 { return npcManager; }
    public MarkerManager getMarkerManager()           { return markerManager; }
    public BoardManager getBoardManager()             { return boardManager; }
}
