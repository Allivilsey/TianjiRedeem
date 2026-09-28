package org.tianjiserver.redeem;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class ItemDelivery {
    private ItemDelivery() {}

    static void give(Player player, ItemStack template, int amount) {
        int stackSize = Math.min(template.getMaxStackSize(), player.getInventory().getMaxStackSize());
        while (amount > 0) {
            int size = Math.min(amount, stackSize);
            ItemStack stack = template.clone();
            stack.setAmount(size);
            for (ItemStack overflow : player.getInventory().addItem(stack).values()) {
                player.getWorld().dropItem(player.getLocation(), overflow);
            }
            amount -= size;
        }
    }
}
