package com.arv.economy;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class EconomyManager {

    public static final double START_BALANCE = 1000.0;
    public static final double MAX_AMOUNT = 1_000_000_000_000.0;
    private static final Pattern AMOUNT = Pattern.compile("\\d{1,13}(\\.\\d{1,2})?");

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

    public double get(UUID id) {
        return balances.getOrDefault(id, START_BALANCE);
    }

    public void add(UUID id, double amount) {
        balances.put(id, round(get(id) + amount));
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

    /** Returns null when the text is not a valid positive amount (max 2 decimals). */
    public static Double parseAmount(String text) {
        if (text == null || !AMOUNT.matcher(text).matches()) {
            return null;
        }
        double value = new BigDecimal(text).setScale(2, RoundingMode.HALF_UP).doubleValue();
        if (value <= 0 || value > MAX_AMOUNT) {
            return null;
        }
        return value;
    }

    public static double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static String format(double value) {
        return "$" + String.format(Locale.US, "%,.2f", value);
    }
}
