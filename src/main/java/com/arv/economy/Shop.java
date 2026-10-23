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
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Paged, category-based shop GUI.
 *
 * Main menu -> category (28 items per page, previous/next buttons, back button).
 * Prices live in {@link ShopCatalog}; spawners and potions are defined below.
 */
public class Shop implements Listener {

    /** Fraction of the shop price you get back when selling. */
    public static final double SELL_RATIO = 0.5;

    public enum Category {
        BLOCKS("Blocks", Material.GRASS_BLOCK, 10, NamedTextColor.GREEN,
                "Stone, wood, bricks, earth and storage blocks", true),
        DECORATION("Decoration", Material.LANTERN, 12, NamedTextColor.AQUA,
                "Colored blocks, glass, lighting and furniture", true),
        FARMING("Farming & Food", Material.WHEAT, 14, NamedTextColor.GOLD,
                "Seeds, crops, saplings and cooked food", true),
        COMBAT("Combat & Tools", Material.DIAMOND_SWORD, 16, NamedTextColor.RED,
                "Diamond and netherite gear, weapons and utility", false),
        REDSTONE("Redstone", Material.REDSTONE, 28, NamedTextColor.DARK_RED,
                "Components, pistons, rails and storage", true),
        POTIONS("Potions", Material.POTION, 30, NamedTextColor.LIGHT_PURPLE,
                "Drinkable and splash potions", true),
        SPAWNERS("Spawners", Material.SPAWNER, 32, NamedTextColor.DARK_PURPLE,
                "Mob spawners", true),
        MISC("Miscellaneous", Material.ENDER_CHEST, 34, NamedTextColor.YELLOW,
                "Resources, ores, mob drops, workstations and rares", true);

        final String title;
        final Material icon;
        final int slot;
        final NamedTextColor color;
        final String blurb;
        /** Whether /sell all may sell this category (gear is skipped so it is never sold by accident). */
        final boolean sellAll;

        Category(String title, Material icon, int slot, NamedTextColor color, String blurb, boolean sellAll) {
            this.title = title;
            this.icon = icon;
            this.slot = slot;
            this.color = color;
            this.blurb = blurb;
            this.sellAll = sellAll;
        }
    }

    private record Entry(Category category, String name, double price, ItemStack item) {}

    private static final class MenuHolder implements InventoryHolder {
        private final Category category; // null = main menu
        private final int page;
        private Inventory inventory;

        MenuHolder(Category category, int page) {
            this.category = category;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // ---------- layout ----------

    private static final int MAIN_SIZE = 45;
    private static final int MAIN_INFO_SLOT = 22;
    private static final int MAIN_BALANCE_SLOT = 40;

    private static final int CAT_SIZE = 54;
    private static final int HEADER_SLOT = 4;
    private static final int BACK_SLOT = 45;
    private static final int PREV_SLOT = 48;
    private static final int PAGE_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int BALANCE_SLOT = 53;

    /** 4 rows x 7 columns of item slots inside the border. */
    private static final int[] ITEM_SLOTS = new int[28];
    /** Inventory slot -> index within the page (or -1). */
    private static final int[] SLOT_INDEX = new int[CAT_SIZE];
    private static final int PAGE_SIZE = ITEM_SLOTS.length;

    static {
        Arrays.fill(SLOT_INDEX, -1);
        int n = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                int slot = row * 9 + col;
                ITEM_SLOTS[n] = slot;
                SLOT_INDEX[slot] = n;
                n++;
            }
        }
    }

    private final EconomyPlugin plugin;
    private final Map<Category, List<Entry>> catalog = new EnumMap<>(Category.class);
    private final Map<Material, Entry> plainIndex = new HashMap<>();
    private final List<Entry> exactEntries = new ArrayList<>();

    public Shop(EconomyPlugin plugin) {
        this.plugin = plugin;
        buildCatalog();
    }

    // ---------- catalog ----------

    private static boolean isExact(Material type) {
        return type == Material.SPAWNER || type == Material.POTION || type == Material.SPLASH_POTION;
    }

    private void add(Entry entry) {
        catalog.computeIfAbsent(entry.category(), k -> new ArrayList<>()).add(entry);
        Material type = entry.item().getType();
        if (isExact(type)) {
            exactEntries.add(entry);
        } else {
            plainIndex.putIfAbsent(type, entry);
        }
    }

