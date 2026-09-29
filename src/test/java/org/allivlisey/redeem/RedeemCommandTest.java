package org.allivlisey.redeem;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import revxrsal.commands.Lamp;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.Map;

import static net.kyori.adventure.text.format.TextDecoration.ITALIC;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedeemCommandTest {
    private ServerMock server;
    private PlayerMock player;
    private JavaPlugin plugin;
    private Messages messages;
    private RedeemDialogs dialogs;
    private RedeemCommand command;
    private Lamp<BukkitCommandActor> lamp;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.loadWith(CommandTestPlugin.class, getClass().getResourceAsStream("/plugin.yml"));
        player = server.addPlayer("Builder");
        messages = mock(Messages.class);
        when(messages.text(anyString())).thenAnswer(invocation -> Component.text((String) invocation.getArgument(0)));
        when(messages.text(anyString(), anyMap())).thenAnswer(invocation -> Component.text((String) invocation.getArgument(0)));
        dialogs = mock(RedeemDialogs.class);
        var voucher = Vouchers.create(Component.text("custom voucher"), List.of(Component.text("custom lore")));
        command = new RedeemCommand(dialogs, voucher, messages, mock(Runnable.class));
        lamp = command.register(plugin);
    }

    @AfterEach
    void tearDown() {
        if (lamp != null) lamp.unregisterAllCommands();
        MockBukkit.unmock();
    }

    @Test
    void playerWithUsePermissionOpensCategories() {
        assertTrue(run(player, "open"));
        verify(dialogs).openCategories(player);
    }

    @Test
    void unexpectedFailureUsesConfiguredMessage() {
        doThrow(new IllegalStateException("dialog unavailable")).when(dialogs).openCategories(player);
        run(player, "open");
        assertEquals(Component.text("command.failed"), player.nextComponentMessage());
    }

    @Test
    void deniesCatalogWithoutUsePermissionAndFromConsole() {
        player.addAttachment(plugin, "tianjiredeem.use", false);
        run(player, "open");
        assertEquals(Component.text("command.no-permission"), player.nextComponentMessage());
        run(server.getConsoleSender(), "open");
        assertEquals(Component.text("command.player-only"), server.getConsoleSender().nextComponentMessage());
        verifyNoInteractions(dialogs);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Builder", "Target"})
    void openingForANamedPlayerRequiresAdminPermission(String targetName) {
        server.addPlayer("Target");
        run(player, "open", targetName);
        assertEquals(Component.text("command.no-permission"), player.nextComponentMessage());
        verifyNoInteractions(dialogs);
    }

    @Test
    void adminCanOpenForAnotherPlayerWithoutUsePermission() {
        var target = server.addPlayer("Target");
        player.addAttachment(plugin, "tianjiredeem.use", false);
        player.addAttachment(plugin, "tianjiredeem.admin.open", true);
        run(player, "open", "Target");
        verify(dialogs).openCategories(target);
        verifyNoMoreInteractions(dialogs);
        assertNull(player.nextComponentMessage());
    }

    @Test
    void opCanOpenForAnotherPlayerByDefault() {
        var target = server.addPlayer("Target");
        player.setOp(true);
        run(player, "open", "Target");
        verify(dialogs).openCategories(target);
        verifyNoMoreInteractions(dialogs);
    }

    @Test
    void consoleCanOpenOnlyForAnExactOnlinePlayerIgnoringCase() {
        var console = server.getConsoleSender();
        run(console, "open", "Buil");
        run(console, "open", "Offline");
        assertEquals(Component.text("command.player-not-found"), console.nextComponentMessage());
        assertEquals(Component.text("command.player-not-found"), console.nextComponentMessage());
        verifyNoInteractions(dialogs);
        run(console, "OPEN", "builder");
        verify(dialogs).openCategories(player);
        assertNull(console.nextComponentMessage());
    }

    @Test
    void giveRequiresItsOwnPermissionAndDoesNotRequireUse() {
        run(player, "give", "1");
        assertEquals(Component.text("command.no-permission"), player.nextComponentMessage());
        assertEquals(0, Vouchers.count(player.getInventory()));
        player.addAttachment(plugin, "tianjiredeem.use", false);
        player.addAttachment(plugin, "tianjiredeem.admin.give", true);
        run(player, "give", "65");
        assertEquals(65, Vouchers.count(player.getInventory()));
        assertEquals(Component.text("command.given"), player.nextComponentMessage());
        assertNull(player.nextComponentMessage());
        verify(messages).text("command.given", Map.of("player", "Builder", "amount", "65"));
        assertEquals(Material.FIELD_MASONED_BANNER_PATTERN, player.getInventory().getItem(0).getType());
        assertEquals(Component.text("custom voucher").decoration(ITALIC, false), player.getInventory().getItem(0).getItemMeta().displayName());
        assertEquals(List.of(Component.text("custom lore").decoration(ITALIC, false)), player.getInventory().getItem(0).getItemMeta().lore());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "abc", "2147483648"})
    void invalidAmountsDoNotGiveAnything(String amount) {
        player.setOp(true);
        run(player, "give", amount);
        assertEquals(Component.text("command.invalid-amount"), player.nextComponentMessage());
        assertEquals(0, Vouchers.count(player.getInventory()));
    }

    @Test
    void consoleRequiresAnExactOnlineTarget() {
        var console = server.getConsoleSender();
        run(console, "give", "2");
        assertEquals(Component.text("command.player-required"), console.nextComponentMessage());
        run(console, "give", "2", "Buil");
        assertEquals(Component.text("command.player-not-found"), console.nextComponentMessage());
        verify(messages).text("command.player-not-found", Map.of("player", "Buil"));
        assertEquals(0, Vouchers.count(player.getInventory()));
        run(console, "give", "2", "Builder");
        assertEquals(2, Vouchers.count(player.getInventory()));
        assertEquals(Component.text("command.given"), console.nextComponentMessage());
        assertEquals(Component.text("command.received"), player.nextComponentMessage());
        verify(messages).text("command.received", Map.of("player", "Builder", "amount", "2"));
    }

    @Test
    void adminCanGiveToAnotherPlayerAndOverflowDropsAtTheirFeet() {
        player.setOp(true);
        var target = server.addPlayer("Target");
        // MockBukkit's addItem also visits armor/offhand; occupy them to model full storage.
        for (int slot = 0; slot < target.getInventory().getSize(); slot++) {
            target.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
        }
        run(player, "give", "70", "Target");
        assertEquals(0, Vouchers.count(player.getInventory()));
        assertEquals(0, Vouchers.count(target.getInventory()));
        var drops = target.getWorld().getEntities().stream().filter(Item.class::isInstance).map(Item.class::cast).toList();
        assertEquals(70, drops.stream().map(Item::getItemStack).filter(Vouchers::isVoucher).mapToInt(ItemStack::getAmount).sum());
        assertTrue(drops.stream().allMatch(item -> item.getLocation().equals(target.getLocation())));
        assertEquals(Component.text("command.received"), target.nextComponentMessage());
        verify(messages).text("command.given", Map.of("player", "Target", "amount", "70"));
    }

    @Test
    void rejectsUnknownSubcommandsMissingAmountsAndExtraArguments() {
        player.setOp(true);
        run(player);
        run(player, "unknown");
        run(player, "open", "Builder", "extra");
        run(player, "give");
        run(player, "give", "1", "Builder", "extra");
        for (int i = 0; i < 5; i++) assertEquals(Component.text("command.usage"), player.nextComponentMessage());
        assertEquals(0, Vouchers.count(player.getInventory()));
        verifyNoInteractions(dialogs);
    }

    @Test
    void completionOnlyOffersPermittedCommandAndMatchingOnlineNames() {
        server.addPlayer("Alice");
        server.addPlayer("Alex");
        assertEquals(List.of("open"), complete(""));
        assertEquals(List.of("open"), complete("o"));
        assertEquals(List.of(), complete("open", ""));
        assertEquals(List.of(), complete("give", "1", ""));
        player.addAttachment(plugin, "tianjiredeem.admin.give", true);
        player.addAttachment(plugin, "tianjiredeem.admin.open", true);
        assertEquals(List.of("give", "open"), complete("").stream().sorted().toList());
        assertEquals(List.of(), complete("r"));
        player.addAttachment(plugin, "tianjiredeem.admin.reload", true);
        assertEquals(List.of("give", "open", "reload"), complete("").stream().sorted().toList());
        assertEquals(List.of("reload"), complete("r"));
        assertEquals(List.of(), complete("reload", ""));
        assertEquals(List.of("give"), complete("g"));
        // Literal completion follows Lamp's native case-sensitive behavior.
        assertEquals(List.of(), complete("G"));
        assertEquals(List.of(), complete("x"));
        assertEquals(List.of(), complete("give", ""));
        assertEquals(List.of("Alex", "Alice"), complete("give", "1", "a"));
        assertEquals(List.of("Alex", "Alice"), complete("GIVE", "1", "A"));
        assertEquals(List.of("Alex", "Alice"), complete("open", "a"));
        assertEquals(List.of("Alex", "Alice"), complete("OPEN", "A"));
        assertEquals(List.of(), complete("open", "Builder", ""));
        assertEquals(List.of(), complete("unknown", "1", ""));
        assertEquals(List.of(), complete("give", "1", "Builder", ""));
    }

    @Test
    void hidesOpenCompletionWhenBothPermissionsAreDenied() {
        player.addAttachment(plugin, "tianjiredeem.use", false);
        assertEquals(List.of(), complete(""));
        assertEquals(List.of(), complete("open", ""));
    }

    private boolean run(CommandSender sender, String... args) {
        return plugin.getCommand("tianjiredeem").execute(sender, "tianjiredeem", args);
    }

    private List<String> complete(String... args) {
        return plugin.getCommand("tianjiredeem").tabComplete(player, "tianjiredeem", args);
    }

    public static class CommandTestPlugin extends JavaPlugin {}
}
