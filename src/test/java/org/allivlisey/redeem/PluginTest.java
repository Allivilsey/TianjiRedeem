package org.allivlisey.redeem;

import java.io.File;
import java.nio.file.Files;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static net.kyori.adventure.text.format.TextDecoration.ITALIC;
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
        assertFalse(player.hasPermission("tianjiredeem.admin.open"));
        assertFalse(player.hasPermission("tianjiredeem.admin.give"));
        assertFalse(player.hasPermission("tianjiredeem.admin.reload"));
        player.setOp(true);
        assertTrue(player.hasPermission("tianjiredeem.admin.open"));
        assertTrue(player.hasPermission("tianjiredeem.admin.reload"));
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
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        var config = YamlConfiguration.loadConfiguration(configFile);
        config.set("voucher.material", "PAPER");
        config.set("voucher.name", "&e配置中的兑换券");
        config.set("voucher.lore", java.util.List.of("&7第一行", "第二行"));
        config.save(configFile);
        server.dispatchCommand(server.getConsoleSender(), "tianjiredeem reload");
        assertTrue(plugin.isEnabled());
        var player = server.addPlayer();
        player.setOp(true);
        server.dispatchCommand(player, "tianjiredeem give 2");
        var voucher = player.getInventory().getItem(0);
        var legacy = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand();
        assertEquals(org.bukkit.Material.FIELD_MASONED_BANNER_PATTERN, voucher.getType());
        assertEquals(legacy.deserialize("&e配置中的兑换券").decoration(ITALIC, false), voucher.getItemMeta().displayName());
        assertEquals(java.util.List.of(legacy.deserialize("&7第一行").decoration(ITALIC, false), legacy.deserialize("第二行").decoration(ITALIC, false)),
                voucher.getItemMeta().lore());
        assertEquals(2, Vouchers.count(player.getInventory()));
    }

    @Test
    void reloadRequiresItsOwnPermissionAndRejectsExtraArguments() throws Exception {
        TianjiRedeemPlugin plugin = MockBukkit.load(TianjiRedeemPlugin.class);
        var player = server.addPlayer();
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        var messages = YamlConfiguration.loadConfiguration(messagesFile);
        messages.set("command.reloaded", "reloaded");
        messages.set("command.usage", "new usage");
        messages.save(messagesFile);

        server.dispatchCommand(player, "tianjiredeem reload");
        assertTrue(player.nextMessage().contains("权限"));
        player.addAttachment(plugin, "tianjiredeem.use", false);
        player.addAttachment(plugin, "tianjiredeem.admin.reload", true);
        server.dispatchCommand(player, "tianjiredeem reload extra");
        assertTrue(player.nextMessage().contains("用法"));
        server.dispatchCommand(player, "tianjiredeem reload");
        assertEquals(Component.text("reloaded"), player.nextComponentMessage());
        server.dispatchCommand(player, "tianjiredeem");
        assertEquals(Component.text("new usage"), player.nextComponentMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"config.yml", "messages.yml"})
    void invalidReloadPreservesCurrentSettingsAndCanBeRetried(String invalidFile) throws Exception {
        TianjiRedeemPlugin plugin = MockBukkit.load(TianjiRedeemPlugin.class);
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        var originalConfig = YamlConfiguration.loadConfiguration(configFile);
        var updatedConfig = YamlConfiguration.loadConfiguration(configFile);
        updatedConfig.set("voucher.name", "new voucher");
        updatedConfig.save(configFile);
        var updatedMessages = YamlConfiguration.loadConfiguration(messagesFile);
        updatedMessages.set("command.usage", "new usage");
        updatedMessages.set("command.reloaded", "reloaded");
        updatedMessages.save(messagesFile);
        Files.writeString(new File(plugin.getDataFolder(), invalidFile).toPath(), "invalid: [\n");

        var console = server.getConsoleSender();
        server.dispatchCommand(console, "tianjiredeem reload");
        assertTrue(plugin.isEnabled());
        assertTrue(console.nextMessage().contains(invalidFile));
        var player = server.addPlayer();
        server.dispatchCommand(player, "tianjiredeem");
        assertTrue(player.nextMessage().contains("用法"));
        server.dispatchCommand(console, "tianjiredeem give 1 " + player.getName());
        console.nextComponentMessage();
        assertEquals(RedeemConfig.load(originalConfig).voucherName().decoration(ITALIC, false),
                player.getInventory().getItem(0).getItemMeta().displayName());

        updatedConfig.save(configFile);
        updatedMessages.save(messagesFile);
        server.dispatchCommand(console, "tianjiredeem reload");
        assertEquals(Component.text("reloaded"), console.nextComponentMessage());
        server.dispatchCommand(console, "tianjiredeem");
        assertEquals(Component.text("new usage"), console.nextComponentMessage());
        server.dispatchCommand(console, "tianjiredeem give 1 " + player.getName());
        assertEquals(Component.text("new voucher").decoration(ITALIC, false),
                player.getInventory().getItem(1).getItemMeta().displayName());
    }

    @Test
    void existingMessagesWithoutReloadTextsRemainUsable() throws Exception {
        TianjiRedeemPlugin plugin = MockBukkit.load(TianjiRedeemPlugin.class);
        server.getPluginManager().disablePlugin(plugin);
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        var messages = YamlConfiguration.loadConfiguration(messagesFile);
        messages.set("command.reloaded", null);
        messages.set("command.reload-failed", null);
        messages.save(messagesFile);
        server.getPluginManager().enablePlugin(plugin);
        assertTrue(plugin.isEnabled());
        server.dispatchCommand(server.getConsoleSender(), "tianjiredeem reload");
        assertTrue(server.getConsoleSender().nextMessage().contains("已重新加载"));
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
