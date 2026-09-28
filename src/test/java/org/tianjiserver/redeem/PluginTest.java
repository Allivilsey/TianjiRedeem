package org.tianjiserver.redeem;

import java.io.File;
import java.nio.file.Files;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

class PluginTest {
    private ServerMock server;

    @BeforeEach
    void setUp() { server = MockBukkit.mock(); }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void enablesWithDefaultFilesAndSingleCommand() {
        TianjiRedeemPlugin plugin = MockBukkit.load(TianjiRedeemPlugin.class);
        assertTrue(plugin.isEnabled());
        assertTrue(new File(plugin.getDataFolder(), "config.yml").isFile());
        assertTrue(new File(plugin.getDataFolder(), "messages.yml").isFile());
        assertEquals(1, plugin.getDescription().getCommands().size());
        assertTrue(plugin.getCommand("tianjiredeem").getAliases().isEmpty());
        var player = server.addPlayer();
        assertTrue(player.hasPermission("tianjiredeem.use"));
        assertFalse(player.hasPermission("tianjiredeem.admin.give"));
        player.setOp(true);
        assertTrue(server.dispatchCommand(player, "tianjiredeem give 65"));
        int held = java.util.Arrays.stream(player.getInventory().getContents())
                .filter(Vouchers::isVoucher).mapToInt(org.bukkit.inventory.ItemStack::getAmount).sum();
        int dropped = player.getWorld().getEntities().stream().filter(Item.class::isInstance).map(Item.class::cast)
                .map(Item::getItemStack).filter(Vouchers::isVoucher).mapToInt(org.bukkit.inventory.ItemStack::getAmount).sum();
        assertEquals(65, held + dropped);
    }

    @Test
    void malformedConfigDisablesPluginInsteadOfUsingDefaults() throws Exception {
        TianjiRedeemPlugin plugin = MockBukkit.load(TianjiRedeemPlugin.class);
        server.getPluginManager().disablePlugin(plugin);
        Files.writeString(new File(plugin.getDataFolder(), "config.yml").toPath(), "products: [\n");
        server.getPluginManager().enablePlugin(plugin);
        assertFalse(plugin.isEnabled());
    }

    @Test
    void invalidMaterialDisablesPlugin() throws Exception {
        TianjiRedeemPlugin plugin = MockBukkit.load(TianjiRedeemPlugin.class);
        server.getPluginManager().disablePlugin(plugin);
        YamlConfiguration config = new YamlConfiguration();
        config.set("voucher.material", "AIR");
        config.set("products", java.util.List.of());
        config.save(new File(plugin.getDataFolder(), "config.yml"));
        server.getPluginManager().enablePlugin(plugin);
        assertFalse(plugin.isEnabled());
    }
}
