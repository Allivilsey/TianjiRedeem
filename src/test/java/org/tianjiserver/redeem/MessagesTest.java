package org.tianjiserver.redeem;

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
        assertTrue(title.contains(product));
        assertFalse(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(title).contains("{product}"));
    }

    @Test
    void loadsLegacyColoredText() {
        var config = defaults();
        config.set("dialog.success", "&a使用 {amount} 张券兑换 {count} 个{product}。");
        var messages = Messages.load(config);
        var replacements = Map.of("amount", "2", "count", "128", "product", "石头");
        assertEquals("使用 2 张券兑换 128 个石头。", messages.plain("dialog.success", replacements));
        assertEquals(NamedTextColor.GREEN, messages.text("dialog.success", replacements).color());
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
