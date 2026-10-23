package com.arv.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /sell opens a menu: put items in the top slots, the bottom row is a green "Sell" button
 * whose description shows how much money you get. Anything that can't be sold stays in the
 * menu and is handed back when you close it. /sell hand and /sell all still work.
 */
public class SellMenu implements Listener, CommandExecutor, TabCompleter {

    private static final int SIZE = 54;
    /** Slots 0-44 hold the items to sell, 45-53 is the green Sell row. */
    private static final int ITEM_SLOTS = 45;

    private static final class SellHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final EconomyPlugin plugin;

    public SellMenu(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    // ---------- menu ----------

    public void open(Player player) {
        SellHolder holder = new SellHolder();
        Inventory inv = Bukkit.createInventory(holder, SIZE, Component.text("Sell Items", NamedTextColor.DARK_GRAY));
        holder.inventory = inv;
        refresh(inv);
        player.openInventory(inv);
    }

    private double total(Inventory inv) {
        double total = 0;
        for (int i = 0; i < ITEM_SLOTS; i++) {
            double quote = plugin.shop().sellQuote(inv.getItem(i));
            if (quote > 0) {
                total += quote;
            }
        }
        return EconomyManager.round(total);
    }

    /** Rebuilds the green Sell row with the current payout in its description. */
    private void refresh(Inventory inv) {
        double total = total(inv);
        ItemStack pane = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(line("Sell", NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        List<Component> lore = new ArrayList<>();
        if (total > 0) {
            lore.add(line("You get: " + EconomyManager.format(total), NamedTextColor.GREEN));
            lore.add(line("Click to sell", NamedTextColor.YELLOW));
        } else {
            lore.add(line("You get: " + EconomyManager.format(0), NamedTextColor.GREEN));
            lore.add(line("Put items in the slots above.", NamedTextColor.GRAY));
        }
        meta.lore(lore);
        pane.setItemMeta(meta);
        for (int slot = ITEM_SLOTS; slot < SIZE; slot++) {
            inv.setItem(slot, pane.clone());
        }
    }

    private void sell(Player player, Inventory inv) {
        double total = 0;
        int count = 0;
        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack stack = inv.getItem(i);
            double quote = plugin.shop().sellQuote(stack);
            if (quote < 0) {
                continue;
            }
            total += quote;
            count += stack.getAmount();
            inv.setItem(i, null);
        }
        if (count == 0) {
            player.sendMessage(Component.text("Nothing in the menu can be sold.", NamedTextColor.RED));
            return;
        }
        total = EconomyManager.round(total);
        plugin.economy().add(player.getUniqueId(), total);
        player.sendMessage(Component.text("Sold " + count + " items for " + EconomyManager.format(total) + ".",
                NamedTextColor.GREEN));
        player.playSound(player.getLocation(), "entity.experience_orb.pickup", 0.6f, 1.2f);
        refresh(inv);
    }

    // ---------- events ----------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof SellHolder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked != null && clicked.equals(top) && event.getSlot() >= ITEM_SLOTS) {
            event.setCancelled(true);
            sell(player, top);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> refresh(top));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof SellHolder)) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw >= ITEM_SLOTS && raw < SIZE) {
                event.setCancelled(true);
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> refresh(top));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof SellHolder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            inv.setItem(i, null);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
            for (ItemStack rest : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
    }

    // ---------- /sell ----------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /sell.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            open(player);
        } else if (args[0].equalsIgnoreCase("hand")) {
            plugin.shop().sellHand(player);
        } else if (args[0].equalsIgnoreCase("all")) {
            plugin.shop().sellAll(player);
        } else {
            player.sendMessage(Component.text("Usage: /sell (menu), /sell hand or /sell all", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String option : List.of("hand", "all")) {
                if (option.startsWith(prefix)) {
                    out.add(option);
                }
            }
        }
        return out;
    }
}
