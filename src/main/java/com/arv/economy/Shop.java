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

    /** Fraction of the shop price you get back when selling. */
    public static final double SELL_RATIO = 0.5;

    public enum Category {
        GEAR("Gear", Material.DIAMOND_CHESTPLATE, 10, NamedTextColor.AQUA),
        BLOCKS("Blocks", Material.GRASS_BLOCK, 11, NamedTextColor.GREEN),
        FOOD("Food", Material.COOKED_BEEF, 12, NamedTextColor.GOLD),
        SPAWNERS("Spawners", Material.SPAWNER, 14, NamedTextColor.DARK_PURPLE),
        POTIONS("Potions", Material.POTION, 15, NamedTextColor.LIGHT_PURPLE),
        MISC("Misc", Material.ENDER_CHEST, 16, NamedTextColor.YELLOW);

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

    private record Entry(Category category, String name, double price, ItemStack item) {}

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
    private static final int MAX_PER_CATEGORY = 45;

    private final EconomyPlugin plugin;
    private final Map<Category, List<Entry>> catalog = new EnumMap<>(Category.class);

    public Shop(EconomyPlugin plugin) {
        this.plugin = plugin;
        buildCatalog();
    }

    // ---------- catalog ----------

    private void add(Entry entry) {
        List<Entry> list = catalog.computeIfAbsent(entry.category(), k -> new ArrayList<>());
        if (list.size() < MAX_PER_CATEGORY) {
            list.add(entry);
        }
    }

    private void simple(Category category, Material material, double price) {
        add(new Entry(category, pretty(material.name()), price, new ItemStack(material)));
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
        add(new Entry(Category.SPAWNERS, name, price, stack));
    }

    private void potion(Material container, PotionType type, double price) {
        ItemStack stack = new ItemStack(container);
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        meta.setBasePotionType(type);
        stack.setItemMeta(meta);
        String prefix = container == Material.SPLASH_POTION ? "Splash " : "";
        add(new Entry(Category.POTIONS, prefix + "Potion of " + pretty(type.name()), price, stack));
    }

    private void buildCatalog() {
        // ---- Gear ----
        Category g = Category.GEAR;
        simple(g, Material.IRON_SWORD, 40);
        simple(g, Material.IRON_PICKAXE, 50);
        simple(g, Material.IRON_AXE, 45);
        simple(g, Material.IRON_SHOVEL, 30);
        simple(g, Material.IRON_HELMET, 75);
        simple(g, Material.IRON_CHESTPLATE, 120);
        simple(g, Material.IRON_LEGGINGS, 105);
        simple(g, Material.IRON_BOOTS, 60);
        simple(g, Material.DIAMOND_SWORD, 150);
        simple(g, Material.DIAMOND_PICKAXE, 200);
        simple(g, Material.DIAMOND_AXE, 180);
        simple(g, Material.DIAMOND_SHOVEL, 100);
        simple(g, Material.DIAMOND_HELMET, 300);
        simple(g, Material.DIAMOND_CHESTPLATE, 480);
        simple(g, Material.DIAMOND_LEGGINGS, 420);
        simple(g, Material.DIAMOND_BOOTS, 240);
        simple(g, Material.NETHERITE_SWORD, 900);
        simple(g, Material.NETHERITE_PICKAXE, 950);
        simple(g, Material.NETHERITE_AXE, 1000);
        simple(g, Material.NETHERITE_SHOVEL, 900);
        simple(g, Material.NETHERITE_HELMET, 1200);
        simple(g, Material.NETHERITE_CHESTPLATE, 1500);
        simple(g, Material.NETHERITE_LEGGINGS, 1400);
        simple(g, Material.NETHERITE_BOOTS, 1000);
        simple(g, Material.BOW, 80);
        simple(g, Material.CROSSBOW, 100);
        simple(g, Material.ARROW, 2);
        simple(g, Material.SHIELD, 60);
        simple(g, Material.TRIDENT, 1200);
        simple(g, Material.MACE, 3000);
        simple(g, Material.WIND_CHARGE, 20);
        simple(g, Material.ELYTRA, 2500);
        simple(g, Material.TOTEM_OF_UNDYING, 600);
        simple(g, Material.TURTLE_HELMET, 150);
        simple(g, Material.ENDER_PEARL, 50);
        simple(g, Material.FISHING_ROD, 40);
        simple(g, Material.SHEARS, 20);
        simple(g, Material.FLINT_AND_STEEL, 20);

        // ---- Blocks ----
        Category b = Category.BLOCKS;
        simple(b, Material.COBBLESTONE, 1);
        simple(b, Material.STONE, 2);
        simple(b, Material.DIRT, 1);
        simple(b, Material.GRAVEL, 1);
        simple(b, Material.SAND, 2);
        simple(b, Material.ANDESITE, 2);
        simple(b, Material.DIORITE, 2);
        simple(b, Material.GRANITE, 2);
        simple(b, Material.DEEPSLATE, 3);
        simple(b, Material.NETHERRACK, 1);
        simple(b, Material.END_STONE, 8);
        simple(b, Material.BLACKSTONE, 4);
        simple(b, Material.OAK_PLANKS, 1);
        simple(b, Material.OAK_LOG, 4);
        simple(b, Material.SPRUCE_LOG, 4);
        simple(b, Material.BIRCH_LOG, 4);
        simple(b, Material.DARK_OAK_LOG, 4);
        simple(b, Material.GLASS, 4);
        simple(b, Material.STONE_BRICKS, 3);
        simple(b, Material.DEEPSLATE_BRICKS, 5);
        simple(b, Material.BRICKS, 6);
        simple(b, Material.SANDSTONE, 4);
        simple(b, Material.TERRACOTTA, 5);
        simple(b, Material.WHITE_CONCRETE, 5);
        simple(b, Material.WHITE_WOOL, 5);
        simple(b, Material.PRISMARINE, 8);
        simple(b, Material.BOOKSHELF, 25);
        simple(b, Material.ICE, 5);
        simple(b, Material.PACKED_ICE, 10);
        simple(b, Material.HAY_BLOCK, 12);
        simple(b, Material.SOUL_SAND, 6);
        simple(b, Material.OBSIDIAN, 30);
        simple(b, Material.CRYING_OBSIDIAN, 60);
        simple(b, Material.QUARTZ_BLOCK, 15);
        simple(b, Material.GLOWSTONE, 20);
        simple(b, Material.SEA_LANTERN, 25);
        simple(b, Material.COAL_BLOCK, 30);
        simple(b, Material.COPPER_BLOCK, 60);
        simple(b, Material.LAPIS_BLOCK, 60);
        simple(b, Material.REDSTONE_BLOCK, 45);
        simple(b, Material.IRON_BLOCK, 90);
        simple(b, Material.GOLD_BLOCK, 180);
        simple(b, Material.EMERALD_BLOCK, 360);
        simple(b, Material.DIAMOND_BLOCK, 600);
        simple(b, Material.NETHERITE_BLOCK, 3600);

        // ---- Food ----
        Category f = Category.FOOD;
        simple(f, Material.APPLE, 3);
        simple(f, Material.BREAD, 3);
        simple(f, Material.CARROT, 2);
        simple(f, Material.POTATO, 2);
        simple(f, Material.BAKED_POTATO, 3);
        simple(f, Material.BEETROOT, 2);
        simple(f, Material.SWEET_BERRIES, 2);
        simple(f, Material.GLOW_BERRIES, 3);
        simple(f, Material.DRIED_KELP, 1);
        simple(f, Material.COOKIE, 1);
        simple(f, Material.MELON_SLICE, 1);
        simple(f, Material.COOKED_COD, 5);
        simple(f, Material.COOKED_SALMON, 5);
        simple(f, Material.COOKED_CHICKEN, 5);
        simple(f, Material.COOKED_RABBIT, 5);
        simple(f, Material.COOKED_MUTTON, 6);
        simple(f, Material.COOKED_PORKCHOP, 6);
        simple(f, Material.COOKED_BEEF, 6);
        simple(f, Material.PUMPKIN_PIE, 6);
        simple(f, Material.CHORUS_FRUIT, 6);
        simple(f, Material.MUSHROOM_STEW, 8);
        simple(f, Material.BEETROOT_SOUP, 6);
        simple(f, Material.RABBIT_STEW, 12);
        simple(f, Material.HONEY_BOTTLE, 8);
        simple(f, Material.GOLDEN_CARROT, 12);
        simple(f, Material.CAKE, 30);
        simple(f, Material.GOLDEN_APPLE, 60);
        simple(f, Material.ENCHANTED_GOLDEN_APPLE, 800);

        // ---- Spawners ----
        spawner(EntityType.PIG, 1000);
        spawner(EntityType.COW, 1000);
        spawner(EntityType.SHEEP, 1000);
        spawner(EntityType.CHICKEN, 1000);
        spawner(EntityType.ZOMBIE, 1500);
        spawner(EntityType.SKELETON, 1500);
        spawner(EntityType.SPIDER, 1500);
        spawner(EntityType.SILVERFISH, 1500);
        spawner(EntityType.CAVE_SPIDER, 1800);
        spawner(EntityType.SLIME, 2000);
        spawner(EntityType.ENDERMITE, 2000);
        spawner(EntityType.WITCH, 2200);
        spawner(EntityType.CREEPER, 2500);
        spawner(EntityType.MAGMA_CUBE, 2500);
        spawner(EntityType.ENDERMAN, 3000);
        spawner(EntityType.ZOMBIFIED_PIGLIN, 3000);
        spawner(EntityType.BLAZE, 3500);
        spawner(EntityType.IRON_GOLEM, 4000);
        spawner(EntityType.WITHER_SKELETON, 5000);

        // ---- Potions ----
        potion(Material.POTION, PotionType.STRONG_HEALING, 80);
        potion(Material.POTION, PotionType.STRONG_STRENGTH, 120);
        potion(Material.POTION, PotionType.STRONG_SWIFTNESS, 70);
        potion(Material.POTION, PotionType.STRONG_LEAPING, 60);
        potion(Material.POTION, PotionType.FIRE_RESISTANCE, 60);
        potion(Material.POTION, PotionType.STRONG_REGENERATION, 100);
        potion(Material.POTION, PotionType.NIGHT_VISION, 50);
        potion(Material.POTION, PotionType.WATER_BREATHING, 50);
        potion(Material.POTION, PotionType.SLOW_FALLING, 50);
        potion(Material.POTION, PotionType.INVISIBILITY, 60);
        potion(Material.POTION, PotionType.TURTLE_MASTER, 100);
        potion(Material.POTION, PotionType.LUCK, 80);
        potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 100);
        potion(Material.SPLASH_POTION, PotionType.STRONG_STRENGTH, 140);
        potion(Material.SPLASH_POTION, PotionType.STRONG_SWIFTNESS, 90);
        potion(Material.SPLASH_POTION, PotionType.STRONG_REGENERATION, 120);
        potion(Material.SPLASH_POTION, PotionType.FIRE_RESISTANCE, 80);
        potion(Material.SPLASH_POTION, PotionType.STRONG_HARMING, 100);
        potion(Material.SPLASH_POTION, PotionType.STRONG_POISON, 80);
        potion(Material.SPLASH_POTION, PotionType.SLOWNESS, 40);
        potion(Material.SPLASH_POTION, PotionType.WEAKNESS, 40);

        // ---- Misc ----
        Category m = Category.MISC;
        simple(m, Material.COAL, 3);
        simple(m, Material.REDSTONE, 3);
        simple(m, Material.LAPIS_LAZULI, 3);
        simple(m, Material.QUARTZ, 5);
        simple(m, Material.IRON_INGOT, 10);
        simple(m, Material.GOLD_INGOT, 20);
        simple(m, Material.EMERALD, 40);
        simple(m, Material.DIAMOND, 100);
        simple(m, Material.NETHERITE_INGOT, 400);
        simple(m, Material.STRING, 2);
        simple(m, Material.BONE, 2);
        simple(m, Material.GUNPOWDER, 8);
        simple(m, Material.SLIME_BALL, 8);
        simple(m, Material.BLAZE_ROD, 30);
        simple(m, Material.EXPERIENCE_BOTTLE, 20);
        simple(m, Material.FIREWORK_ROCKET, 10);
        simple(m, Material.WATER_BUCKET, 15);
        simple(m, Material.LAVA_BUCKET, 40);
        simple(m, Material.TNT, 40);
        simple(m, Material.PISTON, 15);
        simple(m, Material.STICKY_PISTON, 25);
        simple(m, Material.OBSERVER, 12);
        simple(m, Material.HOPPER, 30);
        simple(m, Material.NAME_TAG, 150);
        simple(m, Material.SADDLE, 100);
        simple(m, Material.LEAD, 20);
        simple(m, Material.ANVIL, 100);
        simple(m, Material.ENCHANTING_TABLE, 200);
        simple(m, Material.ENDER_CHEST, 200);
        simple(m, Material.END_CRYSTAL, 300);
        simple(m, Material.SHULKER_BOX, 600);
        simple(m, Material.BEACON, 5000);
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

    private static double unitSell(Entry entry) {
        return EconomyManager.round(entry.price() * SELL_RATIO);
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
        for (int i = 0; i < entries.size() && i < MAX_PER_CATEGORY; i++) {
            Entry entry = entries.get(i);
            ItemStack display = entry.item().clone();
            ItemMeta meta = display.getItemMeta();
            meta.lore(List.of(
                    line("Price: " + EconomyManager.format(entry.price()) + " each", NamedTextColor.GOLD),
                    line("Sells for: " + EconomyManager.format(unitSell(entry)) + " each (/sell)",
                            NamedTextColor.YELLOW),
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

    // ---------- selling ----------

    private Entry find(ItemStack stack) {
        Material type = stack.getType();
        boolean exact = type == Material.SPAWNER || type == Material.POTION || type == Material.SPLASH_POTION;
        if (!exact && stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            // never sell enchanted/renamed items or containers that may hold items
            if (meta.hasEnchants() || meta.hasDisplayName() || meta instanceof BlockStateMeta) {
                return null;
            }
        }
        for (List<Entry> list : catalog.values()) {
            for (Entry entry : list) {
                if (exact) {
                    if (entry.item().isSimilar(stack)) {
                        return entry;
                    }
                } else if (entry.item().getType() == type) {
                    return entry;
                }
            }
        }
        return null;
    }

    public void sellHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage(Component.text("Hold the item you want to sell.", NamedTextColor.RED));
            return;
        }
        Entry entry = find(hand);
        if (entry == null) {
            player.sendMessage(Component.text("That item can't be sold.", NamedTextColor.RED));
            return;
        }
        int amount = hand.getAmount();
        double total = EconomyManager.round(unitSell(entry) * amount);
        player.getInventory().setItemInMainHand(null);
        plugin.economy().add(player.getUniqueId(), total);
        player.sendMessage(Component.text("Sold " + amount + "x " + entry.name() + " for "
                + EconomyManager.format(total) + ".", NamedTextColor.GREEN));
    }

    public void sellAll(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        double total = 0;
        int count = 0;
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            Entry entry = find(stack);
            // gear is skipped so tools and armor are never sold by accident
            if (entry == null || entry.category() == Category.GEAR) {
                continue;
            }
            total += unitSell(entry) * stack.getAmount();
            count += stack.getAmount();
            contents[i] = null;
        }
        if (count == 0) {
            player.sendMessage(Component.text("You have nothing sellable. (Gear is skipped; hold it and use /sell.)",
                    NamedTextColor.RED));
            return;
        }
        total = EconomyManager.round(total);
        player.getInventory().setStorageContents(contents);
        plugin.economy().add(player.getUniqueId(), total);
        player.sendMessage(Component.text("Sold " + count + " items for " + EconomyManager.format(total) + ".",
                NamedTextColor.GREEN));
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
        if (slot >= 0 && slot < entries.size() && slot < MAX_PER_CATEGORY) {
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
