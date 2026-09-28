package org.tianjiserver.redeem;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessagesTest {
    @Test
    void loadsDefaultVoucherAppearanceAndLegacyColoredText() {
        var config = defaults();
        config.set("dialog.success", "&a使用 {amount} 张券兑换 {count} 个{product}。");
        var messages = Messages.load(config);
        assertEquals("[建材兑换券]", messages.plain("voucher.name", Map.of()));
        assertEquals("输入命令/tianjiredeem打开兑换界面", plain(messages.lines("voucher.lore").getFirst()));
        var replacements = Map.of("amount", "2", "count", "128", "product", "石头");
        assertEquals("使用 2 张券兑换 128 个石头。", messages.plain("dialog.success", replacements));
        assertEquals(NamedTextColor.GREEN, messages.text("dialog.success", replacements).color());
    }

    @Test
    void acceptsConfiguredVoucherNameAndMultipleLoreLines() {
        var config = defaults();
        config.set("voucher.name", "&e测试券");
        config.set("voucher.lore", List.of("第一行", "&7第二行"));
        var messages = Messages.load(config);
        assertEquals("测试券", plain(messages.text("voucher.name")));
        assertEquals(List.of("第一行", "第二行"), messages.lines("voucher.lore").stream().map(MessagesTest::plain).toList());
    }

    @Test
    void rejectsMissingMessagesDuringStartupWithFileAndKey() {
        var config = defaults();
        config.set("dialog.redeem", null);
        var error = assertThrows(IllegalArgumentException.class, () -> Messages.load(config));
        assertTrue(error.getMessage().contains("messages.yml: dialog.redeem"));
    }

    @Test
    void rejectsWrongMessageTypesAndNonTextLoreEntries() {
        var config = defaults();
        config.set("command.given", 123);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Messages.load(config))
                .getMessage().contains("messages.yml: command.given"));
        var invalidLore = defaults();
        invalidLore.set("voucher.lore", List.of("line", 123));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> Messages.load(invalidLore))
                .getMessage().contains("messages.yml: voucher.lore[1]"));
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static YamlConfiguration defaults() {
        var stream = MessagesTest.class.getResourceAsStream("/messages.yml");
        assertNotNull(stream);
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
