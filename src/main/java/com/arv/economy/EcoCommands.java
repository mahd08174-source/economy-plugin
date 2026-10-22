package com.arv.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class EcoCommands implements CommandExecutor {

    public static final String DEV_NAME = "Arv1oishated";
    private static final long TPA_TIMEOUT_MS = 60_000L;

    private record TpaRequest(UUID from, boolean here, long expires) {}

    private record Target(UUID id, String name) {}

    private final EconomyPlugin plugin;
    private final EconomyManager eco;
    private final Shop shop;
    private final Map<UUID, TpaRequest> pending = new HashMap<>();

    public EcoCommands(EconomyPlugin plugin, Shop shop) {
        this.plugin = plugin;
        this.eco = plugin.economy();
        this.shop = shop;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "bal" -> bal(sender, args);
            case "pay" -> pay(sender, args);
            case "give" -> give(sender, args);
            case "remove" -> remove(sender, args);
            case "shop" -> openShop(sender);
            case "sell" -> sell(sender, args);
            case "tpa" -> tpa(sender, args, false);
            case "tpahere" -> tpa(sender, args, true);
            case "tpaccept" -> tpAccept(sender);
            case "tpdeny" -> tpDeny(sender);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---------- helpers ----------

    private static void error(CommandSender to, String message) {
        to.sendMessage(Component.text(message, NamedTextColor.RED));
    }

    private static void info(CommandSender to, String message) {
        to.sendMessage(Component.text(message, NamedTextColor.GREEN));
    }

    private static boolean isDev(CommandSender sender) {
        return sender instanceof ConsoleCommandSender
                || (sender instanceof Player p && p.getName().equalsIgnoreCase(DEV_NAME));
    }

    private Target resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return new Target(online.getUniqueId(), online.getName());
        }
        OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(name);
        if (off != null && eco.has(off.getUniqueId())) {
            String shown = off.getName() == null ? name : off.getName();
            return new Target(off.getUniqueId(), shown);
        }
        return null;
    }

    public void clear(Player player) {
        UUID id = player.getUniqueId();
        pending.remove(id);
        pending.values().removeIf(r -> r.from().equals(id));
    }

    // ---------- economy ----------

    private void bal(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                error(sender, "Usage: /bal <player>");
                return;
            }
            sender.sendMessage(Component.text("Balance: ", NamedTextColor.GRAY)
                    .append(Component.text(EconomyManager.format(eco.get(p.getUniqueId())), NamedTextColor.GREEN)));
            return;
        }
        Target target = resolve(args[0]);
        if (target == null) {
            error(sender, "Player not found.");
            return;
        }
        sender.sendMessage(Component.text(target.name() + "'s balance: ", NamedTextColor.GRAY)
                .append(Component.text(EconomyManager.format(eco.get(target.id())), NamedTextColor.GREEN)));
    }

    private void pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            error(sender, "Only players can use /pay.");
            return;
        }
        if (args.length != 2) {
            error(sender, "Usage: /pay <player> <amount>  (e.g. 100m, 1.5k)");
            return;
        }
        Target target = resolve(args[0]);
        if (target == null) {
            error(sender, "Player not found.");
            return;
        }
        Double amount = EconomyManager.parseAmount(args[1]);
        if (amount == null) {
            error(sender, "Enter a valid amount, like 50, 12.50, 100k or 100m.");
            return;
        }
        if (!eco.withdraw(p.getUniqueId(), amount)) {
            error(sender, "You don't have enough money.");
            return;
        }
        eco.add(target.id(), amount);
        info(p, "You paid " + target.name() + " " + EconomyManager.format(amount) + ".");
        Player online = Bukkit.getPlayer(target.id());
        if (online != null && !online.equals(p)) {
            info(online, p.getName() + " paid you " + EconomyManager.format(amount) + ".");
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (!isDev(sender)) {
            error(sender, "You don't have permission to use this command.");
            return;
        }
        if (args.length != 2) {
            error(sender, "Usage: /give <username> <money>");
            return;
        }
        Target target = resolve(args[0]);
        if (target == null) {
            error(sender, "Player not found.");
            return;
        }
        Double amount = EconomyManager.parseAmount(args[1]);
        if (amount == null) {
            error(sender, "Enter a valid amount, like 500, 100k or 100m.");
            return;
        }
        eco.add(target.id(), amount);
        info(sender, "Gave " + EconomyManager.format(amount) + " to " + target.name() + ".");
        Player online = Bukkit.getPlayer(target.id());
        if (online != null && !online.equals(sender)) {
            info(online, "You received " + EconomyManager.format(amount) + ".");
        }
    }

    private void remove(CommandSender sender, String[] args) {
        if (!isDev(sender)) {
            error(sender, "You don't have permission to use this command.");
            return;
        }
        if (args.length != 2) {
            error(sender, "Usage: /remove <username> <money>");
            return;
        }
        Target target = resolve(args[0]);
        if (target == null) {
            error(sender, "Player not found.");
            return;
        }
        Double amount = EconomyManager.parseAmount(args[1]);
        if (amount == null) {
            error(sender, "Enter a valid amount, like 500, 100k or 100m.");
            return;
        }
        double take = Math.min(amount, eco.get(target.id()));
        eco.withdraw(target.id(), take);
        info(sender, "Removed " + EconomyManager.format(take) + " from " + target.name() + ".");
        Player online = Bukkit.getPlayer(target.id());
        if (online != null && !online.equals(sender)) {
            error(online, EconomyManager.format(take) + " was removed from your balance.");
        }
    }

    private void openShop(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            error(sender, "Only players can use /shop.");
            return;
        }
        shop.openMain(p);
    }

    private void sell(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            error(sender, "Only players can use /sell.");
            return;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("hand")) {
            shop.sellHand(p);
        } else if (args[0].equalsIgnoreCase("all")) {
            shop.sellAll(p);
        } else {
            error(sender, "Usage: /sell (item in hand) or /sell all");
        }
    }

    // ---------- teleport requests ----------

    private void tpa(CommandSender sender, String[] args, boolean here) {
        if (!(sender instanceof Player p)) {
            error(sender, "Only players can use this command.");
            return;
        }
        if (args.length != 1) {
            error(sender, "Usage: /" + (here ? "tpahere" : "tpa") + " <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            error(sender, "That player is not online.");
            return;
        }
        if (target.equals(p)) {
            error(sender, "You can't send a request to yourself.");
            return;
        }
        pending.put(target.getUniqueId(),
                new TpaRequest(p.getUniqueId(), here, System.currentTimeMillis() + TPA_TIMEOUT_MS));

        info(p, "Teleport request sent to " + target.getName() + ". It expires in 60 seconds.");

        String ask = here
                ? p.getName() + " wants you to teleport to them. "
                : p.getName() + " wants to teleport to you. ";
        Component accept = Component.text("[Accept]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/tpaccept"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to accept")));
        Component deny = Component.text("[Deny]", NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/tpdeny"))
                .hoverEvent(HoverEvent.showText(Component.text("Click to deny")));
        target.sendMessage(Component.text(ask, NamedTextColor.YELLOW)
                .append(accept)
                .append(Component.space())
                .append(deny));
    }

    private TpaRequest takeRequest(Player me) {
        TpaRequest request = pending.remove(me.getUniqueId());
        if (request == null || request.expires() < System.currentTimeMillis()) {
            error(me, "You have no pending teleport requests.");
            return null;
        }
        return request;
    }

    private void tpAccept(CommandSender sender) {
        if (!(sender instanceof Player me)) {
            error(sender, "Only players can use this command.");
            return;
        }
        TpaRequest request = takeRequest(me);
        if (request == null) {
            return;
        }
        Player from = Bukkit.getPlayer(request.from());
        if (from == null) {
            error(me, "That player is no longer online.");
            return;
        }
        if (request.here()) {
            me.teleportAsync(from.getLocation());
            info(me, "Teleporting to " + from.getName() + "...");
        } else {
            from.teleportAsync(me.getLocation());
            info(from, "Teleporting to " + me.getName() + "...");
        }
        info(from, me.getName() + " accepted your teleport request.");
    }

    private void tpDeny(CommandSender sender) {
        if (!(sender instanceof Player me)) {
            error(sender, "Only players can use this command.");
            return;
        }
        TpaRequest request = takeRequest(me);
        if (request == null) {
            return;
        }
        info(me, "Teleport request denied.");
        Player from = Bukkit.getPlayer(request.from());
        if (from != null) {
            error(from, me.getName() + " denied your teleport request.");
        }
    }
}
