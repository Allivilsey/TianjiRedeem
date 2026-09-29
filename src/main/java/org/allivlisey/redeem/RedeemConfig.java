package org.allivlisey.redeem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Art;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record RedeemConfig(Component voucherName,
                           List<Component> voucherLore, Map<String, String> categories, List<RedeemProduct> products,
                           Sound successSound, Sound failureSound) {
    public RedeemConfig {
        voucherLore = List.copyOf(voucherLore);
        categories = Collections.unmodifiableMap(new LinkedHashMap<>(categories));
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
        var categories = new LinkedHashMap<String, String>();
        if (config.contains("categories")) {
            ConfigurationSection section = config.getConfigurationSection("categories");
            if (section == null) throw invalid("categories");
            for (String id : section.getKeys(false)) {
                categories.put(id, string(section.get(id), "categories." + id));
            }
        }
        Object configuredProducts = config.get("products");
        if (!(configuredProducts instanceof List<?> entries)) throw invalid("products");
        var products = new ArrayList<RedeemProduct>();
        var identities = new HashSet<String>();
        for (int index = 0; index < entries.size(); index++) {
            String path = "products[" + index + "]";
            Object entry = entries.get(index);
            String category = "";
            Art variant = null;
            Material material;
            if (entry instanceof Map<?, ?> fields) {
                category = string(fields.get("category"), path + ".category");
                if (!categories.containsKey(category)) throw invalid(path + ".category");
                material = material(fields.get("material"), path + ".material");
                if (fields.containsKey("painting-variant")) {
                    String variantPath = path + ".painting-variant";
                    NamespacedKey key = NamespacedKey.fromString(string(fields.get("painting-variant"), variantPath));
                    variant = key == null ? null : Registry.ART.get(key);
                    if (material != Material.PAINTING || variant == null) throw invalid(variantPath);
                }
            } else {
                material = material(entry, path);
            }
            if (!identities.add(material.name() + ":" + (variant == null ? "" : variant.getKey()))) throw invalid(path);
            products.add(new RedeemProduct(category, material, variant));
        }
        if (config.contains("sounds") && !config.isConfigurationSection("sounds")) throw invalid("sounds");
        return new RedeemConfig(voucherName, voucherLore, categories, products,
                sound(config, "sounds.success", "minecraft:entity.experience_orb.pickup"),
                sound(config, "sounds.failure", "minecraft:entity.villager.no"));
    }

    private static Sound sound(ConfigurationSection config, String path, String defaultName) {
        if (config.contains(path) && !config.isConfigurationSection(path)) throw invalid(path);
        NamespacedKey key = NamespacedKey.fromString(string(config.get(path + ".sound", defaultName), path + ".sound"));
        if (key == null || Registry.SOUND_EVENT.get(key) == null) throw invalid(path + ".sound");
        float volume = soundNumber(config, path + ".volume");
        float pitch = soundNumber(config, path + ".pitch");
        if (volume < 0) throw invalid(path + ".volume");
        if (pitch <= 0) throw invalid(path + ".pitch");
        return Sound.sound(key, Sound.Source.MASTER, volume, pitch);
    }

    private static float soundNumber(ConfigurationSection config, String path) {
        if (!(config.get(path, 1.0) instanceof Number number) || !Float.isFinite(number.floatValue())) throw invalid(path);
        return number.floatValue();
    }

    private static Material material(Object value, String path) {
        Material material = Material.matchMaterial(string(value, path));
        if (material == null || material.isAir() || !material.isItem()) throw invalid(path);
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
