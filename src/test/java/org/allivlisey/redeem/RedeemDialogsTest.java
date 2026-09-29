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
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.object.SpriteObjectContents;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Art;
import org.bukkit.configuration.file.YamlConfiguration;
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
import java.io.File;
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
    private static final Sound SUCCESS_SOUND = Sound.sound(Key.key("entity.player.levelup"), Sound.Source.MASTER, 0.5F, 1.2F);
    private static final Sound FAILURE_SOUND = Sound.sound(Key.key("block.anvil.land"), Sound.Source.MASTER, 0.3F, 0.8F);
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
    private RedeemDialogs dialogs;
    private MultiActionType shownActions;

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
            when(builder.tooltip(nullable(Component.class))).thenAnswer(tooltip -> {
                when(button.tooltip()).thenReturn(tooltip.getArgument(0));
                return builder;
            });
            when(builder.width(anyInt())).thenAnswer(width -> {
                when(button.width()).thenReturn(width.getArgument(0));
                return builder;
            });
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
            var type = mock(MultiActionType.class);
            when(type.actions()).thenReturn(call.getArgument(0));
            when(builder.columns(anyInt())).thenAnswer(columns -> {
                when(type.columns()).thenReturn(columns.getArgument(0));
                return builder;
            });
            when(builder.exitAction(any())).thenAnswer(exit -> {
                when(type.exitAction()).thenReturn(exit.getArgument(0));
                return builder;
            });
            when(builder.build()).thenAnswer(ignored -> {
                shownActions = type;
                return type;
            });
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
        dialogs = dialogs(Map.of("stone", "石材", "wood", "木材", "empty", "空分类"),
            List.of(STONE, new RedeemProduct("wood", Material.OAK_LOG, null)));
        dialogs.openCategories(player);
        assertTrue(hasButton("石材"));
        verify(provider).plainMessageDialogBody(Component.text("dialog.category-hint"), 302);
        assertTrue(hasButton("木材"));
        assertFalse(hasButton("空分类"));
        click("木材", null, player);
        assertEquals(1, shownActions.actions().size());
        assertEquals(new RedeemProduct("wood", Material.OAK_LOG, null).icon(), shownActions.actions().getFirst().label());
        click(shownActions.actions().getFirst(), null, player);
        click("dialog.back", null, player);
        assertEquals(1, shownActions.actions().size());
        assertEquals(new RedeemProduct("wood", Material.OAK_LOG, null).icon(), shownActions.actions().getFirst().label());
        assertEquals("dialog.back", label(shownActions.exitAction()));
        assertEquals(150, shownActions.exitAction().width());
        click(shownActions.exitAction(), null, player);
        assertEquals(2, buttons.stream().filter(button -> label(button).equals("木材")).count());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 8, 9, 35, 45, 48, 51, 53, 54, 100})
    void gridHasNineColumnsAndEnoughRowsForEveryChoice(int count) {
        openCatalog(java.util.Collections.nCopies(count, STONE));
        assertEquals(9, shownActions.columns());
        assertEquals(count, shownActions.actions().size());
        verify(provider).plainMessageDialogBody(Component.text("dialog.catalog-hint"), Math.min(count, 9) * 22 - 2);
        for (ActionButton button : shownActions.actions()) {
            assertEquals(20, button.width());
            assertEquals(STONE.icon(), button.label());
            assertInstanceOf(SpriteObjectContents.class, assertInstanceOf(ObjectComponent.class, button.label()).contents());
            assertEquals(STONE.name(), button.tooltip());
        }
        verify(messages, never()).text("dialog.rate");
    }

    @Test
    void largeCustomCategoriesKeepEveryProductSelectableInOneDialog() {
        var choices = new ArrayList<>(java.util.Collections.nCopies(54, STONE));
        choices.set(53, new RedeemProduct("stone", Material.SHULKER_BOX, null));
        openCatalog(choices);
        assertEquals(54, shownActions.actions().size());
        click(shownActions.actions().get(53), null, player);
        verify(provider).itemDialogBodyBuilder(argThat(item -> item.getType() == Material.SHULKER_BOX));
        click("dialog.back", null, player);
        assertEquals(54, shownActions.actions().size());
    }

    @Test
    void legacyProductsRemainAvailableUnderCatalogTitle() {
        dialogs = dialogs(Map.of(), List.of(new RedeemProduct("", Material.STONE, null)));
        dialogs.openCategories(player);
        click("dialog.catalog-title", null, player);
        assertEquals(STONE.icon(), shownActions.actions().getFirst().label());
    }

    @Test
    void revokingPermissionBlocksCatalogSelection() {
        openCatalog(List.of(STONE));
        when(player.hasPermission("tianjiredeem.use")).thenReturn(false);
        click(shownActions.actions().getFirst(), null, player);
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
        assertEquals(painting.icon(), shownActions.actions().getFirst().label());
        assertEquals(painting.name().append(Component.newline()).append(Component.text(Art.EARTH.getKey().asString())),
            shownActions.actions().getFirst().tooltip());
        click(shownActions.actions().getFirst(), null, player);
        verify(provider).itemDialogBodyBuilder(argThat(item -> item.getType() == Material.PAINTING
            && item.getData(DataComponentTypes.PAINTING_VARIANT) == Art.EARTH));
        click("dialog.redeem", 1F, player);
        verify(service).redeem(player, painting, 1);
    }

    @Test
    void successfulRedemptionPlaysConfiguredSoundAndRefreshesBalanceAndButtons() {
        when(player.getInventory().getStorageContents()).thenReturn(new ItemStack[]{RedemptionTest.voucher(64)});
        when(service.redeem(player, STONE, 64)).thenAnswer(call -> {
            when(player.getInventory().getStorageContents()).thenReturn(new ItemStack[36]);
            return true;
        });
        openProduct();
        var previousButton = shownActions.actions().getFirst();
        click("dialog.redeem", 64F, player);
        verify(service).redeem(player, STONE, 64);
        verify(player).playSound(SUCCESS_SOUND);
        verify(messages).text("dialog.balance", Map.of("amount", "64"));
        verify(messages).text("dialog.balance", Map.of("amount", "0"));
        assertNotSame(previousButton, shownActions.actions().getFirst());
        assertFalse(hasButton("dialog.continue"));
        verify(provider, times(2)).numberRangeBuilder(eq("amount"), any(), eq(1F), eq(64F));
        click("dialog.redeem", 1F, player);
        verify(service).redeem(player, STONE, 1);
        click("dialog.back", null, player);
        assertEquals(STONE.icon(), shownActions.actions().getFirst().label());
    }

    @Test
    void insufficientVouchersPlaysFailureSoundAndRefreshesProductPageWithReason() {
        openProduct();
        click("dialog.redeem", 1F, player);
        verify(service).redeem(player, STONE, 1);
        verify(messages).textComponents(eq("dialog.insufficient"), anyMap());
        verify(player).playSound(FAILURE_SOUND);
        verify(provider, times(2)).numberRangeBuilder(eq("amount"), any(), eq(1F), eq(64F));
        assertFalse(hasButton("dialog.continue"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(floats = {0F, -1F, 65F, 1.5F, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    void malformedAmountsCannotReachRedemption(Float value) {
        openProduct();
        click("dialog.redeem", value, player);
        verifyNoInteractions(service);
        verify(messages).text("dialog.invalid-amount");
        verify(player).playSound(FAILURE_SOUND);
        verify(provider, times(2)).numberRangeBuilder(eq("amount"), any(), eq(1F), eq(64F));
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
    void reloadUpdatesMenusMessagesAndSoundsAndInvalidatesOldButtons() throws Exception {
        when(player.getName()).thenReturn("ReloadViewer");
        when(player.hasPermission(any(org.bukkit.permissions.Permission.class)))
                .thenAnswer(call -> player.hasPermission(call.getArgument(0, org.bukkit.permissions.Permission.class).getName()));
        var livePlugin = MockBukkit.load(TianjiRedeemPlugin.class);
        var command = livePlugin.getCommand("tianjiredeem");
        command.execute(player, "tianjiredeem", new String[]{"open"});
        click(shownActions.actions().getFirst(), null, player);
        click(shownActions.actions().getFirst(), null, player);
        var oldRedeem = shownActions.actions().getFirst();

        File configFile = new File(livePlugin.getDataFolder(), "config.yml");
        var config = YamlConfiguration.loadConfiguration(configFile);
        config.set("categories", Map.of("new", Map.of("name", "新分类", "products", List.of("minecraft:diamond_block"))));
        config.set("sounds.failure.sound", "minecraft:block.anvil.land");
        config.set("sounds.failure.volume", 0.3);
        config.set("sounds.failure.pitch", 0.8);
        config.save(configFile);
        File messagesFile = new File(livePlugin.getDataFolder(), "messages.yml");
        var texts = YamlConfiguration.loadConfiguration(messagesFile);
        texts.set("dialog.balance", "new balance {amount}");
        texts.set("command.reloaded", "reloaded");
        texts.save(messagesFile);

        var console = MockBukkit.getMock().getConsoleSender();
        command.execute(console, "tianjiredeem", new String[]{"reload"});
        assertEquals(Component.text("reloaded"), console.nextComponentMessage());
        clearInvocations(player);
        click(oldRedeem, 1F, player);
        verify(player).closeDialog();
        verify(player, never()).getInventory();
        verify(player, never()).showDialog(any());

        buttons.clear();
        command.execute(player, "tianjiredeem", new String[]{"open"});
        assertEquals(1, shownActions.actions().size());
        assertTrue(hasButton("新分类"));
        click("新分类", null, player);
        assertEquals(1, shownActions.actions().size());
        click(shownActions.actions().getFirst(), null, player);
        verify(provider).itemDialogBodyBuilder(argThat(item -> item.getType() == Material.DIAMOND_BLOCK));
        verify(provider).plainMessageDialogBody(argThat(text ->
                PlainTextComponentSerializer.plainText().serialize(text).equals("new balance 0")));
        click(shownActions.actions().getFirst(), 1F, player);
        verify(player).playSound(FAILURE_SOUND);
    }

    @Test
    void emptyCatalogCanBeClosed() {
        dialogs(Map.of(), List.of()).openCategories(player);
        verify(messages).text("dialog.catalog-empty");
        click("dialog.close", null, player);
        verify(player).closeDialog();
    }

    private void openProduct() {
        openCatalog(List.of(STONE));
        click(shownActions.actions().getFirst(), null, player);
    }

    private void openCatalog(List<RedeemProduct> products) {
        dialogs = dialogs(Map.of("stone", "石材"), products);
        dialogs.openCategories(player);
        click("石材", null, player);
    }

    private void click(String label, Float amount, Player actor) {
        var button = buttons.reversed().stream().filter(entry -> label(entry).equals(label)).findFirst().orElseThrow();
        click(button, amount, actor);
    }

    private RedeemDialogs dialogs(Map<String, String> categories, List<RedeemProduct> products) {
        return new RedeemDialogs(plugin, new RedeemConfig(Component.empty(), List.of(), categories, products,
            SUCCESS_SOUND, FAILURE_SOUND), messages, service);
    }

    private void click(ActionButton button, Float amount, Player actor) {
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
