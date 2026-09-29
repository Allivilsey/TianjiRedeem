package org.allivlisey.redeem;

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
        assertEquals(64, player.getInventory().getItem(0).getAmount());
        assertEquals(1, player.getInventory().getItem(1).getAmount());
        assertEquals(0, dropped);
        server.dispatchCommand(player, "tianjiredeem give 1");
        assertEquals(2, player.getInventory().getItem(1).getAmount());
    }

    @Test
    void givesVoucherAppearanceFromConfigInsteadOfMessages() throws Exception {
        TianjiRedeemPlugin plugin = MockBukkit.load(TianjiRedeemPlugin.class);
        server.getPluginManager().disablePlugin(plugin);
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        var config = YamlConfiguration.loadConfiguration(configFile);
        config.set("voucher.material", "PAPER");
        config.set("voucher.name", "&e配置中的兑换券");
        config.set("voucher.lore", java.util.List.of("&7第一行", "第二行"));
        config.save(configFile);
        server.getPluginManager().enablePlugin(plugin);
        assertTrue(plugin.isEnabled());
        var player = server.addPlayer();
        player.setOp(true);
        server.dispatchCommand(player, "tianjiredeem give 2");
        var voucher = player.getInventory().getItem(0);
        var legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand();
        assertEquals(org.bukkit.Material.FIELD_MASONED_BANNER_PATTERN, voucher.getType());
        assertEquals(legacy.deserialize("&e配置中的兑换券"), voucher.getItemMeta().displayName());
        assertEquals(java.util.List.of(legacy.deserialize("&7第一行"), legacy.deserialize("第二行")),
                voucher.getItemMeta().lore());
        assertEquals(2, Vouchers.count(player.getInventory()));
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
    void invalidProductMaterialDisablesPlugin() throws Exception {
        TianjiRedeemPlugin plugin = MockBukkit.load(TianjiRedeemPlugin.class);
        server.getPluginManager().disablePlugin(plugin);
        var config = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
        config.set("products", java.util.List.of("minecraft:air"));
        config.save(new File(plugin.getDataFolder(), "config.yml"));
        server.getPluginManager().enablePlugin(plugin);
        assertFalse(plugin.isEnabled());
    }
}
