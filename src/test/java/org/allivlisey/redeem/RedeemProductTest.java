package org.allivlisey.redeem;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.object.SpriteObjectContents;
import org.bukkit.Art;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedeemProductTest {
    @BeforeEach void startServer() { MockBukkit.mock(); }
    @AfterEach void stopServer() { MockBukkit.unmock(); }

    @ParameterizedTest
    @CsvSource({
        "STONE, blocks, block/stone",
        "SHULKER_BOX, blocks, block/shulker_box",
        "OAK_STAIRS, blocks, block/oak_planks",
        "PAPER, items, item/paper",
        "WAXED_COPPER_BLOCK, blocks, block/copper_block",
        "WAXED_EXPOSED_COPPER_LANTERN, items, item/exposed_copper_lantern",
        "WHITE_STAINED_GLASS_PANE, blocks, block/white_stained_glass",
        "CRIMSON_HYPHAE, blocks, block/crimson_stem",
        "MANGROVE_ROOTS, blocks, block/mangrove_roots_side",
        "TALL_GRASS, blocks, block/tall_grass_top",
        "SUNFLOWER, blocks, block/sunflower_front",
        "SNOW_BLOCK, blocks, block/snow",
        "MAGMA_BLOCK, blocks, block/magma",
        "DRIED_KELP_BLOCK, blocks, block/dried_kelp_side",
        "LECTERN, blocks, block/lectern_front",
        "WHITE_CANDLE, items, item/white_candle",
        "POINTED_DRIPSTONE, items, item/pointed_dripstone",
        "SEA_LANTERN, blocks, block/sea_lantern",
        "JACK_O_LANTERN, blocks, block/jack_o_lantern"
    })
    void resolvesVanillaTextureAliases(Material material, String atlas, String sprite) {
        var icon = (ObjectComponent) new RedeemProduct("", material, null).icon();
        var contents = (SpriteObjectContents) icon.contents();
        assertEquals(Key.key(atlas), contents.atlas());
        assertEquals(Key.key(sprite), contents.sprite());
    }

    @ParameterizedTest
    @CsvSource({
        "OAK_LEAVES, 0x48B518", "SPRUCE_LEAVES, 0x619961", "BIRCH_LEAVES, 0x80A755",
        "MANGROVE_LEAVES, 0x92C648", "GRASS_BLOCK, 0x7CBD6B", "LILY_PAD, 0x71C35C"
    })
    void appliesVanillaFoliageTint(Material material, int color) {
        assertEquals(color, new RedeemProduct("", material, null).icon().color().value());
    }

    @Test
    void paintingUsesItsAssetId() {
        Art painting = mock(Art.class);
        when(painting.assetId()).thenReturn(Key.key("example:custom_art"));
        var icon = (ObjectComponent) new RedeemProduct("painting", Material.PAINTING, painting).icon();
        var contents = (SpriteObjectContents) icon.contents();
        assertEquals(Key.key("minecraft:paintings"), contents.atlas());
        assertEquals(Key.key("example:custom_art"), contents.sprite());
    }
}
