package org.tianjiserver.redeem;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public record RedeemConfig(Material voucherMaterial, List<Product> products) {
    public RedeemConfig {
        products = List.copyOf(products);
    }

    public static RedeemConfig load(ConfigurationSection config) {
        Material voucher = material(config.get("voucher.material"), "voucher.material", false);
        Object configuredProducts = config.get("products");
        if (!(configuredProducts instanceof List<?> entries)) throw invalid("products");
        var products = new ArrayList<Product>();
        var ids = new HashSet<String>();
        for (int index = 0; index < entries.size(); index++) {
            String path = "products[" + index + "]";
            if (!(entries.get(index) instanceof Map<?, ?> entry)) throw invalid(path);
            String id = string(entry.get("id"), path + ".id");
            if (!ids.add(id)) throw invalid(path + ".id");
            String name = string(entry.get("name"), path + ".name");
            Material material = material(entry.get("material"), path + ".material", true);
            products.add(new Product(id, name, material));
        }
        return new RedeemConfig(voucher, products);
    }

    private static Material material(Object value, String path, boolean block) {
        Material material = Material.matchMaterial(string(value, path));
        if (material == null || material.isAir() || !material.isItem() || (block && !material.isBlock())) throw invalid(path);
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
