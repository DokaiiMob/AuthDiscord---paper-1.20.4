package ru.example.authdiscord.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;
import ru.example.authdiscord.manager.AuthManager;

public class PlayerEventListener implements Listener {

    private final Plugin plugin;
    private final AuthManager authManager;

    public PlayerEventListener(Plugin plugin, AuthManager authManager) {
        this.plugin = plugin;
        this.authManager = authManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (authManager.isPlayerAuthenticated(player.getUniqueId())) {
            // Игрок уже авторизован, загружаем его тег
            authManager.loadPlayerTag(player);
        } else {
            // Требуется авторизация
            String code = authManager.generateAuthCode(player.getUniqueId());

            String message = plugin.getConfig().getString("messages.auth_required",
                "§eДля авторизации напишите этот код боту в Discord: §b%code%");

            player.sendMessage(message.replace("%code%", code));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        authManager.onPlayerQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (shouldBlockAction(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (shouldBlockAction(event.getPlayer())) {
            event.setCancelled(true);
            sendAuthMessage(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (shouldBlockAction(event.getPlayer())) {
            event.setCancelled(true);
            sendAuthMessage(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (shouldBlockAction(event.getPlayer())) {
            event.setCancelled(true);
            sendAuthMessage(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            if (shouldBlockAction(player)) {
                event.setCancelled(true);
                sendAuthMessage(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        if (shouldBlockAction(event.getPlayer())) {
            event.setCancelled(true);
            // Отправляем сообщение синхронно
            plugin.getServer().getScheduler().runTask(plugin, () ->
                sendAuthMessage(event.getPlayer())
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (shouldBlockAction(event.getPlayer())) {
            // Разрешаем только основные команды
            String command = event.getMessage().toLowerCase();
            if (!command.startsWith("/help") &&
                !command.startsWith("/rules") &&
                !command.startsWith("/info")) {
                event.setCancelled(true);
                sendAuthMessage(event.getPlayer());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (shouldBlockAction(event.getPlayer())) {
            event.setCancelled(true);
            sendAuthMessage(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPickupItem(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (shouldBlockAction(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (shouldBlockAction(player)) {
                event.setCancelled(true);
                sendAuthMessage(player);
            }
        }
    }

    private boolean shouldBlockAction(Player player) {
        return authManager.requiresAuth(player.getUniqueId());
    }

    private void sendAuthMessage(Player player) {
        String message = plugin.getConfig().getString("messages.auth_frozen",
            "§cВы должны авторизоваться через Discord!");
        player.sendMessage(message);
    }
}
