package com.arv.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SidebarManager {

    private static final String BLANK_ENTRY = "\u00a71";
    private static final String NAME_ENTRY = "\u00a72";
    private static final String CASH_ENTRY = "\u00a73";

    private final EconomyPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public SidebarManager(EconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void show(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective(
                "economy",
                Criteria.DUMMY,
                Component.text("ECONOMY", NamedTextColor.GOLD, TextDecoration.BOLD));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        Team nameTeam = board.registerNewTeam("name");
        nameTeam.addEntry(NAME_ENTRY);
        Team cashTeam = board.registerNewTeam("cash");
        cashTeam.addEntry(CASH_ENTRY);

        objective.getScore(BLANK_ENTRY).setScore(3);
        objective.getScore(NAME_ENTRY).setScore(2);
        objective.getScore(CASH_ENTRY).setScore(1);

        boards.put(player.getUniqueId(), board);
        update(player);
        player.setScoreboard(board);
    }

    public void update(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) {
            show(player);
            return;
        }
        Team nameTeam = board.getTeam("name");
        Team cashTeam = board.getTeam("cash");
        if (nameTeam != null) {
            nameTeam.prefix(Component.text("Player: ", NamedTextColor.GRAY)
                    .append(Component.text(player.getName(), NamedTextColor.WHITE)));
        }
        if (cashTeam != null) {
            double cash = plugin.economy().get(player.getUniqueId());
            cashTeam.prefix(Component.text("Cash: ", NamedTextColor.GRAY)
                    .append(Component.text(EconomyManager.format(cash), NamedTextColor.GREEN)));
        }
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    public void remove(Player player) {
        boards.remove(player.getUniqueId());
    }
}
