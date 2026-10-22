package com.arv.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Shop implements Listener {

    public enum Category {
        GEAR("Gear", Material.DIAMOND_CHESTPLATE, 11, NamedTextColor.AQUA),
        BLOCKS("Blocks", Material.GRASS_BLOCK, 12, NamedTextColor.GREEN),
        FOOD("Food", Material.COOKED_BEEF, 13, NamedTextColor.GOLD),
        SPAWNERS("Spawners", Material.SPAWNER, 14, NamedTextColor.DARK_PURPLE),
        POTIONS("Potions", Material.POTION, 15, NamedTextColor.LIGHT_PURPLE);

        final String title;
        final Material icon;
        final int slot;
        final NamedTextColor color;

        Category(String title, Material icon, int slot, NamedTextColor color) {
            this.title = title;
            this.icon = icon;
            this.slot = slot;
            this.color = color;
        }
    }

    private record Entry(String name, double price, ItemStack item) {}

    private static final class MenuHolder implements InventoryHolder {
        private final Category category; // null = main menu
        private Inventory inventory;

        MenuHolder(Category category) {
            this.category = category;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int BACK_SLOT = 49;
    private static final int BALANCE_SLOT = 53;

    private final EconomyPlugin plugin;
    private final Map<Category, List<Entry>> catalog = new EnumMap<>(Category.class);

    public Shop(EconomyPlugin plugin) {
        this.plugin = plugin;
        buildCatalog();
    }

    // ---------- catalog ----------

    private void add(Category category, Entry entry) {
        catalog.computeIfAbsent(category, k -> new ArrayList<>()).add(entry);
    }

    private void simple(Category category, Material material, double price) {
        add(category, new Entry(pretty(material.name()), price, new ItemStack(material)));
    }

    private void spawner(EntityType type, double price) {
        String name = pretty(type.name()) + " Spawner";
        ItemStack stack = new ItemStack(Material.SPAWNER);
        BlockStateMeta meta = (BlockStateMeta) stack.getItemMeta();
        CreatureSpawner state = (CreatureSpawner) meta.getBlockState();
        state.setSpawnedType(type);
        meta.setBlockState(state);
        meta.displayName(Component.text(name, NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        add(Category.SPAWNERS, new Entry(name, price, stack));
    }

    private void potion(Material container, PotionType type, double price) {
        ItemStack stack = new ItemStack(container);
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        meta.setBasePotionType(type);
        stack.setItemMeta(meta);
        String prefix = container == Material.SPLASH_POTION ? "Splash " : "";
        add(Category.POTIONS, new Entry(prefix + "Potion of " + pretty(type.name()), price, stack));
    }

    private void buildCatalog() {
        // Gear
        simple(Category.GEAR, Material.DIAMOND_SWORD, 150);
        simple(Category.GEAR, Material.DIAMOND_PICKAXE, 200);
        simple(Category.GEAR, Material.DIAMOND_AXE, 180);
        simple(Category.GEAR, Material.DIAMOND_HELMET, 300);
        simple(Category.GEAR, Material.DIAMOND_CHESTPLATE, 480);
        simple(Category.GEAR, Material.DIAMOND_LEGGINGS, 420);
        simple(Category.GEAR, Material.DIAMOND_BOOTS, 240);
        simple(Category.GEAR, Material.NETHERITE_SWORD, 900);
        simple(Category.GEAR, Material.NETHERITE_PICKAXE, 950);
        simple(Category.GEAR, Material.NETHERITE_CHESTPLATE, 1500);
        simple(Category.GEAR, Material.BOW, 80);
        simple(Category.GEAR, Material.CROSSBOW, 100);
        simple(Category.GEAR, Material.ARROW, 2);
        simple(Category.GEAR, Material.SHIELD, 60);
        simple(Category.GEAR, Material.TRIDENT, 1200);
        simple(Category.GEAR, Material.ELYTRA, 2500);
        simple(Category.GEAR, Material.TOTEM_OF_UNDYING, 600);
        simple(Category.GEAR, Material.ENDER_PEARL, 50);
        simple(Category.GEAR, Material.FISHING_ROD, 40);

        // Blocks
        simple(Category.BLOCKS, Material.COBBLESTONE, 1);
        simple(Category.BLOCKS, Material.STONE, 2);
        simple(Category.BLOCKS, Material.DIRT, 1);
        simple(Category.BLOCKS, Material.SAND, 2);
        simple(Category.BLOCKS, Material.OAK_PLANKS, 1);
        simple(Category.BLOCKS, Material.OAK_LOG, 4);
        simple(Category.BLOCKS, Material.GLASS, 4);
        simple(Category.BLOCKS, Material.STONE_BRICKS, 3);
        simple(Category.BLOCKS, Material.DEEPSLATE_BRICKS, 5);
        simple(Category.BLOCKS, Material.BRICKS, 6);
        simple(Category.BLOCKS, Material.WHITE_WOOL, 5);
        simple(Category.BLOCKS, Material.OBSIDIAN, 30);
        simple(Category.BLOCKS, Material.QUARTZ_BLOCK, 15);
        simple(Category.BLOCKS, Material.GLOWSTONE, 20);
        simple(Category.BLOCKS, Material.SEA_LANTERN, 25);
        simple(Category.BLOCKS, Material.REDSTONE_BLOCK, 45);
        simple(Category.BLOCKS, Material.IRON_BLOCK, 90);
        simple(Category.BLOCKS, Material.GOLD_BLOCK, 180);
        simple(Category.BLOCKS, Material.DIAMOND_BLOCK, 600);

        // Food
        simple(Category.FOOD, Material.APPLE, 3);
        simple(Category.FOOD, Material.BREAD, 3);
        simple(Category.FOOD, Material.BAKED_POTATO, 3);
        simple(Category.FOOD, Material.COOKIE, 1);
        simple(Category.FOOD, Material.MELON_SLICE, 1);
        simple(Category.FOOD, Material.COOKED_CHICKEN, 5);
        simple(Category.FOOD, Material.COOKED_SALMON, 5);
        simple(Category.FOOD, Material.COOKED_PORKCHOP, 6);
        simple(Category.FOOD, Material.COOKED_BEEF, 6);
        simple(Category.FOOD, Material.PUMPKIN_PIE, 6);
        simple(Category.FOOD, Material.GOLDEN_CARROT, 12);
        simple(Category.FOOD, Material.CAKE, 30);
        simple(Category.FOOD, Material.GOLDEN_APPLE, 60);
        simple(Category.FOOD, Material.ENCHANTED_GOLDEN_APPLE, 800);

        // Spawners
        spawner(EntityType.PIG, 1000);
        spawner(EntityType.COW, 1000);
        spawner(EntityType.SHEEP, 1000);
        spawner(EntityType.CHICKEN, 1000);
        spawner(EntityType.ZOMBIE, 1500);
        spawner(EntityType.SKELETON, 1500);
        spawner(EntityType.SPIDER, 1500);
        spawner(EntityType.CAVE_SPIDER, 1800);
        spawner(EntityType.SLIME, 2000);
        spawner(EntityType.CREEPER, 2500);
        spawner(EntityType.ENDERMAN, 3000);
        spawner(EntityType.BLAZE, 3500);

        // Potions
        potion(Material.POTION, PotionType.STRONG_HEALING, 80);
        potion(Material.POTION, PotionType.STRONG_STRENGTH, 120);
        potion(Material.POTION, PotionType.STRONG_SWIFTNESS, 70);
        potion(Material.POTION, PotionType.FIRE_RESISTANCE, 60);
        potion(Material.POTION, PotionType.REGENERATION, 90);
        potion(Material.POTION, PotionType.NIGHT_VISION, 50);
        potion(Material.POTION, PotionType.WATER_BREATHING, 50);
        potion(Material.POTION, PotionType.SLOW_FALLING, 50);
        potion(Material.POTION, PotionType.INVISIBILITY, 60);
        potion(Material.POTION, PotionType.TURTLE_MASTER, 100);
        potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 100);
        simple(Category.POTIONS, Material.EXPERIENCE_BOTTLE, 20);
    }

    private static String pretty(String enumName) {
        StringBuilder out = new StringBuilder();
        for (String word : enumName.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    // ---------- menus ----------

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack filler() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return pane;
    }

    private ItemStack balanceItem(Player player) {
        ItemStack stack = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(line("Balance: " + EconomyManager.format(plugin.economy().get(player.getUniqueId())),
                NamedTextColor.GOLD));
        stack.setItemMeta(meta);
        return stack;
    }

    public void openMain(Player player) {
        MenuHolder holder = new MenuHolder(null);
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Shop", NamedTextColor.DARK_GRAY));
        holder.inventory = inv;
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler());
        }
        for (Category category : Category.values()) {
            ItemStack icon = new ItemStack(category.icon);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(line(category.title, category.color).decorate(TextDecoration.BOLD));
            meta.lore(List.of(line("Click to browse", NamedTextColor.GRAY)));
            icon.setItemMeta(meta);
            inv.setItem(category.slot, icon);
        }
        inv.setItem(22, balanceItem(player));
        player.openInventory(inv);
    }

    private void openCategory(Player player, Category category) {
        MenuHolder holder = new MenuHolder(category);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Shop - " + category.title, NamedTextColor.DARK_GRAY));
        holder.inventory = inv;

        List<Entry> entries = catalog.getOrDefault(category, List.of());
        for (int i = 0; i < entries.size() && i < 45; i++) {
            Entry entry = entries.get(i);
            ItemStack display = entry.item().clone();
            ItemMeta meta = display.getItemMeta();
            meta.lore(List.of(
                    line("Price: " + EconomyManager.format(entry.price()) + " each", NamedTextColor.GOLD),
                    line("Left-click: buy 1", NamedTextColor.GRAY),
                    line("Shift-click: buy 16", NamedTextColor.GRAY)));
            display.setItemMeta(meta);
            inv.setItem(i, display);
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(line("Back", NamedTextColor.YELLOW));
        back.setItemMeta(backMeta);
        inv.setItem(BACK_SLOT, back);
        inv.setItem(BALANCE_SLOT, balanceItem(player));
        player.openInventory(inv);
    }

    // ---------- buying ----------

    private void buy(Player player, Entry entry, int quantity) {
        EconomyManager eco = plugin.economy();
        double total = EconomyManager.round(entry.price() * quantity);
        if (eco.get(player.getUniqueId()) + 0.001 < total) {
            player.sendMessage(Component.text("You need " + EconomyManager.format(total) + " to buy that.",
                    NamedTextColor.RED));
            return;
        }
        ItemStack give = entry.item().clone();
        give.setAmount(quantity);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(give);
        int notGiven = 0;
        for (ItemStack rest : leftover.values()) {
            notGiven += rest.getAmount();
        }
        int given = quantity - notGiven;
        if (given <= 0) {
            player.sendMessage(Component.text("Your inventory is full.", NamedTextColor.RED));
            return;
        }
        double cost = EconomyManager.round(entry.price() * given);
        eco.withdraw(player.getUniqueId(), cost);
        player.sendMessage(Component.text("Bought " + given + "x " + entry.name() + " for "
                + EconomyManager.format(cost) + ".", NamedTextColor.GREEN));
    }

    // ---------- events ----------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(top)) {
            return;
        }
        int slot = event.getSlot();

        if (holder.category == null) {
            for (Category category : Category.values()) {
                if (category.slot == slot) {
                    Bukkit.getScheduler().runTask(plugin, () -> openCategory(player, category));
                    return;
                }
            }
            return;
        }

        if (slot == BACK_SLOT) {
            Bukkit.getScheduler().runTask(plugin, () -> openMain(player));
            return;
        }
        List<Entry> entries = catalog.getOrDefault(holder.category, List.of());
        if (slot >= 0 && slot < entries.size() && slot < 45) {
            buy(player, entries.get(slot), event.isShiftClick() ? 16 : 1);
            top.setItem(BALANCE_SLOT, balanceItem(player));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }
}
