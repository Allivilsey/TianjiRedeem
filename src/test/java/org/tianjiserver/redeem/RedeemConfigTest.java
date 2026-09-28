package org.tianjiserver.redeem;

import org.bukkit.Material;
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
                voucher:
                  material: FIELD_MASONED_BANNER_PATTERN
                products:
                  - id: stone
                    name: 石头
                    material: STONE
                  - id: shulker
                    name: 潜影盒
                    material: SHULKER_BOX
                """));
        assertEquals(Material.FIELD_MASONED_BANNER_PATTERN, config.voucherMaterial());
        assertEquals(new Product("stone", "石头", Material.STONE), config.products().getFirst());
        assertEquals(Material.SHULKER_BOX, config.products().get(1).material());
        assertThrows(UnsupportedOperationException.class, () -> config.products().clear());
    }

    @Test
    void permitsEmptyCatalogAndNonBlockVoucherMaterial() throws Exception {
        var config = RedeemConfig.load(yaml("voucher: {material: PAPER}\nproducts: []"));
        assertEquals(Material.PAPER, config.voucherMaterial());
        assertTrue(config.products().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIAMOND_SWORD", "WATER", "AIR", "NOT_A_MATERIAL"})
    void rejectsProductsThatCannotBeGivenAsBlocks(String material) throws Exception {
        var config = yaml("voucher: {material: PAPER}\nproducts:\n  - {id: a, name: A, material: " + material + "}");
        var error = assertThrows(IllegalArgumentException.class, () -> RedeemConfig.load(config));
        assertTrue(error.getMessage().contains("config.yml: products[0].material"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"voucher: {material: WATER}\nproducts: []", "voucher: {material: AIR}\nproducts: []", "voucher: {}\nproducts: []"})
    void reportsVoucherMaterialPath(String source) throws Exception {
        var error = assertThrows(IllegalArgumentException.class, () -> RedeemConfig.load(yaml(source)));
        assertTrue(error.getMessage().contains("config.yml: voucher.material"));
    }

    @Test
    void rejectsDuplicateProductIdsAtSecondOccurrence() throws Exception {
        var config = yaml("""
                voucher: {material: PAPER}
                products:
                  - {id: stone, name: 石头, material: STONE}
                  - {id: stone, name: 草方块, material: GRASS_BLOCK}
                """);
        var error = assertThrows(IllegalArgumentException.class, () -> RedeemConfig.load(config));
        assertTrue(error.getMessage().contains("config.yml: products[1].id"));
    }

    @Test
    void rejectsMissingOrWronglyTypedFieldsAtTheirLocation() throws Exception {
        var config = yaml("voucher: {material: PAPER}\nproducts:\n  - {id: a, material: STONE}");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> RedeemConfig.load(config))
                .getMessage().contains("config.yml: products[0].name"));
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
            assertEquals(Material.FIELD_MASONED_BANNER_PATTERN, config.voucherMaterial());
            assertFalse(config.products().isEmpty());
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static YamlConfiguration yaml(String source) throws Exception {
        var configuration = new YamlConfiguration();
        configuration.loadFromString(source);
        configuration.set("voucher.name", "测试券");
        configuration.set("voucher.lore", java.util.List.of("测试描述"));
        return configuration;
    }
}
