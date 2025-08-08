package ru.example.authdiscord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.plugin.java.JavaPlugin;
import ru.example.authdiscord.commands.UnlinkCommand;
import ru.example.authdiscord.commands.ReloadAuthCommand;
import ru.example.authdiscord.database.DatabaseManager;
import ru.example.authdiscord.discord.DiscordBot;
import ru.example.authdiscord.listeners.PlayerEventListener;
import ru.example.authdiscord.manager.AuthManager;

import java.util.logging.Level;

public class AuthDiscordPlugin extends JavaPlugin {

    private DatabaseManager databaseManager;
    private AuthManager authManager;
    private JDA jda;
    private DiscordBot discordBot;

    @Override
    public void onEnable() {
        // Сохранение конфига по умолчанию
        saveDefaultConfig();

        // Инициализация базы данных
        try {
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();
            getLogger().info("База данных инициализирована");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Ошибка инициализации базы данных", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Инициализация менеджера авторизации
        authManager = new AuthManager(this, databaseManager);

        // Инициализация Discord бота
        try {
            initializeDiscordBot();
            getLogger().info("Discord бот запущен");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Ошибка запуска Discord бота", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Регистрация обработчиков событий
        getServer().getPluginManager().registerEvents(new PlayerEventListener(this, authManager), this);

        // Регистрация команд
        getCommand("unlink").setExecutor(new UnlinkCommand(authManager));
        getCommand("reloadauth").setExecutor(new ReloadAuthCommand(this));

        getLogger().info("AuthDiscord плагин успешно запущен!");
    }

    @Override
    public void onDisable() {
        // Остановка Discord бота
        if (jda != null) {
            jda.shutdownNow();
            getLogger().info("Discord бот остановлен");
        }

        // Закрытие базы данных
        if (databaseManager != null) {
            databaseManager.close();
            getLogger().info("База данных закрыта");
        }

        getLogger().info("AuthDiscord плагин отключен");
    }

    private void initializeDiscordBot() throws InterruptedException {
        String token = getConfig().getString("bot_token");
        if (token == null || token.equals("YOUR_DISCORD_BOT_TOKEN_HERE")) {
            throw new IllegalArgumentException("Необходимо указать валидный Discord Bot Token в config.yml");
        }

        discordBot = new DiscordBot(this, authManager);

        jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.DIRECT_MESSAGES)
                .addEventListeners(discordBot)
                .build();

        jda.awaitReady();
    }

    public void reloadPlugin() {
        reloadConfig();

        // Перезапуск Discord бота
        if (jda != null) {
            jda.shutdownNow();
            try {
                initializeDiscordBot();
                getLogger().info("Discord бот перезапущен");
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Ошибка перезапуска Discord бота", e);
            }
        }
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public AuthManager getAuthManager() {
        return authManager;
    }

    public JDA getJDA() {
        return jda;
    }
}
