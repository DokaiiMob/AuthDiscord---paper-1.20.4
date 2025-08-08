package ru.example.authdiscord.database;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.*;
import java.util.UUID;

public class DatabaseManager {

    private final Plugin plugin;
    private Connection connection;
    private final String databasePath;

    public DatabaseManager(Plugin plugin) {
        this.plugin = plugin;
        File dataFolder = new File(plugin.getDataFolder(), "data.db");
        this.databasePath = dataFolder.getAbsolutePath();
    }

    public void initialize() throws SQLException {
        // Создание папки плагина если не существует
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Подключение к базе данных
        connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);

        // Создание таблицы если не существует
        createTables();
    }

    private void createTables() throws SQLException {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS links (
                uuid TEXT PRIMARY KEY,
                discord_id TEXT NOT NULL,
                discord_tag TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
        }
    }

    public void saveLink(UUID uuid, String discordId, String discordTag) throws SQLException {
        String sql = "INSERT OR REPLACE INTO links (uuid, discord_id, discord_tag) VALUES (?, ?, ?)";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, discordId);
            stmt.setString(3, discordTag);
            stmt.executeUpdate();
        }
    }

    public PlayerLink getLink(UUID uuid) throws SQLException {
        String sql = "SELECT discord_id, discord_tag FROM links WHERE uuid = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new PlayerLink(
                            uuid,
                            rs.getString("discord_id"),
                            rs.getString("discord_tag")
                    );
                }
            }
        }

        return null;
    }

    public boolean removeLink(UUID uuid) throws SQLException {
        String sql = "DELETE FROM links WHERE uuid = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            return stmt.executeUpdate() > 0;
        }
    }

    public PlayerLink getLinkByDiscordId(String discordId) throws SQLException {
        String sql = "SELECT uuid, discord_tag FROM links WHERE discord_id = ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, discordId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new PlayerLink(
                            UUID.fromString(rs.getString("uuid")),
                            discordId,
                            rs.getString("discord_tag")
                    );
                }
            }
        }

        return null;
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("Ошибка закрытия базы данных: " + e.getMessage());
            }
        }
    }

    public static class PlayerLink {
        private final UUID uuid;
        private final String discordId;
        private final String discordTag;

        public PlayerLink(UUID uuid, String discordId, String discordTag) {
            this.uuid = uuid;
            this.discordId = discordId;
            this.discordTag = discordTag;
        }

        public UUID getUuid() {
            return uuid;
        }

        public String getDiscordId() {
            return discordId;
        }

        public String getDiscordTag() {
            return discordTag;
        }
    }
}
