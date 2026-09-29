package org.allivlisey.redeem;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VouchersTest {
    @Test
    void countsOnlyMarkedStorageStacks() {
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack[] contents = {stack(3, true), null, stack(7, false), stack(8, true)};
        when(inventory.getStorageContents()).thenReturn(contents);
        assertEquals(11, Vouchers.count(inventory));
        verify(inventory, never()).getContents();
        verify(inventory, never()).getItemInOffHand();
    }

    @Test
    void insufficientVouchersLeaveInventoryUntouched() {
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack[] contents = {stack(2, true)};
        when(inventory.getStorageContents()).thenReturn(contents);
        assertFalse(Vouchers.consume(inventory, 3));
        verify(inventory, never()).setItem(anyInt(), any());
    }

    @Test
    void consumesInSlotOrderAndClonesPartialStackToPreserveItsData() {
        PlayerInventory inventory = mock(PlayerInventory.class);
        ItemStack first = stack(2, true);
        ItemStack second = stack(5, true);
        ItemStack remainder = mock(ItemStack.class);
        when(second.clone()).thenReturn(remainder);
        ItemStack[] contents = {first, stack(4, false), second};
        when(inventory.getStorageContents()).thenReturn(contents);
        assertTrue(Vouchers.consume(inventory, 4));
        var order = inOrder(inventory);
        order.verify(inventory).setItem(0, null);
        order.verify(inventory).setItem(2, remainder);
        verify(remainder).setAmount(3);
        verify(second, never()).setAmount(anyInt());
        verify(inventory, never()).setItem(eq(1), any());
    }

    static ItemStack stack(int amount, boolean marked) {
        ItemStack item = mock(ItemStack.class, RETURNS_DEEP_STUBS);
        when(item.getAmount()).thenReturn(amount);
        when(item.getPersistentDataContainer().has(Vouchers.KEY)).thenReturn(marked);
        return item;
    }
}
