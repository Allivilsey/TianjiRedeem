package org.tianjiserver.redeem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Messages {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final List<String> TEXT_KEYS = List.of(
            "command.usage", "command.player-only", "command.no-permission",
            "command.invalid-amount", "command.player-required", "command.player-not-found",
            "command.given", "command.received", "dialog.catalog-title", "dialog.catalog-empty",
            "dialog.page", "dialog.previous", "dialog.next", "dialog.close", "dialog.redeem-title",
            "dialog.rate", "dialog.balance", "dialog.amount", "dialog.redeem", "dialog.back",
            "dialog.result-title", "dialog.success", "dialog.insufficient", "dialog.invalid-amount",
            "dialog.continue", "startup.invalid-config");

    private final Map<String, String> texts;

    private Messages(Map<String, String> texts) {
        this.texts = Map.copyOf(texts);
    }

    public static Messages load(ConfigurationSection config) {
        var texts = new HashMap<String, String>();
        for (String key : TEXT_KEYS) {
            if (!(config.get(key) instanceof String value)) throw invalid(key);
            texts.put(key, value);
        }
        return new Messages(texts);
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

    public String plain(String key, Map<String, String> replacements) {
        return PlainTextComponentSerializer.plainText().serialize(text(key, replacements));
    }

    private static IllegalArgumentException invalid(String path) {
        return new IllegalArgumentException("messages.yml: " + path);
    }
}
