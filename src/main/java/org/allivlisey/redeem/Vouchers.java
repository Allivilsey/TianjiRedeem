package org.allivlisey.redeem;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

/** Only storage contents (hotbar and main inventory) can pay for a redemption. */
public final class Vouchers {
    public static final NamespacedKey KEY = new NamespacedKey("tianjiredeem", "voucher");

    private Vouchers() {}

    public static ItemStack create(Component name, List<Component> lore) {
        ItemStack voucher = new ItemStack(Material.FIELD_MASONED_BANNER_PATTERN);
        voucher.editMeta(meta -> {
            meta.setMaxStackSize(64);
            meta.displayName(name);
            meta.lore(lore);
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        });
        return voucher;
    }

    public static boolean isVoucher(ItemStack item) {
        return item != null && item.getAmount() > 0 && item.getPersistentDataContainer().has(KEY);
    }

    public static int count(PlayerInventory inventory) {
        return count(inventory.getStorageContents());
    }

    private static int count(ItemStack[] contents) {
        int total = 0;
        for (ItemStack item : contents) {
            if (isVoucher(item)) total += item.getAmount();
        }
        return total;
    }

    public static boolean consume(PlayerInventory inventory, int amount) {
        if (amount < 1 || amount > 64) throw new IllegalArgumentException("amount must be 1..64");
        ItemStack[] contents = inventory.getStorageContents();
        if (count(contents) < amount) return false;
        int remaining = amount;
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (!isVoucher(item)) continue;
            int taken = Math.min(item.getAmount(), remaining);
            if (taken == item.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                ItemStack rest = item.clone();
                rest.setAmount(item.getAmount() - taken);
                inventory.setItem(slot, rest);
            }
            remaining -= taken;
        }
        return true;
    }
}
