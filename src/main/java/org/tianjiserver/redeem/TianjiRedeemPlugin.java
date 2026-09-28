package org.tianjiserver.redeem;

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

    @Override
    public void onEnable() {
        // Bundled text remains available for reporting an invalid messages.yml.
        Messages messages = Messages.load(YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getResource("messages.yml")), StandardCharsets.UTF_8)));
        try {
            saveDefaultConfig();
            if (!new File(getDataFolder(), "messages.yml").exists()) saveResource("messages.yml", false);
            messages = Messages.load(readYaml("messages.yml"));
            RedeemConfig config = RedeemConfig.load(readYaml("config.yml"));
            RedeemDialogs dialogs = new RedeemDialogs(this, config.products(), messages, new RedeemService());
            var voucher = Vouchers.create(config.voucherName(), config.voucherLore());
            commands = new RedeemCommand(dialogs, voucher, messages).register(this);
        } catch (IllegalArgumentException error) {
            getLogger().severe(messages.plain("startup.invalid-config", Map.of("error", error.getMessage())));
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (commands != null) {
            commands.unregisterAllCommands();
            commands = null;
        }
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
