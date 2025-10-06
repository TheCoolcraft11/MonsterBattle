package de.thecoolcraft11.monsterBattle.command;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DimensionTeleportCommand implements CommandExecutor, TabCompleter {

    public DimensionTeleportCommand() {
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("monsterbattle.dtp")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }
        if (args.length < 5) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <player> <world> <x> <y> <z>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
            return true;
        }
        String worldName = args[1];
        double x, y, z;
        try {
            x = Double.parseDouble(args[2]);
            y = Double.parseDouble(args[3]);
            z = Double.parseDouble(args[4]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Coordinates must be numbers.");
            return true;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {

            World.Environment env = World.Environment.NORMAL;
            if (worldName.endsWith("_nether")) env = World.Environment.NETHER;
            else if (worldName.endsWith("_the_end")) env = World.Environment.THE_END;
            try {
                world = new WorldCreator(worldName).environment(env).createWorld();
            } catch (Exception e) {
                sender.sendMessage(ChatColor.RED + "Failed to load or create world: " + e.getMessage());
                return true;
            }
            if (world == null) {
                sender.sendMessage(ChatColor.RED + "World could not be created: " + worldName);
                return true;
            }
        }
        Location loc = new Location(world, x, y, z, target.getLocation().getYaw(), target.getLocation().getPitch());
        target.teleport(loc);
        target.sendMessage(ChatColor.GREEN + "Teleported to " + worldName + " (" + x + ", " + y + ", " + z + ")");
        if (sender != target)
            sender.sendMessage(ChatColor.GRAY + "Teleported " + target.getName() + " to " + worldName + ".");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("monsterbattle.dtp")) return Collections.emptyList();
        switch (args.length) {
            case 1 -> {
                String prefix = args[0].toLowerCase();
                return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(prefix)).sorted().collect(Collectors.toList());
            }
            case 2 -> {
                String wPrefix = args[1].toLowerCase();
                Set<String> worlds = getWorlds();
                return worlds.stream().filter(n -> n.toLowerCase().startsWith(wPrefix)).sorted().limit(50).collect(Collectors.toList());
            }
            case 3, 4, 5 -> {

                Player ref = Bukkit.getPlayerExact(args[0]);
                if (ref != null) {
                    Location l = ref.getLocation();
                    int val = switch (args.length) {
                        case 3 -> l.getBlockX();
                        case 4 -> l.getBlockY();
                        default -> l.getBlockZ();
                    };
                    return Collections.singletonList(Integer.toString(val));
                }
            }
        }
        return Collections.emptyList();
    }

    private static @NotNull Set<String> getWorlds() {
        Set<String> worlds = new HashSet<>();

        for (World w : Bukkit.getWorlds()) worlds.add(w.getName());

        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        for (Team t : mgr.getMainScoreboard().getTeams()) {
            String baseTeam = t.getName().replaceAll("[^A-Za-z0-9_-]", "_");
            worlds.add("Farm_" + baseTeam);
            worlds.add("Arena_" + baseTeam);
            worlds.add("Farm_" + baseTeam + "_nether");
            worlds.add("Farm_" + baseTeam + "_the_end");
            worlds.add("Arena_" + baseTeam + "_nether");
            worlds.add("Arena_" + baseTeam + "_the_end");
        }
        return worlds;
    }
}

