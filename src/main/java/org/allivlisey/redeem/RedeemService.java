package org.allivlisey.redeem;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class RedeemService {
    public boolean redeem(Player player, Material material, int amount) {
        ItemStack product = new ItemStack(material);
        if (!Vouchers.consume(player.getInventory(), amount)) return false;
        ItemDelivery.give(player, product, 64 * amount);
        return true;
    }
}
