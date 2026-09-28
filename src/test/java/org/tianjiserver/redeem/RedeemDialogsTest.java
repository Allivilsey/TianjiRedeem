package org.tianjiserver.redeem;

import io.papermc.paper.dialog.Dialog;
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
import org.bukkit.entity.Player;
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
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedeemDialogsTest {
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

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        MockBukkit.mock();
        plugin = mock(JavaPlugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission("tianjiredeem.use")).thenReturn(true);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);
        when(player.getInventory()).thenReturn(inventory);
        messages = mock(Messages.class);
        when(messages.text(anyString())).thenAnswer(call -> Component.text(call.getArgument(0, String.class)));
        when(messages.text(anyString(), anyMap())).thenAnswer(call -> Component.text(call.getArgument(0, String.class)));
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
    void catalogPaginatesTwelveProductsAndKeepsLastPageReachable() {
        var products = IntStream.range(0, 13)
            .mapToObj(i -> new Product("item" + i, "Item " + i, Material.STONE)).toList();
        new RedeemDialogs(plugin, products, messages, service).openCatalog(player, 0);
        assertEquals(12, buttons.stream().filter(button -> label(button).startsWith("Item ")).count());
        assertFalse(hasButton("dialog.previous"));
        click("dialog.next", null, player);
        assertTrue(hasButton("Item 12"));
        verify(messages).text("dialog.page", Map.of("page", "2", "pages", "2"));
        click("dialog.previous", null, player);
        verify(messages, times(2)).text("dialog.page", Map.of("page", "1", "pages", "2"));
    }

    @Test
    void selectionShowsItemPreviewAndOneToSixtyFourInput() {
        openProduct();
        verify(provider).itemDialogBodyBuilder(argThat(item -> item.getType() == Material.STONE));
        verify(provider).numberRangeBuilder(eq("amount"), any(), eq(1F), eq(64F));
        verify(amountInput).initial(1F);
        verify(amountInput).step(1F);
        verify(messages).text("dialog.balance", Map.of("amount", "0"));
    }

    @Test
    void redeemShowsSuccessAndContinueOpensFreshProductPage() {
        when(service.redeem(player, Material.STONE, 64)).thenReturn(true);
        openProduct();
        click("dialog.redeem", 64F, player);
        verify(service).redeem(player, Material.STONE, 64);
        verify(messages).text("dialog.success", Map.of("product", "Stone", "amount", "64", "count", "4096"));
        click("dialog.continue", null, player);
        verify(provider, times(2)).numberRangeBuilder(eq("amount"), any(), eq(1F), eq(64F));
        click("dialog.back", null, player);
        verify(messages, times(2)).text("dialog.catalog-title");
    }

    @Test
    void insufficientVouchersShowsResultWithoutClaimingSuccess() {
        openProduct();
        click("dialog.redeem", 1F, player);
        verify(service).redeem(player, Material.STONE, 1);
        verify(messages).text(eq("dialog.insufficient"), anyMap());
        verify(messages, never()).text(eq("dialog.success"), anyMap());
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
        verify(player).closeDialog();
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
        new RedeemDialogs(plugin, List.of(), messages, service).openCatalog(player, 0);
        verify(messages).text("dialog.catalog-empty");
        click("dialog.close", null, player);
        verify(player).closeDialog();
    }

    private void openProduct() {
        var dialogs = new RedeemDialogs(plugin,
            List.of(new Product("stone", "Stone", Material.STONE)), messages, service);
        dialogs.openCatalog(player, 0);
        click("Stone", null, player);
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
        return PlainTextComponentSerializer.plainText().serialize(button.label());
    }
}
