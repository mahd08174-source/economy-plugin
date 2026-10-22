package com.arv.economy;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class EconomyPlugin extends JavaPlugin implements Listener {

    private EconomyManager economy;
    private SidebarManager sidebar;
    private EcoCommands commands;

    @Override
    public void onEnable() {
        economy = new EconomyManager(this);
        economy.load();
        sidebar = new SidebarManager(this);
        Shop shop = new Shop(this);
        commands = new EcoCommands(this, shop);

        for (String name : List.of("bal", "pay", "give", "shop", "tpa", "tpahere", "tpaccept", "tpdeny")) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(commands);
            }
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(shop, this);

        Bukkit.getScheduler().runTaskTimer(this, sidebar::updateAll, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, economy::saveIfDirty, 600L, 600L);

        for (Player p : Bukkit.getOnlinePlayers()) {
            economy.ensure(p.getUniqueId());
            sidebar.show(p);
        }
    }

    @Override
    public void onDisable() {
        if (economy != null) {
            economy.save();
        }
    }

    public EconomyManager economy() {
        return economy;
    }

    public SidebarManager sidebar() {
        return sidebar;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        economy.ensure(p.getUniqueId());
        sidebar.show(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        sidebar.remove(p);
        commands.clear(p);
    }
}
