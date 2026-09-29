package org.allivlisey.redeem;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class RedeemService {
    public boolean redeem(Player player, RedeemProduct selection, int amount) {
        ItemStack product = selection.createItem();
        if (!Vouchers.consume(player.getInventory(), amount)) return false;
        ItemDelivery.give(player, product, 64 * amount);
        return true;
    }
}
