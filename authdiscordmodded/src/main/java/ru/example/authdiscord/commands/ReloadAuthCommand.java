package ru.example.authdiscord.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import ru.example.authdiscord.AuthDiscordPlugin;

public class ReloadAuthCommand implements CommandExecutor {

    private final AuthDiscordPlugin plugin;

    public ReloadAuthCommand(AuthDiscordPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("authdiscord.admin")) {
            sender.sendMessage("§cУ вас нет прав для выполнения этой команды.");
            return true;
        }

        try {
            plugin.reloadPlugin();
            sender.sendMessage("§aКонфигурация AuthDiscord перезагружена.");
        } catch (Exception e) {
            sender.sendMessage("§cОшибка перезагрузки конфигурации: " + e.getMessage());
            plugin.getLogger().warning("Ошибка перезагрузки: " + e.getMessage());
        }

        return true;
    }
}