    /** Loads "MATERIAL PRICE" lines; unknown materials are skipped so a version mismatch never breaks the shop. */
    private void load(Category category, String table) {
        for (String raw : table.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\s+");
            Material material = Material.matchMaterial(parts[0]);
            if (material == null || !material.isItem() || material.isAir()) {
                plugin.getLogger().warning("Shop: skipping unknown item " + parts[0]);
                continue;
            }
            add(new Entry(category, pretty(material.name()), Double.parseDouble(parts[1]),
                    new ItemStack(material)));
        }
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
        load(Category.BLOCKS, ShopCatalog.BLOCKS);
        load(Category.DECORATION, ShopCatalog.DECORATION);
        load(Category.FARMING, ShopCatalog.FARMING);
        load(Category.COMBAT, ShopCatalog.COMBAT);
        load(Category.REDSTONE, ShopCatalog.REDSTONE);
        load(Category.MISC, ShopCatalog.MISC);

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

    private int pageCount(Category category) {
        int size = catalog.getOrDefault(category, List.of()).size();
        return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    // ---------- menu helpers ----------

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

    private static ItemStack button(Material material, String name, NamedTextColor color, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(line(name, color).decorate(TextDecoration.BOLD));
        List<Component> lines = new ArrayList<>();
        for (String text : lore) {
            lines.add(line(text, NamedTextColor.GRAY));
        }
        meta.lore(lines);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack balanceItem(Player player) {
        ItemStack stack = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(line("Balance: " + EconomyManager.format(plugin.economy().get(player.getUniqueId())),
                NamedTextColor.GOLD));
        stack.setItemMeta(meta);
        return stack;
    }

    private static void click(Player player) {
        player.playSound(player.getLocation(), "ui.button.click", 0.6f, 1.0f);
    }

    // ---------- main menu ----------

    public void openMain(Player player) {
        MenuHolder holder = new MenuHolder(null, 0);
        Inventory inv = Bukkit.createInventory(holder, MAIN_SIZE,
                Component.text("Shop - Categories", NamedTextColor.DARK_GRAY));
        holder.inventory = inv;
        for (int i = 0; i < MAIN_SIZE; i++) {
            inv.setItem(i, filler());
        }
        inv.setItem(HEADER_SLOT, button(Material.NETHER_STAR, "Shop", NamedTextColor.GOLD,
                "Pick a category to browse."));
        for (Category category : Category.values()) {
            int items = catalog.getOrDefault(category, List.of()).size();
            ItemStack icon = new ItemStack(category.icon);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(line(category.title, category.color).decorate(TextDecoration.BOLD));
            meta.lore(List.of(
                    line(category.blurb, NamedTextColor.GRAY),
                    line(items + " items", NamedTextColor.DARK_GRAY),
                    line("Click to browse", NamedTextColor.YELLOW)));
            icon.setItemMeta(meta);
            inv.setItem(category.slot, icon);
        }
        inv.setItem(MAIN_INFO_SLOT, button(Material.BOOK, "How it works", NamedTextColor.AQUA,
                "Left-click: buy 1",
                "Shift-click: buy 16",
                "Right-click: buy 64",
                "/sell sells the item in your hand",
                "/sell all sells everything sellable",
                "(gear is never sold by /sell all)"));
        inv.setItem(MAIN_BALANCE_SLOT, balanceItem(player));
        player.openInventory(inv);
    }

    // ---------- category pages ----------

    private void openCategory(Player player, Category category, int requestedPage) {
        int pages = pageCount(category);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        List<Entry> entries = catalog.getOrDefault(category, List.of());

        MenuHolder holder = new MenuHolder(category, page);
        Inventory inv = Bukkit.createInventory(holder, CAT_SIZE,
                Component.text("Shop - " + category.title + " (" + (page + 1) + "/" + pages + ")",
                        NamedTextColor.DARK_GRAY));
        holder.inventory = inv;

        for (int i = 0; i < CAT_SIZE; i++) {
            if (SLOT_INDEX[i] < 0) {
                inv.setItem(i, filler());
            }
        }

        // header
        ItemStack header = new ItemStack(category.icon);
        ItemMeta headerMeta = header.getItemMeta();
        headerMeta.displayName(line(category.title, category.color).decorate(TextDecoration.BOLD));
        headerMeta.lore(List.of(
                line(category.blurb, NamedTextColor.GRAY),
                line(entries.size() + " items - page " + (page + 1) + " of " + pages, NamedTextColor.DARK_GRAY)));
        header.setItemMeta(headerMeta);
        inv.setItem(HEADER_SLOT, header);

        // item grid
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < entries.size(); i++) {
            Entry entry = entries.get(start + i);
            ItemStack display = entry.item().clone();
            ItemMeta meta = display.getItemMeta();
            List<Component> lore = new ArrayList<>();
            lore.add(line("Price: " + EconomyManager.format(entry.price()) + " each", NamedTextColor.GOLD));
            lore.add(line("Sells for: " + EconomyManager.format(unitSell(entry)) + " each", NamedTextColor.YELLOW));
            if (display.getMaxStackSize() > 1) {
                lore.add(line("Left-click: buy 1", NamedTextColor.GRAY));
                lore.add(line("Shift-click: buy " + Math.min(16, display.getMaxStackSize()), NamedTextColor.GRAY));
                if (display.getMaxStackSize() >= 64) {
                    lore.add(line("Right-click: buy 64", NamedTextColor.GRAY));
                }
            } else {
                lore.add(line("Click: buy 1", NamedTextColor.GRAY));
            }
            meta.lore(lore);
            display.setItemMeta(meta);
            inv.setItem(ITEM_SLOTS[i], display);
        }

        // navigation
        inv.setItem(BACK_SLOT, button(Material.BARRIER, "Back to categories", NamedTextColor.RED,
                "Return to the main menu"));
        if (page > 0) {
            inv.setItem(PREV_SLOT, button(Material.ARROW, "Previous page", NamedTextColor.YELLOW,
                    "Go to page " + page));
        }
        inv.setItem(PAGE_SLOT, button(Material.PAPER, "Page " + (page + 1) + " of " + pages, NamedTextColor.WHITE,
                category.title));
        if (page < pages - 1) {
            inv.setItem(NEXT_SLOT, button(Material.ARROW, "Next page", NamedTextColor.YELLOW,
                    "Go to page " + (page + 2)));
        }
        inv.setItem(BALANCE_SLOT, balanceItem(player));
        player.openInventory(inv);
    }

    // ---------- buying ----------

    private void buy(Player player, Entry entry, int requested) {
        EconomyManager eco = plugin.economy();
        int quantity = Math.max(1, Math.min(requested, entry.item().getMaxStackSize()));
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
        player.playSound(player.getLocation(), "entity.experience_orb.pickup", 0.6f, 1.2f);
    }

    // ---------- selling ----------

    private Entry find(ItemStack stack) {
        Material type = stack.getType();
        boolean exact = isExact(type);
        if (!exact && stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            // never sell enchanted/renamed items or containers that may hold items
            if (meta.hasEnchants() || meta.hasDisplayName() || meta instanceof BlockStateMeta) {
                return null;
            }
        }
        if (exact) {
            for (Entry entry : exactEntries) {
                if (entry.item().isSimilar(stack)) {
                    return entry;
                }
            }
            return null;
        }
        return plainIndex.get(type);
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
            // gear (Combat & Tools) is skipped so tools and armor are never sold by accident
            if (entry == null || !entry.category().sellAll) {
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

        // main menu
        if (holder.category == null) {
            for (Category category : Category.values()) {
                if (category.slot == slot) {
                    click(player);
                    Bukkit.getScheduler().runTask(plugin, () -> openCategory(player, category, 0));
                    return;
                }
            }
            return;
        }

        // category page navigation
        Category category = holder.category;
        int page = holder.page;
        if (slot == BACK_SLOT) {
            click(player);
            Bukkit.getScheduler().runTask(plugin, () -> openMain(player));
            return;
        }
        if (slot == PREV_SLOT && page > 0) {
            click(player);
            Bukkit.getScheduler().runTask(plugin, () -> openCategory(player, category, page - 1));
            return;
        }
        if (slot == NEXT_SLOT && page < pageCount(category) - 1) {
            click(player);
            Bukkit.getScheduler().runTask(plugin, () -> openCategory(player, category, page + 1));
            return;
        }

        // item grid
        int index = slot >= 0 && slot < CAT_SIZE ? SLOT_INDEX[slot] : -1;
        if (index < 0) {
            return;
        }
        List<Entry> entries = catalog.getOrDefault(category, List.of());
        int entryIndex = page * PAGE_SIZE + index;
        if (entryIndex < entries.size()) {
            int quantity = event.isShiftClick() ? 16 : event.isRightClick() ? 64 : 1;
            buy(player, entries.get(entryIndex), quantity);
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
