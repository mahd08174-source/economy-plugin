package com.arv.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
                "Click an item to inspect it",
                "Set the quantity, add enchantments,",
                "then buy or sell from the inspector.",
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
            lore.add(line("Click to inspect, set quantity", NamedTextColor.GRAY));
            lore.add(line("and buy or sell", NamedTextColor.GRAY));
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

    /** Charges for and hands over {@code quantity} copies of {@code template}. */
    private void purchase(Player player, String name, ItemStack template, double unitPrice, int quantity) {
        EconomyManager eco = plugin.economy();
        double total = EconomyManager.round(unitPrice * quantity);
        if (eco.get(player.getUniqueId()) + 0.001 < total) {
            player.sendMessage(Component.text("You need " + EconomyManager.format(total) + " to buy that.",
                    NamedTextColor.RED));
            return;
        }
        ItemStack give = template.clone();
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
        double cost = EconomyManager.round(unitPrice * given);
        eco.withdraw(player.getUniqueId(), cost);
        player.sendMessage(Component.text("Bought " + given + "x " + name + " for "
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

    /** Money a stack would pay when sold, or -1 when it can't be sold (used by the sell menu). */
    public double sellQuote(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return -1;
        }
        Entry entry = find(stack);
        if (entry == null) {
            return -1;
        }
        return EconomyManager.round(unitSell(entry) * stack.getAmount());
    }

    // ---------- item inspector ----------

    private enum Mode { DETAIL, ENCHANT }

    /** State shared between the inspector and its enchantment sub-menu. */
    private static final class Session {
        final Category category;   // where "back" returns to
        final int page;
        final Entry entry;
        final List<Enchantment> applicable;
        final Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
        int quantity = 1;
        int enchantPage = 0;

        Session(Category category, int page, Entry entry, List<Enchantment> applicable) {
            this.category = category;
            this.page = page;
            this.entry = entry;
            this.applicable = applicable;
        }
    }

    private static final class InspectHolder implements InventoryHolder {
        private final Session session;
        private final Mode mode;
        private Inventory inventory;

        InspectHolder(Session session, Mode mode) {
            this.session = session;
            this.mode = mode;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int INSPECT_SIZE = 54;
    private static final int ITEM_CENTER_SLOT = 22;
    private static final int QTY_DISPLAY_SLOT = 13;
    private static final int SUMMARY_SLOT = 31;
    private static final int BUY_SLOT = 38;
    private static final int ENCHANT_SLOT = 40;
    private static final int SELL_SLOT = 42;
    private static final int CLEAR_SLOT = 47;
    private static final int ENCH_SUMMARY_SLOT = 51;
    private static final int MAX_QUANTITY = 64;
    /** Quantity buttons sit left (minus) and right (plus) of the centered item. */
    private static final int[] QTY_SLOTS = {18, 19, 20, 21, 23, 24, 25, 26};
    private static final int[] QTY_DELTAS = {-64, -32, -16, -1, 1, 16, 32, 64};

    /** Cost of one enchantment level = ENCHANT_UNIT_PRICE x weight x level. */
    private static final double ENCHANT_UNIT_PRICE = 20_000.0;
    private static final Map<String, Double> ENCHANT_WEIGHTS = Map.ofEntries(
            Map.entry("mending", 8.0), Map.entry("silk_touch", 4.0), Map.entry("infinity", 4.0),
            Map.entry("swift_sneak", 4.0), Map.entry("channeling", 3.0), Map.entry("riptide", 3.0),
            Map.entry("multishot", 3.0), Map.entry("frost_walker", 3.0), Map.entry("soul_speed", 3.0),
            Map.entry("wind_burst", 3.0), Map.entry("density", 2.0), Map.entry("breach", 2.0),
            Map.entry("fortune", 2.0), Map.entry("looting", 2.0), Map.entry("thorns", 2.0),
            Map.entry("sharpness", 2.0), Map.entry("protection", 2.0), Map.entry("power", 2.0),
            Map.entry("efficiency", 2.0), Map.entry("unbreaking", 2.0),
            Map.entry("binding_curse", 0.25), Map.entry("vanishing_curse", 0.25));

    private static String enchantName(Enchantment e) {
        return pretty(e.getKey().getKey());
    }

    private static double enchantCost(Enchantment e, int level) {
        double weight = ENCHANT_WEIGHTS.getOrDefault(e.getKey().getKey(), 1.0);
        return EconomyManager.round(ENCHANT_UNIT_PRICE * weight * level);
    }

    /** Extra cost per single item for the currently selected enchantments. */
    private static double enchantCostPerItem(Session s) {
        double sum = 0;
        for (Map.Entry<Enchantment, Integer> e : s.enchants.entrySet()) {
            sum += enchantCost(e.getKey(), e.getValue());
        }
        return EconomyManager.round(sum);
    }

    /** Vanilla and datapack (custom) enchantments that can legally go on this item. Books accept any. */
    private static List<Enchantment> applicableEnchants(ItemStack item) {
        List<Enchantment> list = new ArrayList<>();
        boolean book = item.getType() == Material.BOOK;
        for (Enchantment e : Registry.ENCHANTMENT) {
            if (book || e.canEnchantItem(item)) {
                list.add(e);
            }
        }
        list.sort(Comparator.comparing(Shop::enchantName));
        return list;
    }

    /** Builds the item the player will actually receive (books become enchanted books). */
    private static ItemStack applyEnchants(ItemStack base, Map<Enchantment, Integer> enchants) {
        if (enchants.isEmpty()) {
            return base.clone();
        }
        if (base.getType() == Material.BOOK) {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                meta.addStoredEnchant(e.getKey(), e.getValue(), true);
            }
            book.setItemMeta(meta);
            return book;
        }
        ItemStack copy = base.clone();
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            copy.addUnsafeEnchantment(e.getKey(), e.getValue());
        }
        return copy;
    }

    private int countOwned(Player player, Entry entry) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (stack != null && !stack.getType().isAir() && find(stack) == entry) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void removeOwned(Player player, Entry entry, int amount) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        int left = amount;
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType().isAir() || find(stack) != entry) {
                continue;
            }
            int take = Math.min(left, stack.getAmount());
            if (take == stack.getAmount()) {
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - take);
            }
            left -= take;
        }
        player.getInventory().setStorageContents(contents);
    }

    private void openInspector(Player player, Session session) {
        InspectHolder holder = new InspectHolder(session, Mode.DETAIL);
        Inventory inv = Bukkit.createInventory(holder, INSPECT_SIZE,
                Component.text("Inspect - " + session.entry.name(), NamedTextColor.DARK_GRAY));
        holder.inventory = inv;
        renderDetail(inv, session, player);
        player.openInventory(inv);
    }

    private void openEnchantMenu(Player player, Session session) {
        InspectHolder holder = new InspectHolder(session, Mode.ENCHANT);
        Inventory inv = Bukkit.createInventory(holder, INSPECT_SIZE,
                Component.text("Enchant - " + session.entry.name(), NamedTextColor.DARK_GRAY));
        holder.inventory = inv;
        renderEnchant(inv, session, player);
        player.openInventory(inv);
    }

    /** The item as it will be delivered, with price and enchantment lore, for the inspector preview. */
    private ItemStack preview(Session s) {
        ItemStack shown = applyEnchants(s.entry.item(), s.enchants);
        ItemMeta meta = shown.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(line("Base price: " + EconomyManager.format(s.entry.price()), NamedTextColor.GOLD));
        for (Map.Entry<Enchantment, Integer> e : s.enchants.entrySet()) {
            lore.add(line("+ " + enchantName(e.getKey()) + " " + e.getValue() + "  ("
                    + EconomyManager.format(enchantCost(e.getKey(), e.getValue())) + ")", NamedTextColor.AQUA));
        }
        meta.lore(lore);
        shown.setItemMeta(meta);
        return shown;
    }

    private void renderDetail(Inventory inv, Session s, Player player) {
        for (int i = 0; i < INSPECT_SIZE; i++) {
            inv.setItem(i, filler());
        }
        double enchPer = enchantCostPerItem(s);
        double unit = EconomyManager.round(s.entry.price() + enchPer);
        double total = EconomyManager.round(unit * s.quantity);
        int owned = countOwned(player, s.entry);
        int sellAmount = Math.min(s.quantity, owned);
        double sellTotal = EconomyManager.round(unitSell(s.entry) * sellAmount);
        boolean canAfford = plugin.economy().get(player.getUniqueId()) + 0.001 >= total;

        inv.setItem(HEADER_SLOT, button(s.category.icon, "Inspecting: " + s.entry.name(), s.category.color,
                s.category.title));
        inv.setItem(ITEM_CENTER_SLOT, preview(s));
        inv.setItem(QTY_DISPLAY_SLOT, button(Material.PAPER, "Quantity: " + s.quantity, NamedTextColor.WHITE,
                "Min 1, max " + MAX_QUANTITY));

        for (int i = 0; i < QTY_SLOTS.length; i++) {
            int delta = QTY_DELTAS[i];
            String label = (delta > 0 ? "+" : "-") + Math.abs(delta);
            ItemStack b = button(delta > 0 ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                    label, delta > 0 ? NamedTextColor.GREEN : NamedTextColor.RED,
                    "Quantity " + (delta > 0 ? "up" : "down") + " by " + Math.abs(delta));
            b.setAmount(Math.abs(delta));
            inv.setItem(QTY_SLOTS[i], b);
        }

        List<String> summary = new ArrayList<>();
        summary.add("Unit price: " + EconomyManager.format(s.entry.price()));
        if (enchPer > 0) {
            summary.add("Enchantments: +" + EconomyManager.format(enchPer) + " each");
        }
        summary.add("Quantity: " + s.quantity);
        summary.add("Total: " + EconomyManager.format(total));
        summary.add("You own: " + owned);
        inv.setItem(SUMMARY_SLOT, button(Material.GOLD_INGOT, "Order total: " + EconomyManager.format(total),
                NamedTextColor.GOLD, summary.toArray(new String[0])));

        inv.setItem(BUY_SLOT, button(Material.LIME_CONCRETE,
                "Buy " + s.quantity + " for " + EconomyManager.format(total), NamedTextColor.GREEN,
                canAfford ? "Click to purchase" : "You can't afford this"));
        inv.setItem(SELL_SLOT, button(Material.ORANGE_CONCRETE,
                owned > 0 ? "Sell " + sellAmount + " for " + EconomyManager.format(sellTotal) : "Sell",
                NamedTextColor.GOLD,
                owned > 0 ? "Sells plain copies from your inventory" : "You have none to sell",
                "Enchanted or renamed items can't be sold"));
        if (!s.applicable.isEmpty()) {
            inv.setItem(ENCHANT_SLOT, button(Material.ENCHANTING_TABLE, "Enchantments", NamedTextColor.LIGHT_PURPLE,
                    s.enchants.isEmpty() ? "None selected" : s.enchants.size() + " selected",
                    "Click to choose enchantments"));
        }
        inv.setItem(BACK_SLOT, button(Material.BARRIER, "Back to " + s.category.title, NamedTextColor.RED,
                "Return to the item list"));
        inv.setItem(BALANCE_SLOT, balanceItem(player));
    }

    private void renderEnchant(Inventory inv, Session s, Player player) {
        int pages = Math.max(1, (s.applicable.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        s.enchantPage = Math.max(0, Math.min(s.enchantPage, pages - 1));
        for (int i = 0; i < INSPECT_SIZE; i++) {
            inv.setItem(i, SLOT_INDEX[i] < 0 ? filler() : null);
        }
        inv.setItem(HEADER_SLOT, preview(s));

        int start = s.enchantPage * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < s.applicable.size(); i++) {
            Enchantment e = s.applicable.get(start + i);
            int level = s.enchants.getOrDefault(e, 0);
            int max = e.getMaxLevel();
            ItemStack icon = new ItemStack(level > 0 ? Material.ENCHANTED_BOOK : Material.BOOK);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(line(enchantName(e), level > 0 ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                    .decorate(TextDecoration.BOLD));
            List<Component> lore = new ArrayList<>();
            lore.add(line(level > 0 ? "Level " + level + " / " + max : "Not selected (max level " + max + ")",
                    level > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            lore.add(line("Cost: " + EconomyManager.format(enchantCost(e, 1)) + " per level, per item",
                    NamedTextColor.GOLD));
            lore.add(line("Left-click: level up", NamedTextColor.GRAY));
            lore.add(line("Right-click: level down", NamedTextColor.GRAY));
            lore.add(line("Shift-click: max level / remove", NamedTextColor.GRAY));
            meta.lore(lore);
            icon.setItemMeta(meta);
            inv.setItem(ITEM_SLOTS[i], icon);
        }

        inv.setItem(BACK_SLOT, button(Material.BARRIER, "Back to item", NamedTextColor.RED,
                "Return to the inspector"));
        if (!s.enchants.isEmpty()) {
            inv.setItem(CLEAR_SLOT, button(Material.LAVA_BUCKET, "Clear all", NamedTextColor.RED,
                    "Remove every selected enchantment"));
        }
        if (s.enchantPage > 0) {
            inv.setItem(PREV_SLOT, button(Material.ARROW, "Previous page", NamedTextColor.YELLOW,
                    "Go to page " + s.enchantPage));
        }
        inv.setItem(PAGE_SLOT, button(Material.PAPER, "Page " + (s.enchantPage + 1) + " of " + pages,
                NamedTextColor.WHITE, s.applicable.size() + " enchantments"));
        if (s.enchantPage < pages - 1) {
            inv.setItem(NEXT_SLOT, button(Material.ARROW, "Next page", NamedTextColor.YELLOW,
                    "Go to page " + (s.enchantPage + 2)));
        }
        double perItem = enchantCostPerItem(s);
        inv.setItem(ENCH_SUMMARY_SLOT, button(Material.GOLD_INGOT, "Enchant cost: +" + EconomyManager.format(perItem),
                NamedTextColor.GOLD, "Per item. x" + s.quantity + " = " + EconomyManager.format(
                        EconomyManager.round(perItem * s.quantity))));
        inv.setItem(BALANCE_SLOT, balanceItem(player));
    }

    private void handleInspector(InventoryClickEvent event, InspectHolder holder) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory top = holder.getInventory();
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }
        int slot = event.getSlot();
        Session s = holder.session;

        if (holder.mode == Mode.DETAIL) {
            if (slot == BACK_SLOT) {
                click(player);
                Bukkit.getScheduler().runTask(plugin, () -> openCategory(player, s.category, s.page));
                return;
            }
            for (int i = 0; i < QTY_SLOTS.length; i++) {
                if (slot == QTY_SLOTS[i]) {
                    s.quantity = Math.max(1, Math.min(MAX_QUANTITY, s.quantity + QTY_DELTAS[i]));
                    click(player);
                    renderDetail(top, s, player);
                    return;
                }
            }
            if (slot == BUY_SLOT) {
                ItemStack template = applyEnchants(s.entry.item(), s.enchants);
                double unit = EconomyManager.round(s.entry.price() + enchantCostPerItem(s));
                String name = s.enchants.isEmpty() ? s.entry.name() : s.entry.name() + " (enchanted)";
                purchase(player, name, template, unit, s.quantity);
                renderDetail(top, s, player);
                return;
            }
            if (slot == SELL_SLOT) {
                int owned = countOwned(player, s.entry);
                if (owned <= 0) {
                    player.sendMessage(Component.text("You don't have any " + s.entry.name() + " to sell.",
                            NamedTextColor.RED));
                    return;
                }
                int amount = Math.min(s.quantity, owned);
                double total = EconomyManager.round(unitSell(s.entry) * amount);
                removeOwned(player, s.entry, amount);
                plugin.economy().add(player.getUniqueId(), total);
                player.sendMessage(Component.text("Sold " + amount + "x " + s.entry.name() + " for "
                        + EconomyManager.format(total) + ".", NamedTextColor.GREEN));
                player.playSound(player.getLocation(), "entity.experience_orb.pickup", 0.6f, 1.0f);
                renderDetail(top, s, player);
                return;
            }
            if (slot == ENCHANT_SLOT && !s.applicable.isEmpty()) {
                click(player);
                Bukkit.getScheduler().runTask(plugin, () -> openEnchantMenu(player, s));
            }
            return;
        }

        // enchantment menu
        if (slot == BACK_SLOT) {
            click(player);
            Bukkit.getScheduler().runTask(plugin, () -> openInspector(player, s));
            return;
        }
        if (slot == CLEAR_SLOT && !s.enchants.isEmpty()) {
            s.enchants.clear();
            click(player);
            renderEnchant(top, s, player);
            return;
        }
        if (slot == PREV_SLOT && s.enchantPage > 0) {
            s.enchantPage--;
            click(player);
            renderEnchant(top, s, player);
            return;
        }
        if (slot == NEXT_SLOT) {
            int pages = Math.max(1, (s.applicable.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            if (s.enchantPage < pages - 1) {
                s.enchantPage++;
                click(player);
                renderEnchant(top, s, player);
            }
            return;
        }
        int index = slot >= 0 && slot < INSPECT_SIZE ? SLOT_INDEX[slot] : -1;
        if (index < 0) {
            return;
        }
        int enchIndex = s.enchantPage * PAGE_SIZE + index;
        if (enchIndex >= s.applicable.size()) {
            return;
        }
        Enchantment e = s.applicable.get(enchIndex);
        int current = s.enchants.getOrDefault(e, 0);
        int max = e.getMaxLevel();
        int next;
        if (event.isShiftClick()) {
            next = current > 0 && !event.isRightClick() && current == max ? 0 : (event.isRightClick() ? 0 : max);
        } else if (event.isRightClick()) {
            next = current - 1;
        } else {
            next = current + 1;
        }
        next = Math.max(0, Math.min(max, next));
        if (next <= 0) {
            s.enchants.remove(e);
        } else {
            for (Enchantment other : new ArrayList<>(s.enchants.keySet())) {
                if (!other.equals(e) && e.conflictsWith(other)) {
                    s.enchants.remove(other);
                    player.sendMessage(Component.text(enchantName(other) + " was removed: it conflicts with "
                            + enchantName(e) + ".", NamedTextColor.YELLOW));
                }
            }
            s.enchants.put(e, next);
        }
        click(player);
        renderEnchant(top, s, player);
    }

    // ---------- events ----------

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof InspectHolder inspect) {
            handleInspector(event, inspect);
            return;
        }
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

        // item grid -> open the inspector
        int index = slot >= 0 && slot < CAT_SIZE ? SLOT_INDEX[slot] : -1;
        if (index < 0) {
            return;
        }
        List<Entry> entries = catalog.getOrDefault(category, List.of());
        int entryIndex = page * PAGE_SIZE + index;
        if (entryIndex < entries.size()) {
            Entry entry = entries.get(entryIndex);
            Session session = new Session(category, page, entry, applicableEnchants(entry.item()));
            click(player);
            Bukkit.getScheduler().runTask(plugin, () -> openInspector(player, session));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder h = event.getView().getTopInventory().getHolder();
        if (h instanceof MenuHolder || h instanceof InspectHolder) {
            event.setCancelled(true);
        }
    }
}
