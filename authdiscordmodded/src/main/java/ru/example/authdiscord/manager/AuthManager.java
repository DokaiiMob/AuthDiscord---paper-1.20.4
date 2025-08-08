package ru.example.authdiscord.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import ru.example.authdiscord.database.DatabaseManager;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager {

    private final Plugin plugin;
    private final DatabaseManager databaseManager;

    // Игроки, требующие авторизации
    private final Set<UUID> pendingAuth = ConcurrentHashMap.newKeySet();

    // Коды авторизации (код -> UUID игрока)
    private final Map<String, UUID> authCodes = new ConcurrentHashMap<>();

    // Задачи для очистки кодов
    private final Map<String, BukkitTask> codeCleanupTasks = new ConcurrentHashMap<>();

    public AuthManager(Plugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public boolean isPlayerAuthenticated(UUID uuid) {
        try {
            return databaseManager.getLink(uuid) != null;
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка проверки авторизации игрока: " + e.getMessage());
            return false;
        }
    }

    public boolean requiresAuth(UUID uuid) {
        return pendingAuth.contains(uuid);
    }

    public String generateAuthCode(UUID playerUuid) {
        String charset = plugin.getConfig().getString("code_charset", "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789");
        int length = plugin.getConfig().getInt("code_length", 6);

        String code;
        do {
            code = generateRandomCode(charset, length);
        } while (authCodes.containsKey(code));

        authCodes.put(code, playerUuid);
        pendingAuth.add(playerUuid);

        // Установка задачи для очистки кода
        final String finalCode = code; // Делаем переменную final для использования в анонимном классе
        int timeoutMinutes = plugin.getConfig().getInt("code_timeout", 5);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                removeAuthCode(finalCode);
            }
        }.runTaskLater(plugin, timeoutMinutes * 60 * 20L); // 20 тиков = 1 секунда

        codeCleanupTasks.put(code, task);

        return code;
    }

    private String generateRandomCode(String charset, int length) {
        StringBuilder code = new StringBuilder();
        Random random = new Random();

        for (int i = 0; i < length; i++) {
            code.append(charset.charAt(random.nextInt(charset.length())));
        }

        return code.toString();
    }

    public UUID getPlayerByCode(String code) {
        return authCodes.get(code);
    }

    public void removeAuthCode(String code) {
        UUID playerUuid = authCodes.remove(code);
        if (playerUuid != null) {
            pendingAuth.remove(playerUuid);

            // Отмена задачи очистки
            BukkitTask task = codeCleanupTasks.remove(code);
            if (task != null) {
                task.cancel();
            }
        }
    }

    public void completeAuth(UUID playerUuid, String discordId, String discordTag) {
        try {
            // Сохранение в базу данных
            databaseManager.saveLink(playerUuid, discordId, discordTag);

            // Удаление из ожидающих авторизации
            pendingAuth.remove(playerUuid);

            // Удаление кода авторизации
            authCodes.entrySet().removeIf(entry -> entry.getValue().equals(playerUuid));

            // Установка тега в scoreboard
            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                setPlayerTag(player, discordTag);

                String message = plugin.getConfig().getString("messages.auth_success", "§aВы успешно авторизованы как §b%tag%");
                player.sendMessage(message.replace("%tag%", discordTag));
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка завершения авторизации: " + e.getMessage());
        }
    }

    public void setPlayerTag(Player player, String discordTag) {
        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard == null) {
            scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        }

        // Создание или получение команды для тега
        String teamName = "discord_" + player.getName();
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        team.setPrefix("§7[§b" + discordTag + "§7] §f");
        team.addEntry(player.getName());

        // Также установка в player list
        player.setPlayerListName("§7[§b" + discordTag + "§7] §f" + player.getName());
    }

    public boolean removePlayerLink(UUID uuid) {
        try {
            boolean removed = databaseManager.removeLink(uuid);
            if (removed) {
                pendingAuth.remove(uuid);

                // Удаление тега
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    removePlayerTag(player);
                }
            }
            return removed;
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка удаления привязки: " + e.getMessage());
            return false;
        }
    }

    private void removePlayerTag(Player player) {
        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard != null) {
            String teamName = "discord_" + player.getName();
            Team team = scoreboard.getTeam(teamName);
            if (team != null) {
                team.removeEntry(player.getName());
                team.unregister();
            }
        }

        player.setPlayerListName(player.getName());
    }

    public void loadPlayerTag(Player player) {
        try {
            DatabaseManager.PlayerLink link = databaseManager.getLink(player.getUniqueId());
            if (link != null) {
                setPlayerTag(player, link.getDiscordTag());
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка загрузки тега игрока: " + e.getMessage());
        }
    }

    public void onPlayerQuit(UUID playerUuid) {
        // Удаление кода при выходе игрока
        authCodes.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(playerUuid)) {
                String code = entry.getKey();
                BukkitTask task = codeCleanupTasks.remove(code);
                if (task != null) {
                    task.cancel();
                }
                return true;
            }
            return false;
        });

        pendingAuth.remove(playerUuid);
    }
}
