package org.tianjiserver.redeem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public record RedeemConfig(Component voucherName,
                           List<Component> voucherLore, List<Material> products) {
    public RedeemConfig {
        voucherLore = List.copyOf(voucherLore);
        products = List.copyOf(products);
    }

    public static RedeemConfig load(ConfigurationSection config) {
        var legacy = LegacyComponentSerializer.legacyAmpersand();
        if (!(config.get("voucher.name") instanceof String name)) throw invalid("voucher.name");
        Component voucherName = legacy.deserialize(name);
        if (!(config.get("voucher.lore") instanceof List<?> loreEntries)) throw invalid("voucher.lore");
        var voucherLore = new ArrayList<Component>();
        for (int index = 0; index < loreEntries.size(); index++) {
            if (!(loreEntries.get(index) instanceof String line)) throw invalid("voucher.lore[" + index + "]");
            voucherLore.add(legacy.deserialize(line));
        }
        Object configuredProducts = config.get("products");
        if (!(configuredProducts instanceof List<?> entries)) throw invalid("products");
        var products = new ArrayList<Material>();
        var materials = new HashSet<Material>();
        for (int index = 0; index < entries.size(); index++) {
            String path = "products[" + index + "]";
            Material material = material(entries.get(index), path);
            if (!materials.add(material)) throw invalid(path);
            products.add(material);
        }
        return new RedeemConfig(voucherName, voucherLore, products);
    }

    private static Material material(Object value, String path) {
        Material material = Material.matchMaterial(string(value, path));
        if (material == null || material.isAir() || !material.isItem() || !material.isBlock()) throw invalid(path);
        return material;
    }

    private static String string(Object value, String path) {
        if (!(value instanceof String text) || text.isBlank()) throw invalid(path);
        return text;
    }

    private static IllegalArgumentException invalid(String path) {
        return new IllegalArgumentException("config.yml: " + path);
    }
}
