package de.thecoolcraft11.monsterBattle.command;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import de.thecoolcraft11.monsterBattle.util.GameState;
import org.bukkit.Bukkit;
import org.bukkit.World;
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
        if (newState == GameState.FARMING && !checkSetupComplete(sender)) {
            return true;
        }
        if (old == newState) {
            sender.sendMessage("Game is already in phase: " + newState.name());
            return true;
        }
        if (old == GameState.BATTLE) {
            plugin.releaseBattleChunks();
            plugin.getMonsterSpawner().cancelAllSpawners();
        }
        plugin.getDataController().setGameState(newState);

        plugin.getPhaseSwitchHook().newPhase(plugin, newState);

        plugin.getLogger().info("Game phase changed to: " + newState.name());
        Bukkit.getServer().broadcast("§6[MonsterBattle] §eGame phase changed to: §b" + newState.name(), "monsterbattle.admin");
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

    private boolean checkSetupComplete(CommandSender sender) {
        if (plugin.getDataController().getMonsterSpawns().isEmpty()) {
            sender.sendMessage("Monster spawns are not configured yet. Please complete the setup first.");
            return false;
        }
        if (Bukkit.getServer().getScoreboardManager().getMainScoreboard().getTeams().size() != 2) {
            if (Bukkit.getServer().getScoreboardManager().getMainScoreboard().getTeams().size() < 2) {
                sender.sendMessage("Not enough teams are configured yet. Please complete the setup first.");
                return false;
            } else if (Bukkit.getServer().getScoreboardManager().getMainScoreboard().getTeams().size() > 2) {
                sender.sendMessage("Too many teams are configured. The maximum allowed is 4.");
                return false;
            }
            return false;
        }
        World arena = Bukkit.getWorld(plugin.getConfig().getString("arena-template-world", "Arena"));
        if (arena == null) {
            sender.sendMessage("Arena world is not configured yet. Please complete the setup first.");
            return false;
        }
        return true;
    }
}