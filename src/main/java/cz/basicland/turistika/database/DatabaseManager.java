package cz.basicland.turistika.database;

import cz.basicland.turistika.BasicLandTuristika;
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

                // Tabulka pro TextDisplay hologramy (Faze 4)
                statement.execute("CREATE TABLE IF NOT EXISTS holograms (" +
                        "id VARCHAR(64) NOT NULL PRIMARY KEY, " +
                        "world VARCHAR(64), " +
                        "x DOUBLE, y DOUBLE, z DOUBLE, " +
                        "entity_uuid VARCHAR(36)" +
                        ");");

                plugin.getLogger().info("SQLite tabulky jsou pripraveny.");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
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

    public CompletableFuture<Void> addStamp(UUID uuid, String stampId) {
        return CompletableFuture.runAsync(() -> {
            String query = "INSERT OR IGNORE INTO unlocked_stamps (uuid, stamp_id) VALUES (?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, stampId);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }, dbExecutor);
    }

    // ======================================================
    //  TOP LEADERBOARD
    // ======================================================

    /**
     * Vraci seznam hracu serazeny podle poctu odemcenych znamek (TOP N).
     * @param limit maximalni pocet zaznamu
     * @return usporadany seznam dvojic: [player_name, count]
     */
    public CompletableFuture<List<Map.Entry<String, Integer>>> getTopPlayers(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Map.Entry<String, Integer>> top = new ArrayList<>();
            String query = "SELECT uuid, COUNT(*) as cnt FROM unlocked_stamps GROUP BY uuid ORDER BY cnt DESC LIMIT ?";
            try (PreparedStatement ps = connection.prepareStatement(query)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String uuidStr = rs.getString("uuid");
                        int count = rs.getInt("cnt");
                        // Pokus ziskat jmeno z Bukkit cache (muze byt offline)
                        String name = uuidStr;
                        try {
                            org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr));
                            if (op.getName() != null) name = op.getName();
                        } catch (Exception ignored) {}
                        top.add(new AbstractMap.SimpleEntry<>(name, count));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return top;
        }, dbExecutor);
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
    //  DATA CLASSES
    // ======================================================

    public static class HologramData {
        public final String id;
        public final String world;
        public final double x, y, z;
        public final UUID entityUUID;

        public HologramData(String id, String world, double x, double y, double z, UUID entityUUID) {
            this.id = id; this.world = world;
            this.x = x; this.y = y; this.z = z;
            this.entityUUID = entityUUID;
        }
    }
}
