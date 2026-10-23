package com.arv.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** /baltop - the 10 richest players plus the sender's own placement. */
public class Baltop implements CommandExecutor {

    private static final int TOP = 10;

    private final EconomyPlugin plugin;

    public Baltop(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    private static Component text(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<Map.Entry<UUID, Double>> ranking = new ArrayList<>(plugin.economy().snapshot().entrySet());
        ranking.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        UUID self = sender instanceof Player p ? p.getUniqueId() : null;

        sender.sendMessage(text("--- Top " + TOP + " Richest ---", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));

        for (int i = 0; i < ranking.size() && i < TOP; i++) {
            Map.Entry<UUID, Double> entry = ranking.get(i);
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null) {
                name = "Unknown";
            }
            boolean me = entry.getKey().equals(self);
            sender.sendMessage(text((i + 1) + ". ", NamedTextColor.GRAY)
                    .append(text(name, me ? NamedTextColor.YELLOW : NamedTextColor.WHITE))
                    .append(text(" - ", NamedTextColor.DARK_GRAY))
                    .append(text(EconomyManager.format(entry.getValue()), NamedTextColor.GREEN)));
        }
        if (ranking.isEmpty()) {
            sender.sendMessage(text("Nobody has any money yet.", NamedTextColor.GRAY));
        }

        if (self != null) {
            int place = -1;
            for (int i = 0; i < ranking.size(); i++) {
                if (ranking.get(i).getKey().equals(self)) {
                    place = i + 1;
                    break;
                }
            }
            double balance = plugin.economy().get(self);
            String placement = place > 0 ? "#" + place : "unranked";
            sender.sendMessage(text("Your placement: ", NamedTextColor.GRAY)
                    .append(text(placement, NamedTextColor.YELLOW))
                    .append(text(" (" + EconomyManager.format(balance) + ")", NamedTextColor.GREEN)));
        }
        return true;
    }
}
