package org.allivlisey.redeem;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class RedeemDialogs {
    private static final int PAGE_SIZE = 12;
    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
        .uses(1).lifetime(Duration.ofMinutes(10)).build();

    private final JavaPlugin plugin;
    private final List<Material> products;
    private final Messages messages;
    private final RedeemService service;

    public RedeemDialogs(JavaPlugin plugin, List<Material> products, Messages messages, RedeemService service) {
        this.plugin = plugin;
        this.products = List.copyOf(products);
        this.messages = messages;
        this.service = service;
    }

    public void openCatalog(Player player, int page) {
        if (!canUse(player)) return;
        int pages = Math.max(1, (products.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.clamp(page, 0, pages - 1);
        List<ActionButton> actions = new ArrayList<>();
        int end = Math.min(products.size(), (current + 1) * PAGE_SIZE);
        for (int i = current * PAGE_SIZE; i < end; i++) {
            Material product = products.get(i);
            actions.add(button(player, Component.translatable(product.translationKey()),
                (actor, response) -> openProduct(actor, product, current)));
        }
        if (current > 0) {
            actions.add(button(player, messages.text("dialog.previous"),
                (actor, response) -> openCatalog(actor, current - 1)));
        }
        if (current + 1 < pages) {
            actions.add(button(player, messages.text("dialog.next"),
                (actor, response) -> openCatalog(actor, current + 1)));
        }
        List<DialogBody> body = List.of(DialogBody.plainMessage(products.isEmpty()
            ? messages.text("dialog.catalog-empty")
            : messages.text("dialog.page", Map.of("page", Integer.toString(current + 1), "pages", Integer.toString(pages)))));
        ActionButton close = button(player, messages.text("dialog.close"), (actor, response) -> actor.closeDialog());
        player.showDialog(Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(messages.text("dialog.catalog-title"))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(body).build())
            .type(actions.isEmpty() ? DialogType.notice(close) : DialogType.multiAction(actions, close, 2))));
    }

    private void openProduct(Player player, Material product, int page) {
        player.showDialog(Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(messages.textComponents("dialog.redeem-title",
                Map.of("product", Component.translatable(product.translationKey()))))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(
                    DialogBody.plainMessage(messages.text("dialog.balance",
                        Map.of("amount", Integer.toString(Vouchers.count(player.getInventory()))))),
                    DialogBody.item(new ItemStack(product), null, true, true, 32, 32),
                    DialogBody.plainMessage(messages.text("dialog.rate"))))
                .inputs(List.of(DialogInput.numberRange("amount", messages.text("dialog.amount"), 1F, 64F)
                    .initial(1F).step(1F).build()))
                .build())
            .type(DialogType.multiAction(List.of(
                button(player, messages.text("dialog.redeem"), (actor, response) -> redeem(actor, product, page, response))),
                button(player, messages.text("dialog.back"), (actor, response) -> openCatalog(actor, page)), 1))));
    }

    private void redeem(Player player, Material product, int page, DialogResponseView response) {
        Float value = response.getFloat("amount");
        if (value == null || !Float.isFinite(value) || value < 1 || value > 64 || value != Math.floor(value)) {
            openResult(player, product, page, messages.text("dialog.invalid-amount"));
            return;
        }
        int amount = value.intValue();
        boolean redeemed = service.redeem(player, product, amount);
        Component result = messages.textComponents(redeemed ? "dialog.success" : "dialog.insufficient", Map.of(
            "product", Component.translatable(product.translationKey()), "amount", Component.text(amount),
            "count", Component.text(redeemed ? 64 * amount : Vouchers.count(player.getInventory()))));
        openResult(player, product, page, result);
    }

    private void openResult(Player player, Material product, int page, Component result) {
        player.showDialog(Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(messages.text("dialog.result-title"))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(DialogBody.plainMessage(result))).build())
            .type(DialogType.multiAction(List.of(
                button(player, messages.text("dialog.continue"), (actor, response) -> openProduct(actor, product, page))),
                button(player, messages.text("dialog.back"), (actor, response) -> openCatalog(actor, page)), 1))));
    }

    private ActionButton button(Player owner, Component label, BiConsumer<Player, DialogResponseView> action) {
        UUID ownerId = owner.getUniqueId();
        return ActionButton.create(label, null, 150, DialogAction.customClick((response, audience) -> {
            if (!plugin.isEnabled() || !(audience instanceof Player player)
                    || !ownerId.equals(player.getUniqueId()) || !canUse(player)) return;
            action.accept(player, response);
        }, CALLBACK_OPTIONS));
    }

    private boolean canUse(Player player) {
        if (player.hasPermission("tianjiredeem.use")) return true;
        player.closeDialog();
        player.sendMessage(messages.text("command.no-permission"));
        return false;
    }
}
