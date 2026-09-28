package org.tianjiserver.redeem;

import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RedeemCommand implements CommandExecutor, TabCompleter {
    private final Server server;
    private final RedeemDialogs dialogs;
    private final ItemStack voucher;
    private final Messages messages;

    public RedeemCommand(Server server, RedeemDialogs dialogs, ItemStack voucher, Messages messages) {
        this.server = server;
        this.dialogs = dialogs;
        this.voucher = voucher;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!sender.hasPermission("tianjiredeem.use")) {
                sender.sendMessage(messages.text("command.no-permission"));
            } else if (sender instanceof Player player) {
                dialogs.openCatalog(player, 0);
            } else {
                sender.sendMessage(messages.text("command.player-only"));
            }
            return true;
        }
        if (!args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(messages.text("command.usage"));
            return true;
        }
        if (!sender.hasPermission("tianjiredeem.admin.give")) {
            sender.sendMessage(messages.text("command.no-permission"));
            return true;
        }
        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(messages.text("command.usage"));
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(messages.text("command.invalid-amount"));
            return true;
        }
        if (amount <= 0) {
            sender.sendMessage(messages.text("command.invalid-amount"));
            return true;
        }
        Player target;
        if (args.length == 3) {
            target = server.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(messages.text("command.player-not-found", Map.of("player", args[2])));
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(messages.text("command.player-required"));
            return true;
        }
        ItemDelivery.give(target, voucher, amount);
        var replacements = Map.of("player", target.getName(), "amount", Integer.toString(amount));
        sender.sendMessage(messages.text("command.given", replacements));
        if (!target.equals(sender)) target.sendMessage(messages.text("command.received", replacements));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("tianjiredeem.admin.give")) return List.of();
        if (args.length == 1) {
            return "give".startsWith(args[0].toLowerCase(Locale.ROOT)) ? List.of("give") : List.of();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return server.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        return List.of();
    }
}
