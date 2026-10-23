package com.arv.economy;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Stores up to {@link #MAX_HOMES} named homes per player in homes.yml. */
public class HomeManager {

    public static final int MAX_HOMES = 10;

    public record Home(String world, double x, double y, double z, float yaw, float pitch) {
        /** Returns null when the world is not loaded. */
        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z, yaw, pitch);
        }
    }

    private final EconomyPlugin plugin;
    private final File file;
    private final Map<UUID, Map<String, Home>> homes = new HashMap<>();
    private boolean dirty = false;

    public HomeManager(EconomyPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection all = cfg.getConfigurationSection("homes");
        if (all == null) {
            return;
        }
        for (String uuidKey : all.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(uuidKey);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping bad UUID in homes.yml: " + uuidKey);
                continue;
            }
            ConfigurationSection mine = all.getConfigurationSection(uuidKey);
            if (mine == null) {
                continue;
            }
            Map<String, Home> map = new TreeMap<>();
            for (String name : mine.getKeys(false)) {
                ConfigurationSection s = mine.getConfigurationSection(name);
                if (s == null || s.getString("world") == null) {
                    continue;
                }
                map.put(name, new Home(s.getString("world"), s.getDouble("x"), s.getDouble("y"),
                        s.getDouble("z"), (float) s.getDouble("yaw"), (float) s.getDouble("pitch")));
            }
            if (!map.isEmpty()) {
                homes.put(id, map);
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Home>> e : homes.entrySet()) {
            for (Map.Entry<String, Home> h : e.getValue().entrySet()) {
                String base = "homes." + e.getKey() + "." + h.getKey();
                Home v = h.getValue();
                cfg.set(base + ".world", v.world());
                cfg.set(base + ".x", v.x());
                cfg.set(base + ".y", v.y());
                cfg.set(base + ".z", v.z());
                cfg.set(base + ".yaw", (double) v.yaw());
                cfg.set(base + ".pitch", (double) v.pitch());
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            cfg.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save homes.yml: " + ex.getMessage());
        }
    }

    public void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    public Map<String, Home> all(UUID id) {
        Map<String, Home> map = homes.get(id);
        return map == null ? Collections.emptyMap() : Collections.unmodifiableMap(map);
    }

    public Home get(UUID id, String name) {
        return all(id).get(name);
    }

    public int count(UUID id) {
        return all(id).size();
    }

    /** Creates or overwrites a home. Returns false if a NEW home would exceed the limit. */
    public boolean set(UUID id, String name, Location loc) {
        Map<String, Home> map = homes.computeIfAbsent(id, k -> new TreeMap<>());
        if (!map.containsKey(name) && map.size() >= MAX_HOMES) {
            return false;
        }
        map.put(name, new Home(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch()));
        dirty = true;
        return true;
    }

    public boolean delete(UUID id, String name) {
        Map<String, Home> map = homes.get(id);
        if (map == null || map.remove(name) == null) {
            return false;
        }
        if (map.isEmpty()) {
            homes.remove(id);
        }
        dirty = true;
        return true;
    }

    public void clear(UUID id) {
        if (homes.remove(id) != null) {
            dirty = true;
        }
    }
}
