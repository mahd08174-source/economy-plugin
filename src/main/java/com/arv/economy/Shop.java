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
        simple(g, Material.IRON_SWORD, 8000);
        simple(g, Material.IRON_PICKAXE, 10000);
        simple(g, Material.IRON_AXE, 9000);
        simple(g, Material.IRON_SHOVEL, 6000);
        simple(g, Material.IRON_HELMET, 15000);
        simple(g, Material.IRON_CHESTPLATE, 24000);
        simple(g, Material.IRON_LEGGINGS, 21000);
        simple(g, Material.IRON_BOOTS, 12000);
        simple(g, Material.DIAMOND_SWORD, 30000);
        simple(g, Material.DIAMOND_PICKAXE, 40000);
        simple(g, Material.DIAMOND_AXE, 36000);
        simple(g, Material.DIAMOND_SHOVEL, 20000);
        simple(g, Material.DIAMOND_HELMET, 60000);
        simple(g, Material.DIAMOND_CHESTPLATE, 96000);
        simple(g, Material.DIAMOND_LEGGINGS, 84000);
        simple(g, Material.DIAMOND_BOOTS, 48000);
        simple(g, Material.NETHERITE_SWORD, 180000);
        simple(g, Material.NETHERITE_PICKAXE, 190000);
        simple(g, Material.NETHERITE_AXE, 200000);
        simple(g, Material.NETHERITE_SHOVEL, 180000);
        simple(g, Material.NETHERITE_HELMET, 240000);
        simple(g, Material.NETHERITE_CHESTPLATE, 300000);
        simple(g, Material.NETHERITE_LEGGINGS, 280000);
        simple(g, Material.NETHERITE_BOOTS, 200000);
        simple(g, Material.BOW, 16000);
        simple(g, Material.CROSSBOW, 20000);
        simple(g, Material.ARROW, 400);
        simple(g, Material.SHIELD, 12000);
        simple(g, Material.TRIDENT, 240000);
        simple(g, Material.MACE, 600000);
        simple(g, Material.WIND_CHARGE, 4000);
        simple(g, Material.ELYTRA, 500000);
        simple(g, Material.TOTEM_OF_UNDYING, 120000);
        simple(g, Material.TURTLE_HELMET, 30000);
        simple(g, Material.ENDER_PEARL, 10000);
        simple(g, Material.FISHING_ROD, 8000);
        simple(g, Material.SHEARS, 4000);
        simple(g, Material.FLINT_AND_STEEL, 4000);

        // ---- Blocks ----
        Category b = Category.BLOCKS;
        simple(b, Material.COBBLESTONE, 200);
        simple(b, Material.STONE, 400);
        simple(b, Material.DIRT, 200);
        simple(b, Material.GRAVEL, 200);
        simple(b, Material.SAND, 400);
        simple(b, Material.ANDESITE, 400);
        simple(b, Material.DIORITE, 400);
        simple(b, Material.GRANITE, 400);
        simple(b, Material.DEEPSLATE, 600);
        simple(b, Material.NETHERRACK, 200);
        simple(b, Material.END_STONE, 1600);
        simple(b, Material.BLACKSTONE, 800);
        simple(b, Material.OAK_PLANKS, 200);
        simple(b, Material.OAK_LOG, 800);
        simple(b, Material.SPRUCE_LOG, 800);
        simple(b, Material.BIRCH_LOG, 800);
        simple(b, Material.DARK_OAK_LOG, 800);
        simple(b, Material.GLASS, 800);
        simple(b, Material.STONE_BRICKS, 600);
        simple(b, Material.DEEPSLATE_BRICKS, 1000);
        simple(b, Material.BRICKS, 1200);
        simple(b, Material.SANDSTONE, 800);
        simple(b, Material.TERRACOTTA, 1000);
        simple(b, Material.WHITE_CONCRETE, 1000);
        simple(b, Material.WHITE_WOOL, 1000);
        simple(b, Material.PRISMARINE, 1600);
        simple(b, Material.BOOKSHELF, 5000);
        simple(b, Material.ICE, 1000);
        simple(b, Material.PACKED_ICE, 2000);
        simple(b, Material.HAY_BLOCK, 2400);
        simple(b, Material.SOUL_SAND, 1200);
        simple(b, Material.OBSIDIAN, 6000);
        simple(b, Material.CRYING_OBSIDIAN, 12000);
        simple(b, Material.QUARTZ_BLOCK, 3000);
        simple(b, Material.GLOWSTONE, 4000);
        simple(b, Material.SEA_LANTERN, 5000);
        simple(b, Material.COAL_BLOCK, 6000);
        simple(b, Material.COPPER_BLOCK, 12000);
        simple(b, Material.LAPIS_BLOCK, 12000);
        simple(b, Material.REDSTONE_BLOCK, 9000);
        simple(b, Material.IRON_BLOCK, 18000);
        simple(b, Material.GOLD_BLOCK, 36000);
        simple(b, Material.EMERALD_BLOCK, 72000);
        simple(b, Material.DIAMOND_BLOCK, 120000);
        simple(b, Material.NETHERITE_BLOCK, 720000);

        // ---- Food ----
        Category f = Category.FOOD;
        simple(f, Material.APPLE, 600);
        simple(f, Material.BREAD, 600);
        simple(f, Material.CARROT, 400);
        simple(f, Material.POTATO, 400);
        simple(f, Material.BAKED_POTATO, 600);
        simple(f, Material.BEETROOT, 400);
        simple(f, Material.SWEET_BERRIES, 400);
        simple(f, Material.GLOW_BERRIES, 600);
        simple(f, Material.DRIED_KELP, 200);
        simple(f, Material.COOKIE, 200);
        simple(f, Material.MELON_SLICE, 200);
        simple(f, Material.COOKED_COD, 1000);
        simple(f, Material.COOKED_SALMON, 1000);
        simple(f, Material.COOKED_CHICKEN, 1000);
        simple(f, Material.COOKED_RABBIT, 1000);
        simple(f, Material.COOKED_MUTTON, 1200);
        simple(f, Material.COOKED_PORKCHOP, 1200);
        simple(f, Material.COOKED_BEEF, 1200);
        simple(f, Material.PUMPKIN_PIE, 1200);
        simple(f, Material.CHORUS_FRUIT, 1200);
        simple(f, Material.MUSHROOM_STEW, 1600);
        simple(f, Material.BEETROOT_SOUP, 1200);
        simple(f, Material.RABBIT_STEW, 2400);
        simple(f, Material.HONEY_BOTTLE, 1600);
        simple(f, Material.GOLDEN_CARROT, 2400);
        simple(f, Material.CAKE, 6000);
        simple(f, Material.GOLDEN_APPLE, 12000);
        simple(f, Material.ENCHANTED_GOLDEN_APPLE, 160000);

        // ---- Spawners ----
        spawner(EntityType.PIG, 200000);
        spawner(EntityType.COW, 200000);
        spawner(EntityType.SHEEP, 200000);
        spawner(EntityType.CHICKEN, 200000);
        spawner(EntityType.ZOMBIE, 300000);
        spawner(EntityType.SKELETON, 300000);
        spawner(EntityType.SPIDER, 300000);
        spawner(EntityType.SILVERFISH, 300000);
        spawner(EntityType.CAVE_SPIDER, 360000);
        spawner(EntityType.SLIME, 400000);
        spawner(EntityType.ENDERMITE, 400000);
        spawner(EntityType.WITCH, 440000);
        spawner(EntityType.CREEPER, 500000);
        spawner(EntityType.MAGMA_CUBE, 500000);
        spawner(EntityType.ENDERMAN, 600000);
        spawner(EntityType.ZOMBIFIED_PIGLIN, 600000);
        spawner(EntityType.BLAZE, 700000);
        spawner(EntityType.IRON_GOLEM, 800000);
        spawner(EntityType.WITHER_SKELETON, 1000000);

        // ---- Potions ----
        potion(Material.POTION, PotionType.STRONG_HEALING, 16000);
        potion(Material.POTION, PotionType.STRONG_STRENGTH, 24000);
        potion(Material.POTION, PotionType.STRONG_SWIFTNESS, 14000);
        potion(Material.POTION, PotionType.STRONG_LEAPING, 12000);
        potion(Material.POTION, PotionType.FIRE_RESISTANCE, 12000);
        potion(Material.POTION, PotionType.STRONG_REGENERATION, 20000);
        potion(Material.POTION, PotionType.NIGHT_VISION, 10000);
        potion(Material.POTION, PotionType.WATER_BREATHING, 10000);
        potion(Material.POTION, PotionType.SLOW_FALLING, 10000);
        potion(Material.POTION, PotionType.INVISIBILITY, 12000);
        potion(Material.POTION, PotionType.TURTLE_MASTER, 20000);
        potion(Material.POTION, PotionType.LUCK, 16000);
        potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING, 20000);
        potion(Material.SPLASH_POTION, PotionType.STRONG_STRENGTH, 28000);
        potion(Material.SPLASH_POTION, PotionType.STRONG_SWIFTNESS, 18000);
        potion(Material.SPLASH_POTION, PotionType.STRONG_REGENERATION, 24000);
        potion(Material.SPLASH_POTION, PotionType.FIRE_RESISTANCE, 16000);
        potion(Material.SPLASH_POTION, PotionType.STRONG_HARMING, 20000);
        potion(Material.SPLASH_POTION, PotionType.STRONG_POISON, 16000);
        potion(Material.SPLASH_POTION, PotionType.SLOWNESS, 8000);
        potion(Material.SPLASH_POTION, PotionType.WEAKNESS, 8000);

        // ---- Misc ----
        Category m = Category.MISC;
        simple(m, Material.COAL, 600);
        simple(m, Material.REDSTONE, 600);
        simple(m, Material.LAPIS_LAZULI, 600);
        simple(m, Material.QUARTZ, 1000);
        simple(m, Material.IRON_INGOT, 2000);
        simple(m, Material.GOLD_INGOT, 4000);
        simple(m, Material.EMERALD, 8000);
        simple(m, Material.DIAMOND, 20000);
        simple(m, Material.NETHERITE_INGOT, 80000);
        simple(m, Material.STRING, 400);
        simple(m, Material.BONE, 400);
        simple(m, Material.GUNPOWDER, 1600);
        simple(m, Material.SLIME_BALL, 1600);
        simple(m, Material.BLAZE_ROD, 6000);
        simple(m, Material.EXPERIENCE_BOTTLE, 4000);
        simple(m, Material.FIREWORK_ROCKET, 2000);
        simple(m, Material.WATER_BUCKET, 3000);
        simple(m, Material.LAVA_BUCKET, 8000);
        simple(m, Material.TNT, 8000);
        simple(m, Material.PISTON, 3000);
        simple(m, Material.STICKY_PISTON, 5000);
        simple(m, Material.OBSERVER, 2400);
        simple(m, Material.HOPPER, 6000);
        simple(m, Material.NAME_TAG, 30000);
        simple(m, Material.SADDLE, 20000);
        simple(m, Material.LEAD, 4000);
        simple(m, Material.ANVIL, 20000);
        simple(m, Material.ENCHANTING_TABLE, 40000);
        simple(m, Material.ENDER_CHEST, 40000);
        simple(m, Material.END_CRYSTAL, 60000);
        simple(m, Material.SHULKER_BOX, 120000);
        simple(m, Material.BEACON, 1000000);
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
