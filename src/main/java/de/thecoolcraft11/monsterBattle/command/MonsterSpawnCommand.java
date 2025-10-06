package de.thecoolcraft11.monsterBattle.command;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import de.thecoolcraft11.monsterBattle.util.SpawnData;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class MonsterSpawnCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList("add", "addhere", "addrel", "list", "remove", "clear");
    private final MonsterBattle plugin;

    public MonsterSpawnCommand(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.YELLOW + "Usage:");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " add <x> <y> <z> - add absolute coords (doubles, supports ~relative)");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " addhere - add your current block position");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " addrel <dx> <dy> <dz> - add relative to your position");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " list - list spawn points with indices");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " remove <index> - remove spawn point by index");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " clear - clear all spawn points");
    }

    private double parseCoord(String token, double base) throws NumberFormatException {
        if (token.startsWith("~")) {
            if (token.equals("~")) return base;
            String offset = token.substring(1);
            if (offset.isEmpty()) return base;
            return base + Double.parseDouble(offset);
        }
        return Double.parseDouble(token);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "clear" -> {
                plugin.getDataController().clearMonsterSpawns();
                sender.sendMessage(ChatColor.GREEN + "All monster spawn points cleared.");
                return true;
            }
            case "list" -> {
                List<SpawnData> list = plugin.getDataController().getMonsterSpawns();
                if (list.isEmpty()) {
                    sender.sendMessage(ChatColor.YELLOW + "No spawn points set.");
                    return true;
                }
                sender.sendMessage(ChatColor.AQUA + "Monster Spawn Points (" + list.size() + "):");
                for (int i = 0; i < list.size(); i++) {
                    SpawnData d = list.get(i);
                    sender.sendMessage(ChatColor.GRAY + "#" + i + ChatColor.WHITE + ": " + d.x + ", " + d.y + ", " + d.z);
                }
                return true;
            }
            case "remove" -> {
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " remove <index>");
                    return true;
                }
                try {
                    int idx = Integer.parseInt(args[1]);
                    List<SpawnData> list = plugin.getDataController().getMonsterSpawns();
                    if (idx < 0 || idx >= list.size()) {
                        sender.sendMessage(ChatColor.RED + "Index out of range 0-" + (list.size() - 1));
                        return true;
                    }
                    SpawnData removed = list.remove(idx);
                    sender.sendMessage(ChatColor.GREEN + "Removed spawn #" + idx + " (" + removed.x + ", " + removed.y + ", " + removed.z + ")");
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "Index must be a number.");
                }
                return true;
            }
            case "addhere" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(ChatColor.RED + "Player only command.");
                    return true;
                }
                Location loc = p.getLocation();
                SpawnData data = new SpawnData(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                plugin.getDataController().addMonsterSpawn(data);
                sender.sendMessage(ChatColor.GREEN + "Added spawn at your location: " + data.x + ", " + data.y + ", " + data.z);
                return true;
            }
            case "addrel" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(ChatColor.RED + "Player only command.");
                    return true;
                }
                if (args.length != 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " addrel <dx> <dy> <dz>");
                    return true;
                }
                try {
                    double dx = Double.parseDouble(args[1]);
                    double dy = Double.parseDouble(args[2]);
                    double dz = Double.parseDouble(args[3]);
                    Location base = p.getLocation();
                    SpawnData data = new SpawnData(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    plugin.getDataController().addMonsterSpawn(data);
                    sender.sendMessage(ChatColor.GREEN + "Added relative spawn at: " + data.x + ", " + data.y + ", " + data.z);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "dx dy dz must be numbers.");
                }
                return true;
            }
            case "add" -> {
                if (args.length != 4) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " add <x> <y> <z>");
                    return true;
                }
                if (!(sender instanceof Player p) && (args[1].startsWith("~") || args[2].startsWith("~") || args[3].startsWith("~"))) {
                    sender.sendMessage(ChatColor.RED + "Relative (~) coordinates require a player executor.");
                    return true;
                }
                double baseX = 0, baseY = 0, baseZ = 0;
                if (sender instanceof Player pl) {
                    Location l = pl.getLocation();
                    baseX = l.getX();
                    baseY = l.getY();
                    baseZ = l.getZ();
                }
                try {
                    double x = parseCoord(args[1], baseX);
                    double y = parseCoord(args[2], baseY);
                    double z = parseCoord(args[3], baseZ);
                    SpawnData data = new SpawnData(x, y, z);
                    plugin.getDataController().addMonsterSpawn(data);
                    sender.sendMessage(ChatColor.GREEN + "Added spawn at: " + x + ", " + y + ", " + z);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ChatColor.RED + "Coordinates must be numbers (or ~relative).");
                }
                return true;
            }
            default -> {
                sendUsage(sender, label);
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {

        if (!sender.hasPermission("monsterbattle.setphase")) return List.of();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return SUBS.stream().filter(s -> s.startsWith(prefix)).collect(Collectors.toList());
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("remove")) {
            if (args.length == 2) {
                List<SpawnData> list = plugin.getDataController().getMonsterSpawns();
                return indexList(list.size());
            }
            return List.of();
        }
        if (sub.equals("add")) {

            if (sender instanceof Player p) {
                Location l = p.getLocation();
                if (args.length == 2) return Arrays.asList("~", format(l.getX()));
                if (args.length == 3) return Arrays.asList("~", format(l.getY()));
                if (args.length == 4) return Arrays.asList("~", format(l.getZ()));
            } else {
                if (args.length >= 2 && args.length <= 4) return List.of("0");
            }
        }
        if (sub.equals("addrel")) {
            if (args.length <= 4) return List.of("0");
        }
        return List.of();
    }

    private List<String> indexList(int size) {
        List<String> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) list.add(String.valueOf(i));
        return list;
    }

    private String format(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}