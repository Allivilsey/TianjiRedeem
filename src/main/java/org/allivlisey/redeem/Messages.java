package org.allivlisey.redeem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Messages {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final List<String> TEXT_KEYS = List.of(
            "command.usage", "command.player-only", "command.no-permission",
            "command.invalid-amount", "command.player-required", "command.player-not-found",
            "command.given", "command.received", "command.failed", "command.reloaded", "command.reload-failed",
            "dialog.catalog-title", "dialog.catalog-empty",
            "dialog.category-hint", "dialog.catalog-hint", "dialog.close", "dialog.redeem-title",
            "dialog.rate", "dialog.balance", "dialog.amount", "dialog.redeem", "dialog.back",
            "dialog.insufficient", "dialog.invalid-amount", "startup.invalid-config");

    private final Map<String, String> texts;

    private Messages(Map<String, String> texts) {
        this.texts = Map.copyOf(texts);
    }

    public static Messages load(ConfigurationSection config) {
        var texts = new HashMap<String, String>();
        for (String key : TEXT_KEYS) {
            Object raw = config.get(key);
            if ((key.equals("dialog.category-hint") || key.equals("dialog.catalog-hint")) && raw instanceof List<?> lines) {
                var values = new ArrayList<String>();
                for (int i = 0; i < lines.size(); i++) {
                    if (!(lines.get(i) instanceof String line)) throw invalid(key + "[" + i + "]");
                    values.add(line);
                }
                raw = String.join("\n", values);
            }
            if (!(raw instanceof String value)) throw invalid(key);
            texts.put(key, value);
        }
        return new Messages(texts);
    }

    public Component text(String key) {
        return text(key, Map.of());
    }

    public Component text(String key, Map<String, String> replacements) {
        var components = new HashMap<String, Component>();
        replacements.forEach((name, value) -> components.put(name, Component.text(value)));
        return textComponents(key, components);
    }

    public Component textComponents(String key, Map<String, Component> replacements) {
        String message = texts.get(key);
        if (message == null) throw invalid(key);
        return LEGACY.deserialize(message).replaceText(builder -> builder.match("\\{([^{}]+)}")
                .replacement((match, original) -> replacements.getOrDefault(match.group(1), original.build())));
    }

    public String plain(String key, Map<String, String> replacements) {
        return PlainTextComponentSerializer.plainText().serialize(text(key, replacements));
    }

    private static IllegalArgumentException invalid(String path) {
        return new IllegalArgumentException("messages.yml: " + path);
    }
}
