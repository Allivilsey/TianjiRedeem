package org.allivlisey.redeem;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import revxrsal.commands.Lamp;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.CommandPriority;
import revxrsal.commands.annotation.Named;
import revxrsal.commands.annotation.Optional;
import revxrsal.commands.annotation.Range;
import revxrsal.commands.annotation.Sized;
import revxrsal.commands.bukkit.BukkitLamp;
import revxrsal.commands.bukkit.BukkitLampConfig;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.bukkit.exception.InvalidPlayerException;
import revxrsal.commands.bukkit.exception.SenderNotPlayerException;
import revxrsal.commands.exception.*;
import revxrsal.commands.exception.context.ErrorContext;

import java.util.Map;
import java.util.logging.Level;

public final class RedeemCommand {
    private final RedeemDialogs dialogs;
    private final ItemStack voucher;
    private final Messages messages;

    public RedeemCommand(RedeemDialogs dialogs, ItemStack voucher, Messages messages) {
        this.dialogs = dialogs;
        this.voucher = voucher;
        this.messages = messages;
    }

    public Lamp<BukkitCommandActor> register(JavaPlugin plugin) {
        // Online-player suggestions read Bukkit state on the server thread.
        var lamp = BukkitLamp.builder(BukkitLampConfig.<BukkitCommandActor>builder(plugin)
                .disableBrigadier().disableAsyncCompletion().build())
                .parameterTypes(types -> types.addParameterType(Player.class, (input, context) -> {
                    String name = input.readString();
                    Player player = plugin.getServer().getPlayerExact(name);
                    if (player == null) throw new InvalidPlayerException(name);
                    return player;
                }))
                .suggestionProviders(providers -> providers.addProvider(Player.class, context ->
                        plugin.getServer().getOnlinePlayers().stream().map(Player::getName)
                                .sorted(String.CASE_INSENSITIVE_ORDER).toList()))
                .exceptionHandler((error, context) -> handleException(error, context, plugin))
                .build();
        lamp.register(this);
        return lamp;
    }

    // The empty tail lets Lamp reject surplus arguments instead of accepting a prefix.
    @Command("tianjiredeem")
    @CommandPriority.Low
    @CommandPermission(value = "tianjiredeem.use", defaultAccess = PermissionDefault.TRUE)
    public void open(Player player, @Sized(max = 0) String[] extra) {
        dialogs.openCatalog(player, 0);
    }

    @Command("tianjiredeem give")
    @CommandPermission("tianjiredeem.admin.give")
    public void give(BukkitCommandActor actor, @Range(min = 1) int amount,
                     @Optional @Named("player_name") Player target, @Sized(max = 0) String[] extra) {
        if (target == null) target = actor.asPlayer();
        if (target == null) {
            actor.sender().sendMessage(messages.text("command.player-required"));
            return;
        }
        ItemDelivery.give(target, voucher, amount);
        var replacements = Map.of("player", target.getName(), "amount", Integer.toString(amount));
        actor.sender().sendMessage(messages.text("command.given", replacements));
        if (!target.equals(actor.sender())) target.sendMessage(messages.text("command.received", replacements));
    }

    private void handleException(Throwable error, ErrorContext<BukkitCommandActor> context, JavaPlugin plugin) {
        Throwable cause = error instanceof CommandInvocationException invocation ? invocation.cause() : error;
        var sender = context.actor().sender();
        if (cause instanceof InvalidPlayerException invalid) {
            sender.sendMessage(messages.text("command.player-not-found", Map.of("player", invalid.input())));
            return;
        }
        String key = switch (cause) {
            case NoPermissionException _ -> "command.no-permission";
            case SenderNotPlayerException _ -> "command.player-only";
            case InvalidNumberException _, NumberNotInRangeException _ -> "command.invalid-amount";
            case MissingArgumentException _, ExpectedLiteralException _, UnknownParameterException _,
                 UnknownCommandException _, InputParseException _, InvalidListSizeException _ -> "command.usage";
            default -> {
                plugin.getLogger().log(Level.SEVERE, messages.plain("command.failed", Map.of()), cause);
                yield "command.failed";
            }
        };
        sender.sendMessage(messages.text(key));
    }
}
