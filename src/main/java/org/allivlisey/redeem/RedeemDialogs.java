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
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class RedeemDialogs {
    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
        .uses(1).lifetime(Duration.ofMinutes(10)).build();

    private final JavaPlugin plugin;
    private final RedeemConfig config;
    private final Messages messages;
    private final RedeemService service;
    private boolean active = true;

    public RedeemDialogs(JavaPlugin plugin, RedeemConfig config, Messages messages, RedeemService service) {
        this.plugin = plugin;
        this.config = config;
        this.messages = messages;
        this.service = service;
    }

    void invalidate() {
        active = false;
    }

    public void openCategories(Player player) {
        if (!canUse(player)) return;
        List<ActionButton> actions = new ArrayList<>();
        for (var category : config.categories().entrySet()) {
            if (config.products().stream().anyMatch(product -> product.category().equals(category.getKey()))) {
                actions.add(button(player, Component.text(category.getValue()),
                    (actor, response) -> openCatalog(actor, category.getKey())));
            }
        }
        if (config.products().stream().anyMatch(product -> product.category().isEmpty())) {
            actions.add(button(player, messages.text("dialog.catalog-title"),
                (actor, response) -> openCatalog(actor, "")));
        }
        ActionButton close = button(player, messages.text("dialog.close"), (actor, response) -> actor.closeDialog());
        player.showDialog(Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(messages.text("dialog.catalog-title"))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(actions.isEmpty() ? DialogBody.plainMessage(messages.text("dialog.catalog-empty"))
                    : DialogBody.plainMessage(messages.text("dialog.category-hint"), rowWidth(actions, 2)))).build())
            .type(actions.isEmpty() ? DialogType.notice(close) : DialogType.multiAction(actions, close, 2))));
    }

    private void openCatalog(Player player, String category) {
        List<ActionButton> actions = new ArrayList<>();
        for (RedeemProduct product : config.products()) {
            if (!product.category().equals(category)) continue;
            Component tooltip = product.name();
            if (product.paintingVariant() != null) {
                tooltip = tooltip.appendNewline().append(Component.text(product.paintingVariant().getKey().asString()));
            }
            actions.add(button(player, product.icon(), tooltip, 20,
                (actor, response) -> openProduct(actor, product)));
        }
        ActionButton back = button(player, messages.text("dialog.back"), (actor, response) -> openCategories(actor));
        player.showDialog(Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(category.isEmpty() ? messages.text("dialog.catalog-title") : Component.text(config.categories().get(category)))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(DialogBody.plainMessage(messages.text("dialog.catalog-hint"), rowWidth(actions, 9)))).build())
            .type(DialogType.multiAction(actions, back, 9))));
    }

    private static int rowWidth(List<ActionButton> actions, int columns) {
        int count = Math.min(actions.size(), columns);
        // The 26.2 client places 2 pixels between dialog buttons.
        return count * actions.getFirst().width() + (count - 1) * 2;
    }

    private void openProduct(Player player, RedeemProduct product) {
        openProduct(player, product, null);
    }

    private void openProduct(Player player, RedeemProduct product, Component error) {
        List<DialogBody> body = new ArrayList<>();
        if (error != null) body.add(DialogBody.plainMessage(error));
        body.add(DialogBody.plainMessage(messages.text("dialog.balance",
            Map.of("amount", Integer.toString(Vouchers.count(player.getInventory()))))));
        body.add(DialogBody.item(product.createItem(), null, true, true, 32, 32));
        body.add(DialogBody.plainMessage(messages.text("dialog.rate")));
        player.showDialog(Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(messages.textComponents("dialog.redeem-title",
                Map.of("product", product.name())))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(body)
                .inputs(List.of(DialogInput.numberRange("amount", messages.text("dialog.amount"), 1F, 64F)
                    .initial(1F).step(1F).build()))
                .build())
            .type(DialogType.multiAction(List.of(
                button(player, messages.text("dialog.redeem"), (actor, response) -> redeem(actor, product, response))),
                button(player, messages.text("dialog.back"), (actor, response) -> openCatalog(actor, product.category())), 1))));
    }

    private void redeem(Player player, RedeemProduct product, DialogResponseView response) {
        Float value = response.getFloat("amount");
        if (value == null || !Float.isFinite(value) || value < 1 || value > 64 || value != Math.floor(value)) {
            openProduct(player, product, messages.text("dialog.invalid-amount"));
            player.playSound(config.failureSound());
            return;
        }
        int amount = value.intValue();
        boolean redeemed = service.redeem(player, product, amount);
        openProduct(player, product, redeemed ? null : messages.textComponents("dialog.insufficient", Map.of(
            "product", product.name(), "amount", Component.text(amount),
            "count", Component.text(Vouchers.count(player.getInventory())))));
        player.playSound(redeemed ? config.successSound() : config.failureSound());
    }

    private ActionButton button(Player owner, Component label, BiConsumer<Player, DialogResponseView> action) {
        return button(owner, label, null, 150, action);
    }

    private ActionButton button(Player owner, Component label, Component tooltip, int width, BiConsumer<Player, DialogResponseView> action) {
        UUID ownerId = owner.getUniqueId();
        return ActionButton.create(label, tooltip, width, DialogAction.customClick((response, audience) -> {
            if (!plugin.isEnabled() || !(audience instanceof Player player)
                    || !ownerId.equals(player.getUniqueId()) || !canUse(player)) return;
            action.accept(player, response);
        }, CALLBACK_OPTIONS));
    }

    private boolean canUse(Player player) {
        if (!active) {
            player.closeDialog();
            return false;
        }
        if (player.hasPermission("tianjiredeem.use")) return true;
        player.closeDialog();
        player.sendMessage(messages.text("command.no-permission"));
        return false;
    }
}
