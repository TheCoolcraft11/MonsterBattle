package de.thecoolcraft11.monsterBattle.command;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import de.thecoolcraft11.monsterBattle.util.SpawnData;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class MonsterSpawnCommand implements CommandExecutor {

    private final MonsterBattle plugin;

    public MonsterSpawnCommand(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /" + label + " <add|addhere|list|clear> <args...>");
            return false;
        }
        if (args[0].equalsIgnoreCase("clear")) {
            plugin.getDataController().clearMonsterSpawns();
            sender.sendMessage("All monster spawn points cleared.");
            return true;
        } else if (args[0].equalsIgnoreCase("list")) {
            StringBuilder sb = new StringBuilder("Monster Spawn Points:\n");
            for (SpawnData data : plugin.getDataController().getMonsterSpawns()) {
                sb.append("- ").append(data.toString()).append("\n");
            }
            sender.sendMessage(sb.toString());
            return true;
        } else if (args[0].equalsIgnoreCase("add")) {
            
            if (args.length != 4) {
                sender.sendMessage("Usage: /" + label + " add <x> <y> <z>");
                return false;
            }
            try {
                int x = Integer.parseInt(args[1]);
                int y = Integer.parseInt(args[2]);
                int z = Integer.parseInt(args[3]);
                SpawnData data = new SpawnData(x, y, z);
                plugin.getDataController().addMonsterSpawn(data);
                sender.sendMessage("Spawn point set at " + x + ", " + y + ", " + z);
                return true;
            } catch (NumberFormatException ex) {
                sender.sendMessage("Coordinates must be integers. Usage: /" + label + " add <x> <y> <z>");
                return false;
            }
        } else if (args[0].equalsIgnoreCase("addhere")) {
            if (sender instanceof org.bukkit.entity.Player player) {
                Location loc = player.getLocation();
                SpawnData data = new SpawnData(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                plugin.getDataController().addMonsterSpawn(data);
                sender.sendMessage("Spawn point set at your location: " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
            } else {
                sender.sendMessage("This command can only be used by a player.");
            }
            return true;
        }
        sender.sendMessage("Usage: /" + label + " <add|addhere|list|clear> <args...>");
        return false;
    }
}
