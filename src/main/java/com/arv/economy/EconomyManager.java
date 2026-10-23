package com.arv.economy;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EconomyManager {

    public static final double START_BALANCE = 10_000.0;
    public static final double MAX_AMOUNT = 1_000_000_000_000_000.0;
    private static final double MAX_BALANCE = 1.0e18;
    private static final Pattern AMOUNT = Pattern.compile("(\\d{1,15}(?:\\.\\d{1,6})?)([kKmMbBtT])?");
    private static final String[] SUFFIXES = {"", "k", "m", "b", "t", "q"};

    private final EconomyPlugin plugin;
    private final File file;
    private final Map<UUID, Double> balances = new HashMap<>();
    private boolean dirty = false;

    public EconomyManager(EconomyPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "balances.yml");
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = cfg.getConfigurationSection("balances");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                balances.put(UUID.fromString(key), section.getDouble(key));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping bad UUID in balances.yml: " + key);
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Double> e : balances.entrySet()) {
            cfg.set("balances." + e.getKey(), e.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save balances.yml: " + ex.getMessage());
        }
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public boolean has(UUID id) {
        return balances.containsKey(id);
    }

    public void ensure(UUID id) {
        if (!balances.containsKey(id)) {
            balances.put(id, START_BALANCE);
            dirty = true;
        }
    }

    /** Copy of every stored balance (used by /baltop). */
    public Map<UUID, Double> snapshot() {
        return new HashMap<>(balances);
    }

    /** Sets a balance directly (used by admin commands). */
    public void set(UUID id, double amount) {
        balances.put(id, Math.max(0.0, Math.min(MAX_BALANCE, round(amount))));
        dirty = true;
    }

    /** Resets a player's balance to the starting balance. */
    public void reset(UUID id) {
        balances.put(id, START_BALANCE);
        dirty = true;
    }

    public double get(UUID id) {
        return balances.getOrDefault(id, START_BALANCE);
    }

    public void add(UUID id, double amount) {
        balances.put(id, Math.min(MAX_BALANCE, round(get(id) + amount)));
        dirty = true;
    }

    public boolean withdraw(UUID id, double amount) {
        double current = get(id);
        if (current + 0.001 < amount) {
            return false;
        }
        balances.put(id, Math.max(0.0, round(current - amount)));
        dirty = true;
        return true;
    }

    /**
     * Parses amounts like 500, 12.50, 100k, 1.5m, 2b, 3t.
     * Returns null when the text is not a valid positive amount.
     */
    public static Double parseAmount(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = AMOUNT.matcher(text.trim());
        if (!m.matches()) {
            return null;
        }
        BigDecimal value = new BigDecimal(m.group(1));
        String suffix = m.group(2);
        if (suffix != null) {
            BigDecimal factor = switch (Character.toLowerCase(suffix.charAt(0))) {
                case 'k' -> BigDecimal.valueOf(1_000L);
                case 'm' -> BigDecimal.valueOf(1_000_000L);
                case 'b' -> BigDecimal.valueOf(1_000_000_000L);
                default -> BigDecimal.valueOf(1_000_000_000_000L);
            };
            value = value.multiply(factor);
        }
        value = value.setScale(2, RoundingMode.HALF_UP);
        if (value.signum() <= 0 || value.compareTo(BigDecimal.valueOf(MAX_AMOUNT)) > 0) {
            return null;
        }
        return value.doubleValue();
    }

    public static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** Short money format: $950, $12.50, $1.7k, $2.5m, $1.7b, $1.7t. */
    public static String format(double value) {
        BigDecimal v = BigDecimal.valueOf(value);
        BigDecimal thousand = BigDecimal.valueOf(1000);
        int index = 0;
        while (v.abs().compareTo(thousand) >= 0 && index < SUFFIXES.length - 1) {
            v = v.divide(thousand);
            index++;
        }
        String text;
        if (index == 0) {
            v = v.setScale(2, RoundingMode.HALF_UP);
            if (v.remainder(BigDecimal.ONE).signum() == 0) {
                text = v.toBigInteger().toString();
            } else {
                text = v.toPlainString();
            }
        } else {
            text = v.setScale(2, RoundingMode.DOWN).stripTrailingZeros().toPlainString();
        }
        return "$" + text + SUFFIXES[index];
    }
}
