package ru.example.authdiscord.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.example.authdiscord.manager.AuthManager;

public class UnlinkCommand implements CommandExecutor {

    private final AuthManager authManager;

    public UnlinkCommand(AuthManager authManager) {
        this.authManager = authManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("authdiscord.admin")) {
            sender.sendMessage("§cУ вас нет прав для выполнения этой команды.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§cИспользование: /unlink <игрок>");
            return true;
        }

        String playerName = args[0];
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);

        if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline()) {
            sender.sendMessage("§cИгрок не найден.");
            return true;
        }

        boolean removed = authManager.removePlayerLink(offlinePlayer.getUniqueId());

        if (removed) {
            sender.sendMessage("§aПривязка игрока " + offlinePlayer.getName() + " была удалена.");

            // Если игрок онлайн, уведомляем его
            if (offlinePlayer.isOnline()) {
                Player onlinePlayer = (Player) offlinePlayer;
                onlinePlayer.sendMessage("§cВаша привязка Discord была удалена администратором.");
            }
        } else {
            sender.sendMessage("§cУ игрока " + offlinePlayer.getName() + " нет активной привязки.");
        }

        return true;
    }
}
