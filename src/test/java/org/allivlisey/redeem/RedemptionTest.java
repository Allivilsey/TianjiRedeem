package org.allivlisey.redeem;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import static net.kyori.adventure.text.format.TextDecoration.ITALIC;
import static org.junit.jupiter.api.Assertions.*;

class RedemptionTest {
    private PlayerMock player;
    private final RedeemService service = new RedeemService();

    @BeforeEach
    void setUp() {
        player = MockBukkit.mock().addPlayer();
        // MockBukkit addItem incorrectly scans equipment slots too; occupy them so
        // these integration tests exercise Paper's storage-only insertion contract.
        for (int slot = 36; slot < player.getInventory().getSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.BEDROCK, 64));
        }
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void createsVoucherWithConfiguredAppearanceAndByteOneMarker() {
        Component name = Component.text("custom voucher");
        List<Component> lore = List.of(Component.text("custom lore"));
        ItemStack item = Vouchers.create(name, lore);
        assertEquals(Material.FIELD_MASONED_BANNER_PATTERN, item.getType());
        assertEquals(64, item.getMaxStackSize());
        assertTrue(item.getItemMeta().hasMaxStackSize());
        assertEquals(name.decoration(ITALIC, false), item.getItemMeta().displayName());
        assertEquals(List.of(Component.text("custom lore").decoration(ITALIC, false)), item.getItemMeta().lore());
        assertEquals((byte) 1, item.getPersistentDataContainer().get(Vouchers.KEY, PersistentDataType.BYTE));
        assertEquals(1, item.getAmount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"&5&lVoucher", "&r&5&lVoucher"})
    void coloredVoucherTextDefaultsToNonItalic(String text) {
        Component parsed = LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        var meta = Vouchers.create(parsed, List.of(parsed)).getItemMeta();
        for (Component actual : List.of(meta.displayName(), meta.lore().getFirst())) {
            assertEquals(TextDecoration.State.FALSE, actual.decoration(ITALIC));
            assertEquals(NamedTextColor.DARK_PURPLE, actual.color());
            assertEquals(TextDecoration.State.TRUE, actual.decoration(TextDecoration.BOLD));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"&5&oVoucher", "&5Normal &oItalic&r normal"})
    void preservesExplicitItalicFormatting(String text) {
        Component parsed = LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        Component expected = parsed.decorationIfAbsent(ITALIC, TextDecoration.State.FALSE);
        var meta = Vouchers.create(parsed, List.of(parsed)).getItemMeta();
        assertEquals(expected, meta.displayName());
        assertEquals(List.of(expected), meta.lore());
    }

    @Test
    void anyPdcTypeOrValueCountsButAppearanceAloneDoesNot() {
        ItemStack stringVoucher = new ItemStack(Material.DIRT, 3);
        stringVoucher.editPersistentDataContainer(pdc -> pdc.set(Vouchers.KEY, PersistentDataType.STRING, "anything"));
        ItemStack intVoucher = new ItemStack(Material.PAPER, 4);
        intVoucher.editPersistentDataContainer(pdc -> pdc.set(Vouchers.KEY, PersistentDataType.INTEGER, 0));
        ItemStack zeroVoucher = voucher(5);
        zeroVoucher.editPersistentDataContainer(pdc -> pdc.set(Vouchers.KEY, PersistentDataType.BYTE, (byte) 0));
        player.getInventory().setItem(0, stringVoucher);
        player.getInventory().setItem(8, intVoucher);
        player.getInventory().setItem(9, zeroVoucher);
        player.getInventory().setItem(35, new ItemStack(Material.FIELD_MASONED_BANNER_PATTERN, 20));
        player.getInventory().setItemInOffHand(voucher(10));
        player.getInventory().setHelmet(voucher(10));
        player.setItemOnCursor(voucher(10));
        player.getEnderChest().setItem(0, voucher(10));
        assertEquals(12, Vouchers.count(player.getInventory()));
    }

    @Test
    void partialPaymentPreservesAllMetadataAndOtherSlots() {
        ItemStack custom = voucher(5);
        custom.editMeta(meta -> {
            meta.displayName(net.kyori.adventure.text.Component.text("old voucher"));
            meta.getPersistentDataContainer().set(new NamespacedKey("other", "data"), PersistentDataType.STRING, "keep");
        });
        ItemStack expected = custom.clone();
        expected.setAmount(3);
        player.getInventory().setItem(0, voucher(2));
        player.getInventory().setItem(1, new ItemStack(Material.DIAMOND, 7));
        player.getInventory().setItem(9, custom);
        player.getInventory().setItem(35, voucher(9));
        assertTrue(Vouchers.consume(player.getInventory(), 4));
        assertNull(player.getInventory().getItem(0));
        assertEquals(expected, player.getInventory().getItem(9));
        assertEquals(7, player.getInventory().getItem(1).getAmount());
        assertEquals(9, player.getInventory().getItem(35).getAmount());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 64})
    void conservesVouchersAndProducts(int amount) {
        player.getInventory().setItem(0, voucher(64));
        assertTrue(service.redeem(player, Material.STONE, amount));
        assertEquals(64 - amount, Vouchers.count(player.getInventory()));
        assertEquals(amount * 64, produced(Material.STONE));
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == Material.STONE) {
                assertNull(item.getItemMeta().displayName());
                assertTrue(item.getPersistentDataContainer().isEmpty());
            }
        }
    }

    @Test
    void insufficientCurrentBalanceHasNoSideEffects() {
        player.getInventory().setItem(0, voucher(2));
        assertEquals(2, Vouchers.count(player.getInventory()));
        player.getInventory().setItem(0, voucher(1));
        ItemStack[] before = player.getInventory().getContents();
        assertFalse(service.redeem(player, Material.STONE, 2));
        assertArrayEquals(before, player.getInventory().getContents());
        assertEquals(0, produced(Material.STONE));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 65})
    void invalidAmountCannotConsume(int amount) {
        player.getInventory().setItem(0, voucher(64));
        assertThrows(IllegalArgumentException.class, () -> service.redeem(player, Material.STONE, amount));
        assertEquals(64, Vouchers.count(player.getInventory()));
        assertEquals(0, produced(Material.STONE));
    }

    @Test
    void fullInventoryDropsAllOverflowAndUsesFreedVoucherSlot() {
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Material.DIRT, 64));
        player.getInventory().setItem(0, voucher(2));
        assertTrue(service.redeem(player, Material.STONE, 1));
        assertEquals(64, produced(Material.STONE));
        assertEquals(64, dropped(Material.STONE));
        assertTrue(service.redeem(player, Material.STONE, 1));
        assertEquals(128, produced(Material.STONE));
        assertEquals(64, dropped(Material.STONE));
    }

    @Test
    void mergesExistingStacksAndSplitsUnstackableBlocksNormally() {
        player.getInventory().setItem(0, voucher(2));
        player.getInventory().setItem(1, new ItemStack(Material.STONE, 32));
        assertTrue(service.redeem(player, Material.STONE, 1));
        assertEquals(64, player.getInventory().getItem(1).getAmount());
        assertEquals(96, produced(Material.STONE));
        assertTrue(service.redeem(player, Material.SHULKER_BOX, 1));
        assertEquals(64, produced(Material.SHULKER_BOX));
        for (var entity : player.getWorld().getEntities()) {
            if (entity instanceof Item item && item.getItemStack().getType() == Material.SHULKER_BOX) {
                assertEquals(1, item.getItemStack().getAmount());
                assertEquals(player.getLocation(), item.getLocation());
                assertTrue(item.getItemStack().getPersistentDataContainer().isEmpty());
            }
        }
    }

    private int produced(Material material) {
        int inventory = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) inventory += item.getAmount();
        }
        return inventory + dropped(material);
    }

    private int dropped(Material material) {
        return player.getWorld().getEntities().stream().filter(Item.class::isInstance).map(Item.class::cast)
            .map(Item::getItemStack).filter(item -> item.getType() == material).mapToInt(ItemStack::getAmount).sum();
    }

    static ItemStack voucher(int amount) {
        ItemStack item = new ItemStack(Material.FIELD_MASONED_BANNER_PATTERN, amount);
        item.editPersistentDataContainer(pdc -> pdc.set(Vouchers.KEY, PersistentDataType.BYTE, (byte) 1));
        return item;
    }
}
