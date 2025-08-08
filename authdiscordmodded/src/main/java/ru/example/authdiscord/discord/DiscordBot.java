package ru.example.authdiscord.discord;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.example.authdiscord.manager.AuthManager;

import java.util.UUID;

public class DiscordBot extends ListenerAdapter {

    private final Plugin plugin;
    private final AuthManager authManager;

    public DiscordBot(Plugin plugin, AuthManager authManager) {
        this.plugin = plugin;
        this.authManager = authManager;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Обрабатываем только личные сообщения
        if (event.getChannelType() != ChannelType.PRIVATE) {
            return;
        }

        // Игнорируем сообщения от ботов
        if (event.getAuthor().isBot()) {
            return;
        }

        String message = event.getMessage().getContentRaw().trim().toUpperCase();
        User author = event.getAuthor();

        // Проверяем, является ли сообщение кодом авторизации
        UUID playerUuid = authManager.getPlayerByCode(message);
        if (playerUuid == null) {
            event.getChannel().sendMessage(
                plugin.getConfig().getString("messages.invalid_code", "❌ Неверный код авторизации.")
            ).queue();
            return;
        }

        // Проверяем, онлайн ли игрок
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) {
            authManager.removeAuthCode(message);
            event.getChannel().sendMessage("❌ Игрок не в сети.").queue();
            return;
        }

        // Проверяем участие в гильдии
        String guildId = plugin.getConfig().getString("guild_id");
        Guild guild = event.getJDA().getGuildById(guildId);

        if (guild == null) {
            plugin.getLogger().warning("Гильдия с ID " + guildId + " не найдена!");
            event.getChannel().sendMessage("❌ Ошибка конфигурации сервера.").queue();
            return;
        }

        // Асинхронная проверка участия в гильдии
        guild.retrieveMember(author).queue(
            member -> handleGuildMember(event, message, playerUuid, member),
            throwable -> handleNotInGuild(event, message)
        );
    }

    private void handleGuildMember(MessageReceivedEvent event, String code, UUID playerUuid, Member member) {
        User author = event.getAuthor();
        String discordTag = author.getGlobalName() != null ? author.getGlobalName() : author.getName();

        // Выполняем авторизацию в основном потоке сервера
        Bukkit.getScheduler().runTask(plugin, () -> {
            authManager.completeAuth(playerUuid, author.getId(), discordTag);
            authManager.removeAuthCode(code);
        });

        // Отправляем подтверждение в Discord
        event.getChannel().sendMessage(
            plugin.getConfig().getString("messages.auth_complete", "✅ Привязка выполнена! Теперь вы можете играть.")
        ).queue();
    }

    private void handleNotInGuild(MessageReceivedEvent event, String code) {
        // Удаляем код авторизации
        authManager.removeAuthCode(code);

        // Отправляем сообщение об ошибке
        event.getChannel().sendMessage(
            plugin.getConfig().getString("messages.not_in_guild", "❌ Вы не состоите в нужном Discord-сервере.")
        ).queue();
    }
}
