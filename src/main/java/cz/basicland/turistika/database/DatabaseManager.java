package cz.basicland.turistika.database;

import cz.basicland.turistika.BasicLandTuristika;
import cz.basicland.turistika.mechanics.StreakManager;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Spravuje SQLite databazi pluginu.
 * Veskeré operace s databazi jsou provadeny v jednovlaknovem executoru (dbExecutor)
 * pro zajisteni thread-safety SQLite, ktera nepodporuje paralelni zapisy.
 */
public class DatabaseManager {

    private final BasicLandTuristika plugin;
    private Connection connection;
    // Jednovlaknovy executor - jedina cesta jak bezpecne praci se SQLite
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BasicLandTuristika-DB");
        t.setDaemon(true);
        return t;
    });

    public DatabaseManager(BasicLandTuristika plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdir();
            }
            File dbFile = new File(dataFolder, "database.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);
            // Povoli WAL rezim pro lepsi vikon SQLite
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL;");
            }
            plugin.getLogger().info("Pripojeno k SQLite databazi.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Nelze se pripojit k SQLite databazi!");
            e.printStackTrace();
        }
    }

    public void disconnect() {
        dbExecutor.submit(() -> {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                    plugin.getLogger().info("SQLite databaze uzavrena.");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
        dbExecutor.shutdown();
    }

    public void createTables() {
        dbExecutor.submit(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS unlocked_stamps (" +
                        "uuid VARCHAR(36) NOT NULL, " +
                        "stamp_id VARCHAR(64) NOT NULL, " +
                        "points INTEGER NOT NULL DEFAULT 1, " +
                        "obtained_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                        "PRIMARY KEY (uuid, stamp_id)" +
                        ");");

                statement.execute("CREATE TABLE IF NOT EXISTS server_firsts (" +
                        "stamp_id VARCHAR(64) NOT NULL PRIMARY KEY, " +
                        "uuid VARCHAR(36) NOT NULL, " +
                        "player_name VARCHAR(16) NOT NULL" +
                        ");");

                statement.execute("CREATE TABLE IF NOT EXISTS master_firsts (" +
                        "rank INT NOT NULL PRIMARY KEY, " +
                        "uuid VARCHAR(36) NOT NULL, " +
                        "player_name VARCHAR(16) NOT NULL" +
                        ");");

                statement.execute("CREATE TABLE IF NOT EXISTS holograms (" +
                        "id VARCHAR(64) NOT NULL PRIMARY KEY, " +
                        "world VARCHAR(64), " +
                        "x DOUBLE, y DOUBLE, z DOUBLE, " +
                        "entity_uuid VARCHAR(36)" +
                        ");");

                plugin.getLogger().info("SQLite tabulky jsou připraveny.");
            } catch (SQLException e) {
                e.printStackTrace();
            }

            // v2.4 – migrace: přidej sloupce pokud neexistují (ALTER TABLE ignoruje pokud sloupec je)
            migrateAddColumnIfAbsent("unlocked_stamps", "points",   "INTEGER NOT NULL DEFAULT 1");
            migrateAddColumnIfAbsent("unlocked_stamps", "obtained_at", "DATETIME DEFAULT CURRENT_TIMESTAMP");
        });
    }

    /** Bezpečně přidá sloupec pokud neexistuje (SQLite nemá IF NOT EXISTS pro ALTER TABLE). */
    private void migrateAddColumnIfAbsent(String table, String column, String definition) {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (rs.getString("name").equalsIgnoreCase(column)) return; // Sloupec už existuje
            }
        } catch (SQLException ignored) {}
        try (Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            plugin.getLogger().info("[DB Migrace] Přidán sloupec '" + column + "' do tabulky '" + table + "'.");
        } catch (SQLException e) {
            plugin.getLogger().warning("[DB Migrace] Nelze přidat '" + column + "': " + e.getMessage());
        }
    }

    // ======================================================
    //  STAMPS
    // ======================================================

    public CompletableFuture<Boolean> hasStamp(UUID uuid, String stampId) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT 1 FROM unlocked_stamps WHERE uuid = ? AND stamp_id = ?";
            try (PreparedStatement ps = connection.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, stampId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }, dbExecutor);
    }

    public CompletableFuture<Set<String>> getUnlockedStamps(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Set<String> stamps = new LinkedHashSet<>();
            String query = "SELECT stamp_id FROM unlocked_stamps WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        stamps.add(rs.getString("stamp_id"));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return stamps;
        }, dbExecutor);
    }

    /**
     * Přidá známku hráči s bodovým ohodnocením.
     * Starší záznamy bez bodů mají defaultně 1 bod (migrace).
     *
     * @param uuid    UUID hráče
     * @param stampId ID známky
     * @param points  Body za tuto známku (dle rarity)
     */
    public CompletableFuture<Void> addStamp(UUID uuid, String stampId, int points) {
        return CompletableFuture.runAsync(() -> {
            String query = "INSERT OR IGNORE INTO unlocked_stamps (uuid, stamp_id, points) VALUES (?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, stampId);
                ps.setInt(3, points);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    /** @deprecated Použij addStamp(uuid, stampId, points) – zachováno pro zpětnou kompatibilitu */
    @Deprecated
    public CompletableFuture<Void> addStamp(UUID uuid, String stampId) {
        return addStamp(uuid, stampId, 1);
    }

    // ======================================================
    //  TOP LEADERBOARD
    // ======================================================

    /**
     * TOP N hráčů seřazených podle počtu sesbíraných známek.
     */
    public CompletableFuture<List<Map.Entry<String, Integer>>> getTopPlayers(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Map.Entry<String, Integer>> top = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid, COUNT(*) as cnt FROM unlocked_stamps GROUP BY uuid ORDER BY cnt DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String uuidStr = rs.getString("uuid");
                        String name = resolvePlayerName(uuidStr);
                        top.add(new AbstractMap.SimpleEntry<>(name, rs.getInt("cnt")));
                    }
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return top;
        }, dbExecutor);
    }

    /**
     * v2.4: TOP N hráčů seřazených podle celkových BODŮ (SUM(points)).
     * Body se liší dle rarity: COMMON=1, RARE=3, EPIC=5, LEGENDARY=10.
     */
    public CompletableFuture<List<Map.Entry<String, Integer>>> getTopPlayersByPoints(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Map.Entry<String, Integer>> top = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid, SUM(points) as total FROM unlocked_stamps GROUP BY uuid ORDER BY total DESC LIMIT ?")) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String uuidStr = rs.getString("uuid");
                        String name = resolvePlayerName(uuidStr);
                        top.add(new AbstractMap.SimpleEntry<>(name, rs.getInt("total")));
                    }
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return top;
        }, dbExecutor);
    }

    /**
     * v2.4: Celkový počet bodů konkrétního hráče.
     */
    public CompletableFuture<Integer> getPlayerPoints(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COALESCE(SUM(points), 0) as total FROM unlocked_stamps WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt("total");
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return 0;
        }, dbExecutor);
    }

    /** Helper: převede UUID string na jméno hráče (online/offline cache). */
    private String resolvePlayerName(String uuidStr) {
        try {
            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr));
            if (op.getName() != null) return op.getName();
        } catch (Exception ignored) {}
        return uuidStr.substring(0, 8) + "...";
    }

    // ======================================================
    //  SERVER FIRSTS
    // ======================================================

    public CompletableFuture<Boolean> registerServerFirst(String stampId, UUID uuid, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "INSERT INTO server_firsts (stamp_id, uuid, player_name) VALUES (?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(query)) {
                ps.setString(1, stampId);
                ps.setString(2, uuid.toString());
                ps.setString(3, playerName);
                ps.executeUpdate();
                return true;
            } catch (SQLException e) {
                // Porusi PRIMARY KEY = uz evidovan
                return false;
            }
        }, dbExecutor);
    }

    public CompletableFuture<Integer> registerMasterFirst(UUID uuid, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            // Zkontrolovat, zda hrac jiz neni v tabulce
            try (PreparedStatement check = connection.prepareStatement("SELECT 1 FROM master_firsts WHERE uuid = ?")) {
                check.setString(1, uuid.toString());
                try (ResultSet rs = check.executeQuery()) {
                    if (rs.next()) return -1;
                }
            } catch (SQLException e) { e.printStackTrace(); return -1; }

            // Zjistit soucasne poradi
            int rank;
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM master_firsts")) {
                rank = rs.next() ? rs.getInt(1) + 1 : 1;
            } catch (SQLException e) { e.printStackTrace(); return -1; }

            // Ulozit jen top 3
            if (rank > 3) return -1;

            String query = "INSERT INTO master_firsts (rank, uuid, player_name) VALUES (?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(query)) {
                ps.setInt(1, rank);
                ps.setString(2, uuid.toString());
                ps.setString(3, playerName);
                ps.executeUpdate();
                return rank;
            } catch (SQLException e) {
                e.printStackTrace();
                return -1;
            }
        }, dbExecutor);
    }

    // ======================================================
    //  HOLOGRAMS
    // ======================================================

    public CompletableFuture<Void> saveHologram(String id, String world, double x, double y, double z, UUID entityUUID) {
        return CompletableFuture.runAsync(() -> {
            String query = "INSERT OR REPLACE INTO holograms (id, world, x, y, z, entity_uuid) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(query)) {
                ps.setString(1, id);
                ps.setString(2, world);
                ps.setDouble(3, x);
                ps.setDouble(4, y);
                ps.setDouble(5, z);
                ps.setString(6, entityUUID != null ? entityUUID.toString() : null);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    public CompletableFuture<List<HologramData>> loadHolograms() {
        return CompletableFuture.supplyAsync(() -> {
            List<HologramData> list = new ArrayList<>();
            String query = "SELECT id, world, x, y, z, entity_uuid FROM holograms";
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery(query)) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String world = rs.getString("world");
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    String euuid = rs.getString("entity_uuid");
                    UUID entityUUID = euuid != null ? UUID.fromString(euuid) : null;
                    list.add(new HologramData(id, world, x, y, z, entityUUID));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return list;
        }, dbExecutor);
    }

    public CompletableFuture<Void> deleteHologram(String id) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM holograms WHERE id = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    public CompletableFuture<Void> updateHologramEntityUUID(String id, UUID entityUUID) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement("UPDATE holograms SET entity_uuid = ? WHERE id = ?")) {
                ps.setString(1, entityUUID != null ? entityUUID.toString() : null);
                ps.setString(2, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    // ======================================================
    //  STREAK SYSTÉM
    // ======================================================

    public void createStreakTable() {
        dbExecutor.submit(() -> {
            try (Statement st = connection.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS player_streaks (" +
                        "uuid VARCHAR(36) NOT NULL PRIMARY KEY, " +
                        "last_date VARCHAR(10) NOT NULL, " +
                        "streak INT NOT NULL DEFAULT 1" +
                        ");");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    public CompletableFuture<StreakManager.StreakEntry> getStreakData(java.util.UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT last_date, streak FROM player_streaks WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new StreakManager.StreakEntry(rs.getString("last_date"), rs.getInt("streak"));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }, dbExecutor);
    }

    public CompletableFuture<Void> saveStreakData(java.util.UUID uuid, String date, int streak) {
        return CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO player_streaks (uuid, last_date, streak) VALUES (?, ?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, date);
                ps.setInt(3, streak);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    // ======================================================
    //  NPC SYSTÉM (v2.0)
    // ======================================================

    public void createNpcTable() {
        dbExecutor.submit(() -> {
            try (Statement st = connection.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS npc_locations (" +
                        "stamp_id VARCHAR(64) NOT NULL PRIMARY KEY, " +
                        "world VARCHAR(64) NOT NULL, " +
                        "x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, " +
                        "entity_uuid VARCHAR(36)" +
                        ");");
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public void saveNpc(String stampId, org.bukkit.Location loc, UUID entityUUID) {
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO npc_locations (stamp_id,world,x,y,z,entity_uuid) VALUES(?,?,?,?,?,?)")) {
                ps.setString(1, stampId);
                ps.setString(2, loc.getWorld().getName());
                ps.setDouble(3, loc.getX()); ps.setDouble(4, loc.getY()); ps.setDouble(5, loc.getZ());
                ps.setString(6, entityUUID != null ? entityUUID.toString() : null);
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }, dbExecutor);
    }

    public CompletableFuture<List<NpcData>> loadNpcs() {
        return CompletableFuture.supplyAsync(() -> {
            List<NpcData> list = new ArrayList<>();
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM npc_locations")) {
                while (rs.next()) {
                    String euuid = rs.getString("entity_uuid");
                    list.add(new NpcData(
                            rs.getString("stamp_id"), rs.getString("world"),
                            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            euuid != null ? UUID.fromString(euuid) : null));
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return list;
        }, dbExecutor);
    }

    public void deleteNpc(String stampId) {
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM npc_locations WHERE stamp_id = ?")) {
                ps.setString(1, stampId); ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }, dbExecutor);
    }

    // ======================================================
    //  ARMORSTAND MARKERY (v2.1)
    // ======================================================

    public void createMarkerTable() {
        dbExecutor.submit(() -> {
            try (Statement st = connection.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS marker_locations (" +
                        "stamp_id VARCHAR(64) NOT NULL PRIMARY KEY, " +
                        "world VARCHAR(64) NOT NULL, " +
                        "x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, " +
                        "entity_uuid VARCHAR(36)" +
                        ");");
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public void saveMarker(String stampId, org.bukkit.Location loc, UUID entityUUID) {
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO marker_locations (stamp_id,world,x,y,z,entity_uuid) VALUES(?,?,?,?,?,?)")) {
                ps.setString(1, stampId);
                ps.setString(2, loc.getWorld().getName());
                ps.setDouble(3, loc.getX()); ps.setDouble(4, loc.getY()); ps.setDouble(5, loc.getZ());
                ps.setString(6, entityUUID != null ? entityUUID.toString() : null);
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }, dbExecutor);
    }

    public CompletableFuture<List<MarkerData>> loadMarkers() {
        return CompletableFuture.supplyAsync(() -> {
            List<MarkerData> list = new ArrayList<>();
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM marker_locations")) {
                while (rs.next()) {
                    String euuid = rs.getString("entity_uuid");
                    list.add(new MarkerData(
                            rs.getString("stamp_id"), rs.getString("world"),
                            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            euuid != null ? UUID.fromString(euuid) : null));
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return list;
        }, dbExecutor);
    }

    public void deleteMarker(String stampId) {
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM marker_locations WHERE stamp_id = ?")) {
                ps.setString(1, stampId); ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }, dbExecutor);
    }

    // ======================================================
    //  DATA CLASSES
    // ======================================================

    public static class HologramData {
        public final String id, world;
        public final double x, y, z;
        public final UUID entityUUID;
        public HologramData(String id, String world, double x, double y, double z, UUID entityUUID) {
            this.id = id; this.world = world;
            this.x = x; this.y = y; this.z = z; this.entityUUID = entityUUID;
        }
    }

    public static class NpcData {
        public final String stampId, world;
        public final double x, y, z;
        public final UUID entityUUID;
        public NpcData(String stampId, String world, double x, double y, double z, UUID entityUUID) {
            this.stampId = stampId; this.world = world;
            this.x = x; this.y = y; this.z = z; this.entityUUID = entityUUID;
        }
    }

    public static class MarkerData {
        public final String stampId, world;
        public final double x, y, z;
        public final UUID entityUUID;
        public MarkerData(String stampId, String world, double x, double y, double z, UUID entityUUID) {
            this.stampId = stampId; this.world = world;
            this.x = x; this.y = y; this.z = z; this.entityUUID = entityUUID;
        }
    }

    // ======================================================
    //  TURISTICKÉ NÁSTĚNKY – BoardManager (v1.0)
    // ======================================================

    public void createBoardTable() {
        dbExecutor.submit(() -> {
            try (Statement st = connection.createStatement()) {
                st.execute("CREATE TABLE IF NOT EXISTS board_locations (" +
                        "board_id VARCHAR(64) NOT NULL PRIMARY KEY, " +
                        "world VARCHAR(64) NOT NULL, " +
                        "x DOUBLE NOT NULL, y DOUBLE NOT NULL, z DOUBLE NOT NULL, " +
                        "yaw FLOAT NOT NULL DEFAULT 0, " +
                        "entity_uuid VARCHAR(36)" +
                        ");");
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public void saveBoard(String boardId, org.bukkit.Location loc, UUID entityUUID) {
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO board_locations (board_id,world,x,y,z,yaw,entity_uuid) VALUES(?,?,?,?,?,?,?)")) {
                ps.setString(1, boardId);
                ps.setString(2, loc.getWorld().getName());
                ps.setDouble(3, loc.getX()); ps.setDouble(4, loc.getY()); ps.setDouble(5, loc.getZ());
                ps.setFloat(6,  loc.getYaw());
                ps.setString(7, entityUUID != null ? entityUUID.toString() : null);
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }, dbExecutor);
    }

    public CompletableFuture<List<BoardData>> loadBoards() {
        return CompletableFuture.supplyAsync(() -> {
            List<BoardData> list = new ArrayList<>();
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM board_locations")) {
                while (rs.next()) {
                    String euuid = rs.getString("entity_uuid");
                    list.add(new BoardData(
                            rs.getString("board_id"), rs.getString("world"),
                            rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            euuid != null ? UUID.fromString(euuid) : null));
                }
            } catch (SQLException e) { e.printStackTrace(); }
            return list;
        }, dbExecutor);
    }

    public void deleteBoard(String boardId) {
        CompletableFuture.runAsync(() -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM board_locations WHERE board_id = ?")) {
                ps.setString(1, boardId); ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        }, dbExecutor);
    }

    // ======================================================
    //  DATA CLASSES – BOARD
    // ======================================================

    public static class BoardData {
        public final String id, world;
        public final double x, y, z;
        public final float yaw;
        public final UUID entityUUID;
        public BoardData(String id, String world, double x, double y, double z, float yaw, UUID entityUUID) {
            this.id = id; this.world = world;
            this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.entityUUID = entityUUID;
        }
    }
}
