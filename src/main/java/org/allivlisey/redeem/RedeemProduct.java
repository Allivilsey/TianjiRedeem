package org.allivlisey.redeem;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import org.bukkit.Art;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record RedeemProduct(String category, Material material, Art paintingVariant) {
    public Component name() {
        return paintingVariant == null ? Component.translatable(material.translationKey()) : paintingVariant.title();
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(material);
        if (paintingVariant != null) item.setData(DataComponentTypes.PAINTING_VARIANT, paintingVariant);
        return item;
    }
}
