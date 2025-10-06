package de.thecoolcraft11.monsterBattle.command;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import de.thecoolcraft11.monsterBattle.util.GameState;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class SetPhaseCommand implements CommandExecutor, TabCompleter {

    private final MonsterBattle plugin;
    private static final List<String> PHASES = Arrays.asList("lobby", "farm", "battle", "end");

    public SetPhaseCommand(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /" + label + " <lobby|farm|battle|end>");
            return true;
        }
        String phase = args[0].toLowerCase(Locale.ROOT);
        GameState old = plugin.getDataController().getGameState();
        GameState newState;
        switch (phase) {
            case "lobby" -> newState = GameState.LOBBY;
            case "farm" -> newState = GameState.FARMING;
            case "battle" -> newState = GameState.BATTLE;
            case "end" -> newState = GameState.ENDED;
            default -> {
                sender.sendMessage("Invalid phase! Use lobby, farm, battle or end.");
                return true;
            }
        }
        if (old == newState) {
            sender.sendMessage("Game is already in phase: " + newState.name());
            return true;
        }
        if (old == GameState.BATTLE) {
            plugin.releaseBattleChunks();
        }
        plugin.getDataController().setGameState(newState);

        plugin.getPhaseSwitchHook().newPhase(plugin, newState);

        Bukkit.getServer().broadcast("Game phase changed to: " + newState.name(), "monsterbattle.notify");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("monsterbattle.setphase")) return List.of();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return PHASES.stream().filter(p -> p.startsWith(prefix)).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}