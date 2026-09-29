package org.allivlisey.redeem;

import org.bukkit.Material;
import org.bukkit.Art;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RedeemConfigTest {
    @BeforeEach void startServer() { MockBukkit.mock(); }
    @AfterEach void stopServer() { MockBukkit.unmock(); }

    @Test
    void loadsVoucherAndProductsInConfiguredOrder() throws Exception {
        var config = RedeemConfig.load(yaml("""
                products:
                  - minecraft:stone
                  - shulker_box
                """));
        assertEquals(Material.STONE, config.products().getFirst().material());
        assertEquals(Material.SHULKER_BOX, config.products().get(1).material());
        assertEquals("", config.products().getFirst().category());
        assertThrows(UnsupportedOperationException.class, () -> config.products().clear());
    }

    @Test
    void permitsEmptyCatalogWithoutVoucherMaterial() throws Exception {
        var config = RedeemConfig.load(yaml("products: []"));
        assertTrue(config.products().isEmpty());
    }

    @Test
    void oldConfigurationUsesDefaultSounds() throws Exception {
        var config = RedeemConfig.load(yaml("products: []"));
        assertEquals("minecraft:entity.experience_orb.pickup", config.successSound().name().asString());
        assertEquals("minecraft:entity.villager.no", config.failureSound().name().asString());
        assertEquals(1F, config.successSound().volume());
        assertEquals(1F, config.failureSound().pitch());
    }

    @Test
    void loadsIndependentSoundSettingsAndAllowsMutedVolume() throws Exception {
        var config = RedeemConfig.load(yaml("""
                products: []
                sounds:
                  success: {sound: minecraft:entity.player.levelup, volume: 0.5, pitch: 1.2}
                  failure: {sound: minecraft:block.anvil.land, volume: 0, pitch: 0.8}
                """));
        assertEquals("minecraft:entity.player.levelup", config.successSound().name().asString());
        assertEquals(0.5F, config.successSound().volume());
        assertEquals(1.2F, config.successSound().pitch());
        assertEquals("minecraft:block.anvil.land", config.failureSound().name().asString());
        assertEquals(0F, config.failureSound().volume());
        assertEquals(0.8F, config.failureSound().pitch());
    }

    @ParameterizedTest
    @ValueSource(strings = {"sounds: nope", "sounds.success: nope", "sounds.failure: nope",
            "sounds.success.sound: missing", "sounds.failure.sound: 'INVALID KEY'",
            "sounds.success.volume: -1", "sounds.failure.volume: loud", "sounds.success.volume: .inf",
            "sounds.success.pitch: 0", "sounds.failure.pitch: -1", "sounds.failure.pitch: .nan"})
    void rejectsInvalidSoundSettingsAtTheirConfigPath(String entry) throws Exception {
        int separator = entry.indexOf(": ");
        String path = entry.substring(0, separator);
        var config = yaml("products: []");
        var value = new YamlConfiguration();
        value.loadFromString("value: " + entry.substring(separator + 2));
        config.set(path, value.get("value"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> RedeemConfig.load(config))
                .getMessage().contains("config.yml: " + path));
    }

    @ParameterizedTest
    @ValueSource(strings = {"WATER", "AIR", "NOT_A_MATERIAL"})
    void rejectsProductsThatCannotBeGivenAsItems(String material) throws Exception {
        var config = yaml("products:\n  - " + material);
        var error = assertThrows(IllegalArgumentException.class, () -> RedeemConfig.load(config));
        assertTrue(error.getMessage().contains("config.yml: products[0]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"voucher: {material: WATER}\nproducts: []", "voucher: {material: AIR}\nproducts: []", "voucher: {}\nproducts: []"})
    void ignoresObsoleteVoucherMaterial(String source) throws Exception {
        var config = yaml(source);
        assertDoesNotThrow(() -> RedeemConfig.load(config));
    }

    @Test
    void rejectsDuplicateProductIdsAtSecondOccurrence() throws Exception {
        var config = yaml("""
                voucher: {material: PAPER}
                products:
                  - minecraft:stone
                  - STONE
                """);
        var error = assertThrows(IllegalArgumentException.class, () -> RedeemConfig.load(config));
        assertTrue(error.getMessage().contains("config.yml: products[1]"));
    }

    @Test
    void rejectsMissingOrWronglyTypedFieldsAtTheirLocation() throws Exception {
        var config = yaml("products:\n  - {id: stone, material: STONE}");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> RedeemConfig.load(config))
                .getMessage().contains("config.yml: products[0]"));
        config.set("products", "not-a-list");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> RedeemConfig.load(config))
                .getMessage().contains("config.yml: products"));
    }

    @Test
    void rejectsInvalidVoucherAppearanceAtItsConfigPath() throws Exception {
        var config = yaml("voucher: {material: PAPER}\nproducts: []");
        config.set("voucher.name", 123);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> RedeemConfig.load(config))
                .getMessage().contains("config.yml: voucher.name"));
        config.set("voucher.name", "测试券");
        config.set("voucher.lore", java.util.List.of("line", 123));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> RedeemConfig.load(config))
                .getMessage().contains("config.yml: voucher.lore[1]"));
        config.set("voucher.lore", "not-a-list");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> RedeemConfig.load(config))
                .getMessage().contains("config.yml: voucher.lore"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"voucher.name", "voucher.lore"})
    void requiresVoucherAppearanceInConfig(String path) throws Exception {
        var config = yaml("voucher: {material: PAPER}\nproducts: []");
        config.set(path, null);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> RedeemConfig.load(config))
                .getMessage().contains("config.yml: " + path));
    }

    @Test
    void bundledConfigurationIsValid() {
        try (var stream = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(stream);
            var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            var config = RedeemConfig.load(yaml);
            assertFalse(yaml.contains("voucher.material"));
            assertEquals(10, config.categories().size());
            assertEquals(427, config.products().size());
            assertEquals(java.util.Map.of("wood", 40L, "stone", 39L, "masonry", 36L, "color", 48L,
                    "glass", 35L, "terrain", 38L, "plants", 53L, "aquatic", 35L, "decor", 52L, "painting", 51L),
                    config.products().stream().collect(java.util.stream.Collectors.groupingBy(
                            RedeemProduct::category, java.util.stream.Collectors.counting())));
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void loadsCategoriesNonBlockItemsAndDistinctPaintingVariants() throws Exception {
        var config = RedeemConfig.load(yaml("""
                categories:
                  decor: 装饰
                  painting: 画作
                products:
                  - {material: item_frame, category: decor}
                  - {material: painting, category: painting, painting-variant: minecraft:earth}
                  - {material: painting, category: painting, painting-variant: minecraft:wind}
                """));
        assertEquals(java.util.List.of("decor", "painting"), java.util.List.copyOf(config.categories().keySet()));
        assertEquals(Material.ITEM_FRAME, config.products().getFirst().material());
        var earth = config.products().get(1);
        assertEquals("painting", earth.category());
        assertEquals(Art.EARTH, earth.paintingVariant());
        assertEquals(Art.EARTH.title(), earth.name());
        assertEquals(Art.EARTH, earth.createItem().getData(DataComponentTypes.PAINTING_VARIANT));
        assertEquals(Art.WIND, config.products().get(2).createItem().getData(DataComponentTypes.PAINTING_VARIANT));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{material: stone, category: missing}",
            "{material: stone, category: decor, painting-variant: earth}",
            "{material: painting, category: decor, painting-variant: missing}",
            "{material: painting, category: decor, painting-variant: 'INVALID KEY'}"
    })
    void rejectsUnknownCategoryOrInvalidPaintingVariant(String entry) throws Exception {
        var config = yaml("categories: {decor: 装饰}\nproducts:\n  - " + entry);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> RedeemConfig.load(config))
                .getMessage().contains("config.yml: products[0]."));
    }

    @Test
    void rejectsSamePaintingInDifferentCategories() throws Exception {
        var config = yaml("""
                categories: {first: 第一类, second: 第二类}
                products:
                  - {material: painting, category: first, painting-variant: earth}
                  - {material: minecraft:painting, category: second, painting-variant: minecraft:earth}
                """);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> RedeemConfig.load(config))
                .getMessage().contains("config.yml: products[1]"));
    }

    private static YamlConfiguration yaml(String source) throws Exception {
        var configuration = new YamlConfiguration();
        configuration.loadFromString(source);
        configuration.set("voucher.name", "测试券");
        configuration.set("voucher.lore", java.util.List.of("测试描述"));
        return configuration;
    }
}
