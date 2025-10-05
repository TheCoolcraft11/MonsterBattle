package de.thecoolcraft11.monsterBattle.command;

import de.thecoolcraft11.monsterBattle.util.GameState;
import de.thecoolcraft11.monsterBattle.MonsterBattle;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class SetPhaseCommand implements CommandExecutor {

    private final MonsterBattle plugin;

    public SetPhaseCommand(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if(args.length == 0) {
            sender.sendMessage("Usage: /" + label + " <lobby|farm|battle|end>");
            return true;
        }
        String phase = args[0].toLowerCase();
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
        if(old == newState) {
            sender.sendMessage("Game is already in phase: " + newState.name());
            return true;
        }
        plugin.getDataController().setGameState(newState);

        plugin.getPhaseSwitchHook().newPhase(plugin, newState);

        Bukkit.getServer().broadcast("Game phase changed to: " + newState.name(), "monsterbattle.notify");
        return true;
    }
}
