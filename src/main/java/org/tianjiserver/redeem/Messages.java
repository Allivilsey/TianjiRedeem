package org.tianjiserver.redeem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Messages {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final List<String> TEXT_KEYS = List.of(
            "voucher.name", "command.usage", "command.player-only", "command.no-permission",
            "command.invalid-amount", "command.player-required", "command.player-not-found",
            "command.given", "command.received", "dialog.catalog-title", "dialog.catalog-empty",
            "dialog.page", "dialog.previous", "dialog.next", "dialog.close", "dialog.redeem-title",
            "dialog.rate", "dialog.balance", "dialog.amount", "dialog.redeem", "dialog.back",
            "dialog.result-title", "dialog.success", "dialog.insufficient", "dialog.invalid-amount",
            "dialog.continue", "startup.invalid-config");

    private final Map<String, String> texts;
    private final List<Component> voucherLore;

    private Messages(Map<String, String> texts, List<Component> voucherLore) {
        this.texts = Map.copyOf(texts);
        this.voucherLore = List.copyOf(voucherLore);
    }

    public static Messages load(ConfigurationSection config) {
        var texts = new HashMap<String, String>();
        for (String key : TEXT_KEYS) {
            if (!(config.get(key) instanceof String value)) throw invalid(key);
            texts.put(key, value);
        }
        if (!(config.get("voucher.lore") instanceof List<?> entries)) throw invalid("voucher.lore");
        var lore = new ArrayList<Component>();
        for (int index = 0; index < entries.size(); index++) {
            if (!(entries.get(index) instanceof String line)) throw invalid("voucher.lore[" + index + "]");
            lore.add(LEGACY.deserialize(line));
        }
        return new Messages(texts, lore);
    }

    public Component text(String key) {
        return text(key, Map.of());
    }

    public Component text(String key, Map<String, String> replacements) {
        String message = texts.get(key);
        if (message == null) throw invalid(key);
        for (var entry : replacements.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return LEGACY.deserialize(message);
    }

    public List<Component> lines(String key) {
        if (!key.equals("voucher.lore")) throw invalid(key);
        return voucherLore;
    }

    public String plain(String key, Map<String, String> replacements) {
        return PlainTextComponentSerializer.plainText().serialize(text(key, replacements));
    }

    private static IllegalArgumentException invalid(String path) {
        return new IllegalArgumentException("messages.yml: " + path);
    }
}
