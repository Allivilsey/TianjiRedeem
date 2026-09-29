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
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class RedeemDialogs implements Listener {
    private static final ClickCallback.Options CALLBACK_OPTIONS = ClickCallback.Options.builder()
        .uses(1).lifetime(Duration.ofMinutes(10)).build();

    private final JavaPlugin plugin;
    private final Map<String, String> categories;
    private final List<RedeemProduct> products;
    private final Messages messages;
    private final RedeemService service;

    public RedeemDialogs(JavaPlugin plugin, Map<String, String> categories, List<RedeemProduct> products,
                         Messages messages, RedeemService service) {
        this.plugin = plugin;
        this.categories = Collections.unmodifiableMap(new LinkedHashMap<>(categories));
        this.products = List.copyOf(products);
        this.messages = messages;
        this.service = service;
    }

    public void openCategories(Player player) {
        if (!canUse(player)) return;
        player.closeInventory();
        List<ActionButton> actions = new ArrayList<>();
        for (var category : categories.entrySet()) {
            if (products.stream().anyMatch(product -> product.category().equals(category.getKey()))) {
                actions.add(button(player, Component.text(category.getValue()),
                    (actor, response) -> openCatalog(actor, category.getKey(), 0)));
            }
        }
        if (products.stream().anyMatch(product -> product.category().isEmpty())) {
            actions.add(button(player, messages.text("dialog.catalog-title"),
                (actor, response) -> openCatalog(actor, "", 0)));
        }
        ActionButton close = button(player, messages.text("dialog.close"), (actor, response) -> actor.closeDialog());
        player.showDialog(Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(messages.text("dialog.catalog-title"))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(products.isEmpty() ? List.of(DialogBody.plainMessage(messages.text("dialog.catalog-empty"))) : List.of()).build())
            .type(actions.isEmpty() ? DialogType.notice(close) : DialogType.multiAction(actions, close, 2))));
    }

    private void openCatalog(Player player, String category, int page) {
        if (!canUse(player)) return;
        List<RedeemProduct> choices = products.stream().filter(product -> product.category().equals(category)).toList();
        int pages = choices.size() > 53 ? (choices.size() + 44) / 45 : 1;
        Catalog catalog = new Catalog(player.getUniqueId(), category, Math.clamp(page, 0, pages - 1), choices);
        for (int slot = 0; slot < catalog.itemCount(); slot++) {
            catalog.inventory.setItem(slot, choices.get(catalog.page * 45 + slot).createItem());
        }
        int size = catalog.inventory.getSize();
        catalog.inventory.setItem(size - 1, navigation(Material.BARRIER, "dialog.back"));
        if (catalog.page > 0) catalog.inventory.setItem(size - 3, navigation(Material.ARROW, "dialog.previous"));
        if (catalog.page + 1 < pages) catalog.inventory.setItem(size - 2, navigation(Material.ARROW, "dialog.next"));
        player.closeDialog();
        player.openInventory(catalog.inventory);
    }

    private ItemStack navigation(Material material, String key) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(messages.text(key)));
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Catalog catalog)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !catalog.owner.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= catalog.inventory.getSize() || catalog.inventory.getItem(slot) == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled() || !player.isOnline()
                    || player.getOpenInventory().getTopInventory().getHolder() != catalog) return;
            player.closeInventory();
            if (!canUse(player)) return;
            if (slot < catalog.itemCount()) {
                openProduct(player, catalog.products.get(catalog.page * 45 + slot), catalog.page);
            } else if (slot == catalog.inventory.getSize() - 1) {
                openCategories(player);
            } else {
                openCatalog(player, catalog.category, catalog.page + (slot == catalog.inventory.getSize() - 3 ? -1 : 1));
            }
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Catalog) event.setCancelled(true);
    }

    public void close() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Catalog) player.closeInventory();
        }
    }

    private final class Catalog implements InventoryHolder {
        private final UUID owner;
        private final String category;
        private final int page;
        private final List<RedeemProduct> products;
        private final Inventory inventory;

        private Catalog(UUID owner, String category, int page, List<RedeemProduct> products) {
            this.owner = owner;
            this.category = category;
            this.page = page;
            this.products = products;
            int controls = products.size() > 53 ? 3 : 1;
            int size = ((itemCount() + controls + 8) / 9) * 9;
            inventory = Bukkit.createInventory(this, size,
                category.isEmpty() ? messages.text("dialog.catalog-title") : Component.text(categories.get(category)));
        }

        private int itemCount() {
            return Math.min(products.size() - page * 45, products.size() > 53 ? 45 : 53);
        }

        @Override
        public Inventory getInventory() { return inventory; }
    }

    private void openProduct(Player player, RedeemProduct product, int page) {
        player.showDialog(Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(messages.textComponents("dialog.redeem-title",
                Map.of("product", product.name())))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(
                    DialogBody.plainMessage(messages.text("dialog.balance",
                        Map.of("amount", Integer.toString(Vouchers.count(player.getInventory()))))),
                    DialogBody.item(product.createItem(), null, true, true, 32, 32),
                    DialogBody.plainMessage(messages.text("dialog.rate"))))
                .inputs(List.of(DialogInput.numberRange("amount", messages.text("dialog.amount"), 1F, 64F)
                    .initial(1F).step(1F).build()))
                .build())
            .type(DialogType.multiAction(List.of(
                button(player, messages.text("dialog.redeem"), (actor, response) -> redeem(actor, product, page, response))),
                button(player, messages.text("dialog.back"), (actor, response) -> openCatalog(actor, product.category(), page)), 1))));
    }

    private void redeem(Player player, RedeemProduct product, int page, DialogResponseView response) {
        Float value = response.getFloat("amount");
        if (value == null || !Float.isFinite(value) || value < 1 || value > 64 || value != Math.floor(value)) {
            openResult(player, product, page, messages.text("dialog.invalid-amount"));
            return;
        }
        int amount = value.intValue();
        boolean redeemed = service.redeem(player, product, amount);
        Component result = messages.textComponents(redeemed ? "dialog.success" : "dialog.insufficient", Map.of(
            "product", product.name(), "amount", Component.text(amount),
            "count", Component.text(redeemed ? 64 * amount : Vouchers.count(player.getInventory()))));
        openResult(player, product, page, result);
    }

    private void openResult(Player player, RedeemProduct product, int page, Component result) {
        player.showDialog(Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(messages.text("dialog.result-title"))
                .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                .body(List.of(DialogBody.plainMessage(result))).build())
            .type(DialogType.multiAction(List.of(
                button(player, messages.text("dialog.continue"), (actor, response) -> openProduct(actor, product, page))),
                button(player, messages.text("dialog.back"), (actor, response) -> openCatalog(actor, product.category(), page)), 1))));
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
