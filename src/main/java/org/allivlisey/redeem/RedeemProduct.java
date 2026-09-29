package org.allivlisey.redeem;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Art;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record RedeemProduct(String category, Material material, Art paintingVariant) {
    public Component name() {
        return paintingVariant == null ? Component.translatable(material.translationKey()) : paintingVariant.title();
    }

    public Component icon() {
        if (paintingVariant != null) {
            return Component.object(ObjectContents.sprite(Key.key("minecraft:paintings"), paintingVariant.assetId()));
        }
        String name = material.getKey().getKey().replaceFirst("^waxed_", "");
        if (name.endsWith("glass_pane")) name = name.substring(0, name.length() - 5);
        if (name.endsWith("_hyphae")) name = name.substring(0, name.length() - 7) + "_stem";
        String texture = switch (name) {
            case "azalea", "flowering_azalea", "mangrove_roots", "muddy_mangrove_roots", "basalt",
                 "quartz_block", "podzol", "mycelium", "bone_block", "hay_block",
                 "honey_block", "pumpkin", "melon", "cactus", "ochre_froglight", "verdant_froglight",
                 "pearlescent_froglight", "scaffolding", "lodestone" -> "block/" + name + "_side";
            case "grass_block", "tall_grass", "large_fern", "lilac", "rose_bush", "peony", "small_dripleaf",
                 "big_dripleaf" -> "block/" + name + "_top";
            case "magma_block" -> "block/magma";
            case "snow_block" -> "block/snow";
            case "dried_kelp_block" -> "block/dried_kelp_side";
            case "sunflower" -> "block/sunflower_front";
            case "lectern" -> "block/lectern_front";
            case "oak_stairs" -> "block/oak_planks";
            case "mangrove_propagule", "resin_clump", "leaf_litter", "firefly_bush", "pink_petals",
                 "wildflowers", "nether_sprouts", "sugar_cane", "kelp", "seagrass", "sea_pickle",
                 "flower_pot", "pointed_dripstone", "sulfur_spike", "pitcher_plant", "bamboo",
                 "campfire", "soul_campfire", "bell", "lantern", "soul_lantern", "copper_lantern",
                 "exposed_copper_lantern", "weathered_copper_lantern", "oxidized_copper_lantern" -> "item/" + name;
            default -> (!material.isBlock() || name.equals("candle") || name.endsWith("_candle")
                ? "item/" : "block/") + name;
        };
        int color = switch (material) {
            case OAK_LEAVES, JUNGLE_LEAVES, ACACIA_LEAVES, DARK_OAK_LEAVES, VINE -> 0x48B518;
            case SPRUCE_LEAVES -> 0x619961;
            case BIRCH_LEAVES -> 0x80A755;
            case MANGROVE_LEAVES -> 0x92C648;
            case GRASS_BLOCK, SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN, BUSH -> 0x7CBD6B;
            case LILY_PAD -> 0x71C35C;
            default -> 0xFFFFFF;
        };
        return Component.object(ObjectContents.sprite(
            Key.key(texture.startsWith("item/") ? "minecraft:items" : "minecraft:blocks"), Key.key(texture)))
            .color(TextColor.color(color));
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(material);
        if (paintingVariant != null) item.setData(DataComponentTypes.PAINTING_VARIANT, paintingVariant);
        return item;
    }
}
