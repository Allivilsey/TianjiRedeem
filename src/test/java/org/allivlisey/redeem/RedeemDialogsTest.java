package org.allivlisey.redeem;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.RegistryBuilderFactory;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.DialogInstancesProvider;
import io.papermc.paper.registry.data.dialog.DialogRegistryEntry;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.input.NumberRangeDialogInput;
import io.papermc.paper.registry.data.dialog.type.MultiActionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Art;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedeemDialogsTest {
    private static final RedeemProduct STONE = new RedeemProduct("stone", Material.STONE, null);
    private final List<ActionButton> buttons = new ArrayList<>();
    private final Map<DialogAction, DialogActionCallback> callbacks = new IdentityHashMap<>();
    private DialogInstancesProvider provider;
    private MockedStatic<DialogInstancesProvider> providerStatic;
    private MockedStatic<Dialog> dialogStatic;
    private NumberRangeDialogInput.Builder amountInput;
    private Player player;
    private JavaPlugin plugin;
    private Messages messages;
    private RedeemService service;
    private ServerMock server;
    private RedeemDialogs dialogs;
    private Inventory openedInventory;
    private InventoryView view;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        server = MockBukkit.mock();
        plugin = mock(JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission("tianjiredeem.use")).thenReturn(true);
        when(player.isOnline()).thenReturn(true);
        view = mock(InventoryView.class);
        when(player.getOpenInventory()).thenReturn(view);
        when(player.openInventory(any(Inventory.class))).thenAnswer(call -> {
            openedInventory = call.getArgument(0);
            return view;
        });
        doAnswer(call -> { openedInventory = null; return null; }).when(player).closeInventory();
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(view.getTopInventory()).thenAnswer(call -> openedInventory == null ? inventory : openedInventory);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);
        when(player.getInventory()).thenReturn(inventory);
        messages = mock(Messages.class);
        when(messages.text(anyString())).thenAnswer(call -> Component.text(call.getArgument(0, String.class)));
        when(messages.text(anyString(), anyMap())).thenAnswer(call -> Component.text(call.getArgument(0, String.class)));
        when(messages.textComponents(anyString(), anyMap())).thenAnswer(call -> Component.text(call.getArgument(0, String.class)));
        service = mock(RedeemService.class);

        // MockBukkit has no dynamic DialogInstancesProvider. Capture only the Paper boundary.
        provider = mock(DialogInstancesProvider.class, RETURNS_DEEP_STUBS);
        providerStatic = mockStatic(DialogInstancesProvider.class);
        providerStatic.when(DialogInstancesProvider::instance).thenReturn(provider);
        when(provider.dialogBaseBuilder(any())).thenAnswer(call -> {
            var builder = mock(DialogBase.Builder.class, RETURNS_SELF);
            when(builder.build()).thenReturn(mock(DialogBase.class));
            return builder;
        });
        when(provider.actionButtonBuilder(any())).thenAnswer(call -> {
            var builder = mock(ActionButton.Builder.class, RETURNS_SELF);
            var button = mock(ActionButton.class);
            when(button.label()).thenReturn(call.getArgument(0));
            when(builder.action(any())).thenAnswer(action -> {
                when(button.action()).thenReturn(action.getArgument(0));
                return builder;
            });
            when(builder.build()).thenReturn(button);
            buttons.add(button);
            return builder;
        });
        when(provider.register(any(), any())).thenAnswer(call -> {
            var action = mock(DialogAction.CustomClickAction.class);
            callbacks.put(action, call.getArgument(0));
            assertEquals(1, call.getArgument(1, ClickCallback.Options.class).uses());
            return action;
        });
        when(provider.multiAction(anyList())).thenAnswer(call -> {
            assertFalse(call.getArgument(0, List.class).isEmpty(), "Paper rejects empty multiAction dialogs");
            var builder = mock(MultiActionType.Builder.class, RETURNS_SELF);
            when(builder.build()).thenReturn(mock(MultiActionType.class));
            return builder;
        });
        when(provider.numberRangeBuilder(anyString(), any(), anyFloat(), anyFloat())).thenAnswer(call -> {
            var builder = mock(NumberRangeDialogInput.Builder.class, RETURNS_SELF);
            when(builder.build()).thenReturn(mock(NumberRangeDialogInput.class));
            amountInput = builder;
            return builder;
        });
        dialogStatic = mockStatic(Dialog.class);
        dialogStatic.when(() -> Dialog.create(any())).thenAnswer(call -> {
            Consumer<RegistryBuilderFactory<Dialog, DialogRegistryEntry.Builder>> consumer = call.getArgument(0);
            RegistryBuilderFactory<Dialog, DialogRegistryEntry.Builder> factory = mock(RegistryBuilderFactory.class);
            when(factory.empty()).thenReturn(mock(DialogRegistryEntry.Builder.class, RETURNS_SELF));
            consumer.accept(factory);
            return mock(Dialog.class);
        });
    }

    @AfterEach
    void tearDown() {
        if (dialogStatic != null) dialogStatic.close();
        if (providerStatic != null) providerStatic.close();
        MockBukkit.unmock();
    }

    @Test
    void categoryMenuFiltersProductsAndBackReturnsToCategories() {
        dialogs = new RedeemDialogs(plugin, Map.of("stone", "石材", "wood", "木材", "empty", "空分类"),
            List.of(STONE, new RedeemProduct("wood", Material.OAK_LOG, null)), messages, service);
        dialogs.openCategories(player);
        assertTrue(hasButton("石材"));
        assertTrue(hasButton("木材"));
        assertFalse(hasButton("空分类"));
        assertNull(openedInventory);
        click("木材", null, player);
        assertEquals(9, openedInventory.getSize());
        assertEquals(Material.OAK_LOG, openedInventory.getItem(0).getType());
        assertNull(openedInventory.getItem(1));
        clickSlot(8);
        assertNull(openedInventory);
        assertEquals(2, buttons.stream().filter(button -> label(button).equals("木材")).count());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 8, 9, 35, 45, 48, 51, 53})
    void gridHasNineColumnsAndEnoughRowsForEveryChoice(int count) {
        openCatalog(java.util.Collections.nCopies(count, STONE));
        assertEquals(((count + 1 + 8) / 9) * 9, openedInventory.getSize());
        for (int slot = 0; slot < count; slot++) assertEquals(Material.STONE, openedInventory.getItem(slot).getType());
        assertEquals(Material.BARRIER, openedInventory.getItem(openedInventory.getSize() - 1).getType());
        verify(messages, never()).text("dialog.next");
        verify(messages, never()).text("dialog.previous");
    }

    @Test
    void largeCustomCategoriesKeepAllItemsReachable() {
        var choices = new ArrayList<>(java.util.Collections.nCopies(54, STONE));
        choices.set(53, new RedeemProduct("stone", Material.SHULKER_BOX, null));
        openCatalog(choices);
        assertEquals(54, openedInventory.getSize());
        clickSlot(52);
        assertEquals(18, openedInventory.getSize());
        assertEquals(Material.SHULKER_BOX, openedInventory.getItem(8).getType());
        clickSlot(15);
        assertEquals(54, openedInventory.getSize());
        assertEquals(Material.STONE, openedInventory.getItem(0).getType());
    }

    @Test
    void legacyProductsRemainAvailableUnderCatalogTitle() {
        dialogs = new RedeemDialogs(plugin, Map.of(), List.of(new RedeemProduct("", Material.STONE, null)), messages, service);
        dialogs.openCategories(player);
        click("dialog.catalog-title", null, player);
        assertEquals(Material.STONE, openedInventory.getItem(0).getType());
    }

    @Test
    void menuItemsCannotBeTakenOrChangedByBottomClicksOrDrags() {
        openCatalog(List.of(STONE));
        Inventory current = openedInventory;
        InventoryClickEvent bottomClick = inventoryClick(current.getSize());
        dialogs.onClick(bottomClick);
        verify(bottomClick).setCancelled(true);
        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getView()).thenReturn(view);
        dialogs.onDrag(drag);
        verify(drag).setCancelled(true);
        server.getScheduler().performOneTick();
        assertSame(current, openedInventory);
        assertNull(amountInput);
    }

    @Test
    void closingMenuBeforeScheduledSelectionPreventsReopening() {
        openCatalog(List.of(STONE));
        dialogs.onClick(inventoryClick(0));
        player.closeInventory();
        server.getScheduler().performOneTick();
        assertNull(amountInput);
    }

    @Test
    void revokingPermissionBlocksInventorySelection() {
        openCatalog(List.of(STONE));
        when(player.hasPermission("tianjiredeem.use")).thenReturn(false);
        clickSlot(0);
        assertNull(amountInput);
        verify(messages).text("command.no-permission");
    }

    @Test
    void selectionShowsItemPreviewAndOneToSixtyFourInput() {
        openProduct();
        verify(provider).itemDialogBodyBuilder(argThat(item -> item.getType() == Material.STONE));
        verify(provider).numberRangeBuilder(eq("amount"), any(), eq(1F), eq(64F));
        verify(amountInput).initial(1F);
        verify(amountInput).step(1F);
        verify(messages).text("dialog.balance", Map.of("amount", "0"));
        verify(messages).textComponents("dialog.redeem-title",
            Map.of("product", Component.translatable(Material.STONE.translationKey())));
    }

    @Test
    void paintingSelectionKeepsItsVariantInPreviewAndRedemption() {
        var painting = new RedeemProduct("stone", Material.PAINTING, Art.EARTH);
        openCatalog(List.of(painting));
        clickSlot(0);
        verify(provider).itemDialogBodyBuilder(argThat(item -> item.getType() == Material.PAINTING
            && item.getData(DataComponentTypes.PAINTING_VARIANT) == Art.EARTH));
        click("dialog.redeem", 1F, player);
        verify(service).redeem(player, painting, 1);
    }

    @Test
    void shutdownClosesCatalogsAndLeavesOtherInventoriesOpen() {
        openCatalog(List.of(STONE));
        var viewer = server.addPlayer();
        viewer.openInventory(openedInventory);
        var other = server.addPlayer();
        Inventory unrelated = server.createInventory(null, 9);
        other.openInventory(unrelated);
        dialogs.close();
        assertNotSame(openedInventory, viewer.getOpenInventory().getTopInventory());
        assertSame(unrelated, other.getOpenInventory().getTopInventory());
    }

    @Test
    void redeemShowsSuccessAndContinueOpensFreshProductPage() {
        when(service.redeem(player, STONE, 64)).thenReturn(true);
        openProduct();
        click("dialog.redeem", 64F, player);
        verify(service).redeem(player, STONE, 64);
        verify(messages).textComponents("dialog.success", Map.of("product", Component.translatable(Material.STONE.translationKey()),
            "amount", Component.text(64), "count", Component.text(4096)));
        click("dialog.continue", null, player);
        verify(provider, times(2)).numberRangeBuilder(eq("amount"), any(), eq(1F), eq(64F));
        click("dialog.back", null, player);
        assertEquals(Material.STONE, openedInventory.getItem(0).getType());
    }

    @Test
    void insufficientVouchersShowsResultWithoutClaimingSuccess() {
        openProduct();
        click("dialog.redeem", 1F, player);
        verify(service).redeem(player, STONE, 1);
        verify(messages).textComponents(eq("dialog.insufficient"), anyMap());
        verify(messages, never()).textComponents(eq("dialog.success"), anyMap());
        assertTrue(hasButton("dialog.continue"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(floats = {0F, -1F, 65F, 1.5F, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    void malformedAmountsCannotReachRedemption(Float value) {
        openProduct();
        click("dialog.redeem", value, player);
        verifyNoInteractions(service);
        verify(messages).text("dialog.invalid-amount");
    }

    @Test
    void otherPlayerCannotUseTheOwnersCallback() {
        openProduct();
        Player stranger = mock(Player.class);
        when(stranger.getUniqueId()).thenReturn(UUID.randomUUID());
        when(stranger.hasPermission("tianjiredeem.use")).thenReturn(true);
        click("dialog.redeem", 1F, stranger);
        verifyNoInteractions(service);
        verify(stranger, never()).showDialog(any());
    }

    @Test
    void revokingPermissionBlocksPreviouslyOpenedButtons() {
        openProduct();
        when(player.hasPermission("tianjiredeem.use")).thenReturn(false);
        click("dialog.redeem", 1F, player);
        verifyNoInteractions(service);
        verify(player, atLeastOnce()).closeDialog();
    }

    @Test
    void disabledPluginCallbacksCannotRedeem() {
        openProduct();
        when(plugin.isEnabled()).thenReturn(false);
        click("dialog.redeem", 1F, player);
        verifyNoInteractions(service);
    }

    @Test
    void emptyCatalogCanBeClosed() {
        new RedeemDialogs(plugin, Map.of(), List.of(), messages, service).openCategories(player);
        verify(messages).text("dialog.catalog-empty");
        click("dialog.close", null, player);
        verify(player).closeDialog();
    }

    private void openProduct() {
        openCatalog(List.of(STONE));
        clickSlot(0);
    }

    private void openCatalog(List<RedeemProduct> products) {
        dialogs = new RedeemDialogs(plugin, Map.of("stone", "石材"), products, messages, service);
        dialogs.openCategories(player);
        click("石材", null, player);
    }

    private void clickSlot(int slot) {
        InventoryClickEvent event = inventoryClick(slot);
        dialogs.onClick(event);
        verify(event).setCancelled(true);
        server.getScheduler().performOneTick();
    }

    private InventoryClickEvent inventoryClick(int slot) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getView()).thenReturn(view);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(slot);
        return event;
    }

    private void click(String label, Float amount, Player actor) {
        var button = buttons.reversed().stream().filter(entry -> label(entry).equals(label)).findFirst().orElseThrow();
        DialogResponseView response = mock(DialogResponseView.class);
        when(response.getFloat("amount")).thenReturn(amount);
        callbacks.get(button.action()).accept(response, actor);
    }

    private boolean hasButton(String label) {
        return buttons.stream().anyMatch(button -> label(button).equals(label));
    }

    private String label(ActionButton button) {
        if (button.label() instanceof net.kyori.adventure.text.TranslatableComponent translated) {
            return translated.key();
        }
        return PlainTextComponentSerializer.plainText().serialize(button.label());
    }
}
