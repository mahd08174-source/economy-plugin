package com.arv.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public class EcoCommands implements CommandExecutor, TabCompleter {

    public static final String DEV_NAME = "Arv1oishated";
    private static final long TPA_TIMEOUT_MS = 60_000L;

    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Pattern HOME_NAME = Pattern.compile("[a-z0-9_-]{1,16}");

    // /rtp settings
    private static final int RTP_MAX_RADIUS = 10_000;   // never farther than this from the border centre
    private static final int RTP_BORDER_MARGIN = 16;    // stay this far inside the world border
    private static final int RTP_ATTEMPTS = 12;
    private static final Set<Material> UNSAFE = EnumSet.of(
            Material.LAVA, Material.WATER, Material.FIRE, Material.SOUL_FIRE, Material.MAGMA_BLOCK,
            Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.SWEET_BERRY_BUSH,
            Material.POWDER_SNOW, Material.WITHER_ROSE, Material.POINTED_DRIPSTONE,
            Material.COBWEB, Material.BUBBLE_COLUMN);

    private record TpaRequest(UUID from, boolean here, long expires) {}

    private record Target(UUID id, String name) {}

    private final EconomyPlugin plugin;
    private final EconomyManager eco;
    private final Shop shop;
    private final HomeManager homes;
    private final Set<UUID> rtpBusy = new HashSet<>();
    private final Map<UUID, TpaRequest> pending = new HashMap<>();

    public EcoCommands(EconomyPlugin plugin, Shop shop) {
        this.plugin = plugin;
        this.eco = plugin.economy();
        this.shop = shop;
        this.homes = plugin.homes();
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
            case "kick" -> kick(sender, args);
            case "ban" -> ban(sender, args);
            case "wipe" -> wipe(sender, args);
            case "removeall" -> removeAll(sender, args);
            case "rtp" -> rtp(sender);
            case "sethome" -> setHome(sender, args);
            case "home" -> home(sender, args);
            case "delhome" -> delHome(sender, args);
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

    /** Admin commands are restricted strictly to the player named arv1oishated (case-insensitive). */
    private static boolean isDev(CommandSender sender) {
        return sender instanceof Player p && p.getName().equalsIgnoreCase(DEV_NAME);
    }

    private static void noPermission(CommandSender to) {
        error(to, "You don't have permission to use this command.");
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


    // ---------- admin commands (arv1oishated only) ----------

    private void kick(CommandSender sender, String[] args) {
        if (!isDev(sender)) {
            noPermission(sender);
            return;
        }
        if (args.length < 1) {
            error(sender, "Usage: /kick <player> [reason]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            error(sender, "That player is not online.");
            return;
        }
        if (target.equals(sender)) {
            error(sender, "You can't kick yourself.");
            return;
        }
        String reason = args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : "Kicked by an admin.";
        target.kick(Component.text(reason));
        info(sender, "Kicked " + target.getName() + ": " + reason);
    }

    private void ban(CommandSender sender, String[] args) {
        if (!isDev(sender)) {
            noPermission(sender);
            return;
        }
        if (args.length < 1) {
            error(sender, "Usage: /ban <player> [reason]");
            return;
        }
        String name = args[0];
        if (!PLAYER_NAME.matcher(name).matches()) {
            error(sender, "Invalid player name.");
            return;
        }
        if (name.equalsIgnoreCase(DEV_NAME)) {
            error(sender, "You can't ban yourself.");
            return;
        }
        String reason = args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : "Banned by an admin.";
        // Uses the vanilla ban list (works for offline players too). The name is validated above,
        // so selectors like @a can never reach the command.
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:ban " + name + " " + reason);
        info(sender, "Banned " + name + ": " + reason);
    }

    private void wipe(CommandSender sender, String[] args) {
        if (!isDev(sender)) {
            noPermission(sender);
            return;
        }
        if (args.length != 1) {
            error(sender, "Usage: /wipe <player>");
            return;
        }
        Target target = resolve(args[0]);
        if (target == null) {
            error(sender, "Player not found.");
            return;
        }
        eco.reset(target.id());
        homes.clear(target.id());
        pending.remove(target.id());
        pending.values().removeIf(r -> r.from().equals(target.id()));
        info(sender, "Wiped " + target.name() + "'s data (balance reset to "
                + EconomyManager.format(EconomyManager.START_BALANCE) + ", homes deleted).");
        Player online = Bukkit.getPlayer(target.id());
        if (online != null && !online.equals(sender)) {
            error(online, "Your economy data was wiped by an admin.");
        }
    }

    private void removeAll(CommandSender sender, String[] args) {
        if (!isDev(sender)) {
            noPermission(sender);
            return;
        }
        if (args.length != 1) {
            error(sender, "Usage: /removeall <player>");
            return;
        }
        Target target = resolve(args[0]);
        if (target == null) {
            error(sender, "Player not found.");
            return;
        }
        double had = eco.get(target.id());
        eco.set(target.id(), 0.0);
        info(sender, "Removed all money (" + EconomyManager.format(had) + ") from " + target.name() + ".");
        Player online = Bukkit.getPlayer(target.id());
        if (online != null && !online.equals(sender)) {
            error(online, "Your balance was cleared by an admin.");
        }
    }

    // ---------- homes ----------

    private void setHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            error(sender, "Only players can use this command.");
            return;
        }
        if (args.length != 1) {
            error(sender, "Usage: /sethome <name>");
            return;
        }
        String name = args[0].toLowerCase(Locale.ROOT);
        if (!HOME_NAME.matcher(name).matches()) {
            error(sender, "Home names can use letters, numbers, - and _ (max 16 characters).");
            return;
        }
        if (!homes.set(p.getUniqueId(), name, p.getLocation())) {
            error(sender, "You've used all " + HomeManager.MAX_HOMES
                    + " home slots. Remove one with /delhome <name> first.");
            return;
        }
        info(p, "Home '" + name + "' set. (" + homes.count(p.getUniqueId()) + "/" + HomeManager.MAX_HOMES + ")");
    }

    private void home(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            error(sender, "Only players can use this command.");
            return;
        }
        if (args.length == 0) {
            Map<String, HomeManager.Home> mine = homes.all(p.getUniqueId());
            if (mine.isEmpty()) {
                error(sender, "You have no homes. Use /sethome <name>.");
            } else {
                info(p, "Your homes (" + mine.size() + "/" + HomeManager.MAX_HOMES + "): "
                        + String.join(", ", mine.keySet()));
            }
            return;
        }
        if (args.length != 1) {
            error(sender, "Usage: /home <name>");
            return;
        }
        String name = args[0].toLowerCase(Locale.ROOT);
        HomeManager.Home h = homes.get(p.getUniqueId(), name);
        if (h == null) {
            error(sender, "You don't have a home called '" + name + "'.");
            return;
        }
        Location loc = h.toLocation();
        if (loc == null) {
            error(sender, "That home's world is not loaded.");
            return;
        }
        p.teleportAsync(loc);
        info(p, "Teleporting to home '" + name + "'...");
    }

    private void delHome(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            error(sender, "Only players can use this command.");
            return;
        }
        if (args.length != 1) {
            error(sender, "Usage: /delhome <name>");
            return;
        }
        String name = args[0].toLowerCase(Locale.ROOT);
        if (!homes.delete(p.getUniqueId(), name)) {
            error(sender, "You don't have a home called '" + name + "'.");
            return;
        }
        info(p, "Home '" + name + "' deleted. (" + homes.count(p.getUniqueId()) + "/" + HomeManager.MAX_HOMES + ")");
    }

    // ---------- random teleport ----------

    private void rtp(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            error(sender, "Only players can use /rtp.");
            return;
        }
        World world = p.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            error(sender, "/rtp only works in the overworld.");
            return;
        }
        if (!rtpBusy.add(p.getUniqueId())) {
            error(sender, "Already searching for a safe spot...");
            return;
        }
        info(p, "Searching for a safe location...");
        rtpAttempt(p, world, 0);
    }

    private void rtpAttempt(Player p, World world, int attempt) {
        UUID id = p.getUniqueId();
        if (!p.isOnline()) {
            rtpBusy.remove(id);
            return;
        }
        if (attempt >= RTP_ATTEMPTS) {
            rtpBusy.remove(id);
            error(p, "Couldn't find a safe location. Try again.");
            return;
        }
        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double radius = Math.min(border.getSize() / 2.0 - RTP_BORDER_MARGIN, RTP_MAX_RADIUS);
        if (radius < 1) {
            rtpBusy.remove(id);
            error(p, "The world border is too small for /rtp.");
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int x = (int) Math.floor(center.getX() + rnd.nextDouble(-radius, radius));
        int z = (int) Math.floor(center.getZ() + rnd.nextDouble(-radius, radius));

        // Load the target chunk asynchronously so the server doesn't lag, then check it on the main thread.
        world.getChunkAtAsync(x >> 4, z >> 4).whenComplete((chunk, loadError) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (loadError != null) {
                        rtpAttempt(p, world, attempt + 1);
                        return;
                    }
                    Location safe = safeSpot(world, x, z);
                    if (safe == null) {
                        rtpAttempt(p, world, attempt + 1);
                        return;
                    }
                    rtpBusy.remove(id);
                    if (!p.isOnline()) {
                        return;
                    }
                    safe.setYaw(p.getLocation().getYaw());
                    safe.setPitch(p.getLocation().getPitch());
                    p.teleportAsync(safe);
                    info(p, "Teleported to " + safe.getBlockX() + ", " + safe.getBlockY() + ", "
                            + safe.getBlockZ() + ".");
                }));
    }

    /** Returns a standing location at (x, z) or null if the spot is not safe. */
    private static Location safeSpot(World world, int x, int z) {
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 3) {
            return null;
        }
        Block ground = world.getBlockAt(x, y, z);
        Block feet = ground.getRelative(BlockFace.UP);
        Block head = feet.getRelative(BlockFace.UP);
        Material gm = ground.getType();
        if (!gm.isSolid() || UNSAFE.contains(gm)) {
            return null;
        }
        if (!feet.isPassable() || feet.isLiquid() || UNSAFE.contains(feet.getType())) {
            return null;
        }
        if (!head.isPassable() || head.isLiquid() || UNSAFE.contains(head.getType())) {
            return null;
        }
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Material side = feet.getRelative(face).getType();
            if (side == Material.LAVA || side == Material.FIRE || side == Material.SOUL_FIRE) {
                return null;
            }
        }
        Location loc = new Location(world, x + 0.5, y + 1, z + 0.5);
        return world.getWorldBorder().isInside(loc) ? loc : null;
    }

    // ---------- tab completion ----------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "home", "delhome" -> {
                if (sender instanceof Player p) {
                    for (String n : homes.all(p.getUniqueId()).keySet()) {
                        if (n.startsWith(prefix)) {
                            out.add(n);
                        }
                    }
                }
            }
            case "kick", "ban", "wipe", "removeall" -> {
                if (isDev(sender)) {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                            out.add(online.getName());
                        }
                    }
                }
            }
            default -> {
            }
        }
        return out;
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
