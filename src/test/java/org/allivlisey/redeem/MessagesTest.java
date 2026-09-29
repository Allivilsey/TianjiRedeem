package org.allivlisey.redeem;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessagesTest {
    @Test
    void preservesVanillaNameComponentsInsideMessages() {
        var messages = Messages.load(defaults());
        var product = net.kyori.adventure.text.Component.translatable("block.minecraft.stone");
        var title = messages.textComponents("dialog.redeem-title", Map.of("product", product));
        assertTrue(title.equals(product) || title.contains(product));
        assertFalse(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(title).contains("{product}"));
    }

    @Test
    void loadsLegacyColoredText() {
        var config = defaults();
        config.set("dialog.insufficient", "&c需要 {amount} 张券，当前 {count} 张。");
        var messages = Messages.load(config);
        var replacements = Map.of("amount", "2", "count", "1");
        assertEquals("需要 2 张券，当前 1 张。", messages.plain("dialog.insufficient", replacements));
        assertEquals(NamedTextColor.RED, messages.text("dialog.insufficient", replacements).color());
    }

    @Test
    void doesNotRequireVoucherSettingsInMessages() {
        var config = defaults();
        config.set("voucher", null);
        assertDoesNotThrow(() -> Messages.load(config));
    }

    @Test
    void rejectsMissingMessagesDuringStartupWithFileAndKey() {
        var config = defaults();
        config.set("dialog.redeem", null);
        var error = assertThrows(IllegalArgumentException.class, () -> Messages.load(config));
        assertTrue(error.getMessage().contains("messages.yml: dialog.redeem"));
    }

    @Test
    void rejectsWrongMessageTypes() {
        var config = defaults();
        config.set("command.given", 123);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Messages.load(config))
                .getMessage().contains("messages.yml: command.given"));
    }

    private static YamlConfiguration defaults() {
        var stream = MessagesTest.class.getResourceAsStream("/messages.yml");
        assertNotNull(stream);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
