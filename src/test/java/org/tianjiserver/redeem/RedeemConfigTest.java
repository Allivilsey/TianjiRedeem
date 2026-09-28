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
                products:
                  - minecraft:stone
                  - shulker_box
                """));
        assertEquals(Material.STONE, config.products().getFirst());
        assertEquals(Material.SHULKER_BOX, config.products().get(1));
        assertThrows(UnsupportedOperationException.class, () -> config.products().clear());
    }

    @Test
    void permitsEmptyCatalogWithoutVoucherMaterial() throws Exception {
        var config = RedeemConfig.load(yaml("products: []"));
        assertTrue(config.products().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DIAMOND_SWORD", "WATER", "AIR", "NOT_A_MATERIAL"})
    void rejectsProductsThatCannotBeGivenAsBlocks(String material) throws Exception {
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
