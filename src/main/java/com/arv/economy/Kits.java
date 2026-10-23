package com.arv.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shulker Box kit bundles.
 *
 * /kit opens the kit shop. Hovering a kit shows everything inside its shulker box in the lore,
 * clicking it opens a preview of the real contents with a confirm button, and buying puts the
 * filled, color-coded shulker box straight into the player's inventory. Kits can be bought at
 * any time, as often as the player can afford them. Prices are on the 200x economy scale.
 *
 * To add a kit, add one more {@code kits.add(new Kit(...))} in {@link #buildKits()}.
 */
public class Kits implements Listener, CommandExecutor, TabCompleter {

    private record Kit(String id, String name, Material box, NamedTextColor color, double price,
                       String blurb, List<ItemStack> contents) {}

    private static final class KitHolder implements InventoryHolder {
        private final Kit kit; // null = the kit list
        private Inventory inventory;

        KitHolder(Kit kit) {
            this.kit = kit;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    // ---------- layout ----------

    private static final int SIZE = 45;
    private static final int HEADER_SLOT = 4;
    private static final int BACK_SLOT = 36;
    private static final int BUY_SLOT = 40;
    private static final int BALANCE_SLOT = 44;
    private static final int CONTENT_SLOTS = 27;
    private static final int[] KIT_SLOTS = {19, 21, 23, 25, 28, 30, 32, 34};

    /** Slot of the Kits button that is added to the /shop main menu. */
    private static final int SHOP_BUTTON_SLOT = 24;
    private static final String SHOP_MAIN_TITLE = "Shop - Categories";

    private static final Map<String, String> SHORT_ENCHANTS = Map.of(
            "sharpness", "Sharp", "unbreaking", "Unb", "mending", "Mend",
            "fire_aspect", "Fire", "protection", "Prot", "feather_falling", "Feather",
            "efficiency", "Eff", "fortune", "Fort", "silk_touch", "Silk");
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

    private final EconomyPlugin plugin;
    private final List<Kit> kits = new ArrayList<>();

    public Kits(EconomyPlugin plugin) {
        this.plugin = plugin;
        buildKits();
    }

    // ---------- kit definitions (prices are 200x economy) ----------

    private void buildKits() {
        // PvP Kit - red box. Items alone are worth about $2.1m in /shop, enchantments are included.
        List<ItemStack> pvp = new ArrayList<>();
        pvp.add(item(Material.NETHERITE_SWORD, 1, "sharpness", 5, "unbreaking", 3, "mending", 1, "fire_aspect", 2));
        pvp.add(item(Material.NETHERITE_HELMET, 1, "protection", 4, "unbreaking", 3, "mending", 1));
        pvp.add(item(Material.NETHERITE_CHESTPLATE, 1, "protection", 4, "unbreaking", 3, "mending", 1));
        pvp.add(item(Material.NETHERITE_LEGGINGS, 1, "protection", 4, "unbreaking", 3, "mending", 1));
        pvp.add(item(Material.NETHERITE_BOOTS, 1, "protection", 4, "unbreaking", 3, "mending", 1, "feather_falling", 4));
        pvp.add(item(Material.SHIELD, 1, "unbreaking", 3, "mending", 1));
        copies(pvp, item(Material.TOTEM_OF_UNDYING, 1), 2);
        pvp.add(item(Material.ENDER_PEARL, 16));
        pvp.add(item(Material.GOLDEN_APPLE, 16));
        pvp.add(item(Material.COOKED_BEEF, 32));
        copies(pvp, potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING), 4);
        copies(pvp, potion(Material.SPLASH_POTION, PotionType.STRONG_STRENGTH), 2);
        kits.add(new Kit("pvp", "PvP Kit", Material.RED_SHULKER_BOX, NamedTextColor.RED, 2_400_000,
                "Full netherite set, sword and combat consumables", pvp));

        // Mining Kit - blue box. Items alone are worth about $0.9m.
        List<ItemStack> mining = new ArrayList<>();
        mining.add(item(Material.NETHERITE_PICKAXE, 1, "efficiency", 5, "unbreaking", 3, "mending", 1, "fortune", 3));
        mining.add(item(Material.NETHERITE_PICKAXE, 1, "efficiency", 5, "unbreaking", 3, "mending", 1, "silk_touch", 1));
        mining.add(item(Material.NETHERITE_SHOVEL, 1, "efficiency", 5, "unbreaking", 3, "mending", 1));
        mining.add(item(Material.NETHERITE_AXE, 1, "efficiency", 5, "unbreaking", 3, "mending", 1));
        mining.add(item(Material.WATER_BUCKET, 1));
        mining.add(item(Material.COOKED_BEEF, 64));
        mining.add(item(Material.TORCH, 64));
        mining.add(item(Material.COBBLESTONE, 64));
        kits.add(new Kit("mining", "Mining Kit", Material.BLUE_SHULKER_BOX, NamedTextColor.BLUE, 1_000_000,
                "Netherite tools with Efficiency V, Fortune III and Silk Touch", mining));

        // Builder Kit - yellow box. Items alone are worth about $0.63m.
        List<ItemStack> builder = new ArrayList<>();
        builder.add(item(Material.DIAMOND_PICKAXE, 1, "efficiency", 5, "unbreaking", 3, "mending", 1));
        builder.add(item(Material.DIAMOND_AXE, 1, "efficiency", 5, "unbreaking", 3, "mending", 1));
        builder.add(item(Material.DIAMOND_SHOVEL, 1, "efficiency", 5, "unbreaking", 3, "mending", 1));
        builder.add(item(Material.SHEARS, 1, "efficiency", 5, "unbreaking", 3));
        builder.add(item(Material.STONE_BRICKS, 64));
        builder.add(item(Material.OAK_PLANKS, 64));
        builder.add(item(Material.GLASS, 64));
        builder.add(item(Material.QUARTZ_BLOCK, 64));
        builder.add(item(Material.SMOOTH_STONE, 64));
        builder.add(item(Material.WHITE_CONCRETE, 64));
        builder.add(item(Material.DEEPSLATE_BRICKS, 64));
        builder.add(item(Material.TORCH, 64));
        builder.add(item(Material.WATER_BUCKET, 1));
        builder.add(item(Material.COOKED_BEEF, 32));
        kits.add(new Kit("builder", "Builder Kit", Material.YELLOW_SHULKER_BOX, NamedTextColor.YELLOW, 700_000,
                "Diamond tools and stacks of building blocks", builder));

        // Explorer Kit - green box. Items alone are worth about $0.9m.
        List<ItemStack> explorer = new ArrayList<>();
        explorer.add(item(Material.ELYTRA, 1, "unbreaking", 3, "mending", 1));
        explorer.add(item(Material.FIREWORK_ROCKET, 64));
        explorer.add(item(Material.ENDER_PEARL, 16));
        explorer.add(item(Material.DIAMOND_SWORD, 1, "sharpness", 4, "unbreaking", 3, "mending", 1));
        explorer.add(item(Material.SHIELD, 1, "unbreaking", 3, "mending", 1));
        explorer.add(item(Material.WATER_BUCKET, 1));
        explorer.add(item(Material.COOKED_BEEF, 64));
        kits.add(new Kit("explorer", "Explorer Kit", Material.GREEN_SHULKER_BOX, NamedTextColor.GREEN, 1_000_000,
                "Elytra, rockets and survival essentials", explorer));
    }

    // ---------- item helpers ----------

    /** Builds a stack; extra args are (enchantment key, level) pairs, e.g. "sharpness", 5. */
    private static ItemStack item(Material material, int amount, Object... enchants) {
        ItemStack stack = new ItemStack(material, amount);
        if (enchants.length > 0) {
            ItemMeta meta = stack.getItemMeta();
            for (int i = 0; i + 1 < enchants.length; i += 2) {
                Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft((String) enchants[i]));
                if (enchantment != null) {
                    meta.addEnchant(enchantment, (Integer) enchants[i + 1], true);
                }
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack potion(Material container, PotionType type) {
        ItemStack stack = new ItemStack(container);
        PotionMeta meta = (PotionMeta) stack.getItemMeta();
        meta.setBasePotionType(type);
        stack.setItemMeta(meta);
        return stack;
    }

    private static void copies(List<ItemStack> list, ItemStack stack, int count) {
        for (int i = 0; i < count; i++) {
            list.add(stack.clone());
        }
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

    private static String describe(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        String base;
        if (meta instanceof PotionMeta potionMeta && potionMeta.getBasePotionType() != null) {
            String prefix = stack.getType() == Material.SPLASH_POTION ? "Splash Potion" : "Potion";
            base = prefix + " of " + pretty(potionMeta.getBasePotionType().name());
        } else {
            base = pretty(stack.getType().name());
        }
        if (stack.getEnchantments().isEmpty()) {
            return base;
        }
        List<Map.Entry<Enchantment, Integer>> list = new ArrayList<>(stack.getEnchantments().entrySet());
        list.sort(Comparator.comparing((Map.Entry<Enchantment, Integer> e) -> e.getKey().getKey().getKey()));
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> e : list) {
            String key = e.getKey().getKey().getKey();
            String name = SHORT_ENCHANTS.getOrDefault(key, pretty(key));
            int level = e.getValue();
            if (e.getKey().getMaxLevel() > 1) {
                name += " " + (level >= 0 && level < ROMAN.length ? ROMAN[level] : String.valueOf(level));
            }
            parts.add(name);
        }
        return base + " (" + String.join(", ", parts) + ")";
    }

    /** One lore line per distinct item, amounts added up (e.g. two single totems show as 2x). */
    private static List<Component> contentLines(Kit kit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack stack : kit.contents()) {
            counts.merge(describe(stack), stack.getAmount(), Integer::sum);
        }
        List<Component> lines = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            lines.add(Component.text(e.getValue() + "x ", NamedTextColor.WHITE)
                    .append(Component.text(e.getKey(), NamedTextColor.GRAY))
                    .decoration(TextDecoration.ITALIC, false));
        }
        return lines;
    }

    /** The real, populated shulker box the player receives. */
    private ItemStack build(Kit kit) {
        ItemStack box = new ItemStack(kit.box());
        BlockStateMeta meta = (BlockStateMeta) box.getItemMeta();
        ShulkerBox state = (ShulkerBox) meta.getBlockState();
        Inventory inv = state.getInventory();
        for (ItemStack stack : kit.contents()) {
            inv.addItem(stack.clone());
        }
        meta.setBlockState(state);
        meta.displayName(line(kit.name(), kit.color()).decorate(TextDecoration.BOLD));
        meta.lore(List.of(line(kit.blurb(), NamedTextColor.GRAY)));
        box.setItemMeta(meta);
        return box;
    }

    // ---------- gui helpers ----------

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
        return button(Material.GOLD_INGOT,
                "Balance: " + EconomyManager.format(plugin.economy().get(player.getUniqueId())),
                NamedTextColor.GOLD);
    }

    private boolean canAfford(Player player, Kit kit) {
        return plugin.economy().get(player.getUniqueId()) + 0.001 >= kit.price();
    }

    private ItemStack kitIcon(Kit kit, Player player) {
        ItemStack icon = new ItemStack(kit.box());
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(line(kit.name(), kit.color()).decorate(TextDecoration.BOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(line(kit.blurb(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(line("Inside the shulker box:", NamedTextColor.WHITE));
        lore.addAll(contentLines(kit));
        lore.add(Component.empty());
        lore.add(line("Price: " + EconomyManager.format(kit.price()),
                canAfford(player, kit) ? NamedTextColor.GREEN : NamedTextColor.RED));
        lore.add(line("Click to preview and buy", NamedTextColor.YELLOW));
        meta.lore(lore);
        icon.setItemMeta(meta);
        return icon;
    }

    private ItemStack buyButton(Kit kit, Player player) {
        if (canAfford(player, kit)) {
            return button(Material.EMERALD, "Buy for " + EconomyManager.format(kit.price()), NamedTextColor.GREEN,
                    "Click to buy this kit", "The filled shulker box goes", "straight into your inventory.");
        }
        double missing = kit.price() - plugin.economy().get(player.getUniqueId());
        return button(Material.BARRIER, "Buy for " + EconomyManager.format(kit.price()), NamedTextColor.RED,
                "You need " + EconomyManager.format(missing) + " more.");
    }

    private static boolean isShopMain(org.bukkit.inventory.InventoryView view) {
        return SHOP_MAIN_TITLE.equals(PlainTextComponentSerializer.plainText().serialize(view.title()));
    }

    private static void click(Player player) {
        player.playSound(player.getLocation(), "ui.button.click", 0.6f, 1.0f);
    }

    // ---------- menus ----------

    public void openMenu(Player player) {
        KitHolder holder = new KitHolder(null);
        Inventory inv = Bukkit.createInventory(holder, SIZE, Component.text("Kits", NamedTextColor.DARK_GRAY));
        holder.inventory = inv;
        for (int i = 0; i < SIZE; i++) {
            inv.setItem(i, filler());
        }
        inv.setItem(HEADER_SLOT, button(Material.NETHER_STAR, "Kit Bundles", NamedTextColor.GOLD,
                "Pre-packed color-coded shulker boxes.", "Hover a kit to see what is inside.",
                "Buy as many as you like, any time."));
        for (int i = 0; i < kits.size() && i < KIT_SLOTS.length; i++) {
            inv.setItem(KIT_SLOTS[i], kitIcon(kits.get(i), player));
        }
        inv.setItem(BACK_SLOT, button(Material.ARROW, "Back to shop", NamedTextColor.YELLOW));
        inv.setItem(BALANCE_SLOT, balanceItem(player));
        player.openInventory(inv);
    }

    private void openPreview(Player player, Kit kit) {
        KitHolder holder = new KitHolder(kit);
        Inventory inv = Bukkit.createInventory(holder, SIZE, Component.text("Kit - " + kit.name(), kit.color()));
        holder.inventory = inv;
        for (int i = CONTENT_SLOTS; i < SIZE; i++) {
            inv.setItem(i, filler());
        }
        for (int i = 0; i < kit.contents().size() && i < CONTENT_SLOTS; i++) {
            inv.setItem(i, kit.contents().get(i).clone());
        }
        inv.setItem(BACK_SLOT, button(Material.ARROW, "Back to kits", NamedTextColor.YELLOW));
        inv.setItem(BUY_SLOT, buyButton(kit, player));
        inv.setItem(BALANCE_SLOT, balanceItem(player));
        player.openInventory(inv);
    }

    // ---------- buying ----------

    private boolean buy(Player player, Kit kit) {
        EconomyManager eco = plugin.economy();
        if (!canAfford(player, kit)) {
            player.sendMessage(Component.text("You need " + EconomyManager.format(kit.price())
                    + " to buy the " + kit.name() + ".", NamedTextColor.RED));
            return false;
        }
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(Component.text("Free up an inventory slot first - the shulker box needs room.",
                    NamedTextColor.RED));
            return false;
        }
        if (!eco.withdraw(player.getUniqueId(), kit.price())) {
            player.sendMessage(Component.text("You can't afford that.", NamedTextColor.RED));
            return false;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(build(kit));
        if (!leftover.isEmpty()) {
            eco.add(player.getUniqueId(), kit.price());
            player.sendMessage(Component.text("Your inventory is full, you were not charged.", NamedTextColor.RED));
            return false;
        }
        player.sendMessage(Component.text("Bought the " + kit.name() + " for "
                + EconomyManager.format(kit.price()) + ". Check your inventory!", NamedTextColor.GREEN));
        player.playSound(player.getLocation(), "entity.experience_orb.pickup", 0.6f, 1.2f);
        return true;
    }

    // ---------- events ----------

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (isShopMain(event.getView())) {
            event.getInventory().setItem(SHOP_BUTTON_SLOT, button(Material.SHULKER_BOX, "Kits", NamedTextColor.LIGHT_PURPLE,
                    "PvP, Mining, Builder and Explorer kits", "delivered in color-coded shulker boxes."));
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Kits button inside the /shop main menu
        if (!(top.getHolder() instanceof KitHolder)) {
            if (isShopMain(event.getView()) && event.getClickedInventory() != null
                    && event.getClickedInventory().equals(top) && event.getSlot() == SHOP_BUTTON_SLOT) {
                event.setCancelled(true);
                click(player);
                Bukkit.getScheduler().runTask(plugin, () -> openMenu(player));
            }
            return;
        }

        KitHolder holder = (KitHolder) top.getHolder();
        event.setCancelled(true);
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(top)) {
            return;
        }
        int slot = event.getSlot();

        if (holder.kit == null) {
            // kit list
            if (slot == BACK_SLOT) {
                click(player);
                Bukkit.getScheduler().runTask(plugin, () -> plugin.shop().openMain(player));
                return;
            }
            for (int i = 0; i < kits.size() && i < KIT_SLOTS.length; i++) {
                if (KIT_SLOTS[i] == slot) {
                    Kit kit = kits.get(i);
                    click(player);
                    Bukkit.getScheduler().runTask(plugin, () -> openPreview(player, kit));
                    return;
                }
            }
            return;
        }

        // preview / confirm screen
        Kit kit = holder.kit;
        if (slot == BACK_SLOT) {
            click(player);
            Bukkit.getScheduler().runTask(plugin, () -> openMenu(player));
        } else if (slot == BUY_SLOT) {
            buy(player, kit);
            top.setItem(BUY_SLOT, buyButton(kit, player));
            top.setItem(BALANCE_SLOT, balanceItem(player));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof KitHolder) {
            event.setCancelled(true);
        }
    }

    // ---------- /kit ----------

    private Kit find(String text) {
        String query = text.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
        if (query.endsWith("kit")) {
            query = query.substring(0, query.length() - 3);
        }
        for (Kit kit : kits) {
            if (kit.id().equals(query)) {
                return kit;
            }
        }
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /kit.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            openMenu(player);
            return true;
        }
        Kit kit = find(args[0]);
        if (kit == null) {
            List<String> ids = new ArrayList<>();
            for (Kit k : kits) {
                ids.add(k.id());
            }
            player.sendMessage(Component.text("Unknown kit. Available: " + String.join(", ", ids)
                    + ". Use /kit to browse them all.", NamedTextColor.RED));
            return true;
        }
        openPreview(player, kit);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Kit kit : kits) {
                if (kit.id().startsWith(prefix)) {
                    out.add(kit.id());
                }
            }
        }
        return out;
    }
}
