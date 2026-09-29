package org.allivlisey.redeem;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import revxrsal.commands.Lamp;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;

public class TianjiRedeemPlugin extends JavaPlugin {
    private Lamp<BukkitCommandActor> commands;
    private RedeemCommand command;

    @Override
    public void onEnable() {
        // Bundled text remains available for reporting an invalid messages.yml.
        Messages messages = Messages.load(bundledMessages());
        try {
            saveDefaultConfig();
            if (!new File(getDataFolder(), "messages.yml").exists()) saveResource("messages.yml", false);
            messages = readMessages();
            RedeemConfig config = RedeemConfig.load(readYaml("config.yml"));
            RedeemDialogs dialogs = new RedeemDialogs(this, config, messages, new RedeemService());
            var voucher = Vouchers.create(config.voucherName(), config.voucherLore());
            command = new RedeemCommand(dialogs, voucher, messages, this::reloadSettings);
            commands = command.register(this);
        } catch (IllegalArgumentException error) {
            getLogger().severe(messages.plain("startup.invalid-config", Map.of("error", error.getMessage())));
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (command != null) {
            command.invalidate();
            command = null;
        }
        if (commands != null) {
            commands.unregisterAllCommands();
            commands = null;
        }
    }

    private void reloadSettings() {
        Messages messages = readMessages();
        RedeemConfig config = RedeemConfig.load(readYaml("config.yml"));
        RedeemDialogs dialogs = new RedeemDialogs(this, config, messages, new RedeemService());
        var voucher = Vouchers.create(config.voucherName(), config.voucherLore());
        command.update(dialogs, voucher, messages);
    }

    private Messages readMessages() {
        YamlConfiguration config = readYaml("messages.yml");
        YamlConfiguration defaults = bundledMessages();
        // Existing installations predate these two messages; other keys remain required.
        config.addDefault("command.reloaded", defaults.get("command.reloaded"));
        config.addDefault("command.reload-failed", defaults.get("command.reload-failed"));
        return Messages.load(config);
    }

    private YamlConfiguration bundledMessages() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getResource("messages.yml")), StandardCharsets.UTF_8));
    }

    private YamlConfiguration readYaml(String name) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(new File(getDataFolder(), name));
            return config;
        } catch (IOException | InvalidConfigurationException error) {
            throw new IllegalArgumentException(name + ": " + error.getMessage(), error);
        }
    }
}
