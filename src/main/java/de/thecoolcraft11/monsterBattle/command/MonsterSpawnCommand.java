package de.thecoolcraft11.monsterBattle.command;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import de.thecoolcraft11.monsterBattle.util.SpawnData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

@SuppressWarnings("SameReturnValue")
public class MonsterSpawnCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = Arrays.asList("add", "addhere", "addrel", "list", "remove", "clear");
    private final MonsterBattle plugin;

    public MonsterSpawnCommand(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("Usage:", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " add <x> <y> <z> - add absolute coords (doubles, supports ~relative)", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " addhere - add your current block position", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " addrel <dx> <dy> <dz> - add relative to your position", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " list - list spawn points with indices", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " remove <index> - remove spawn point by index", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/" + label + " clear - clear all spawn points", NamedTextColor.GRAY));
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
                sender.sendMessage(Component.text("All monster spawn points cleared.", NamedTextColor.GREEN));
                return true;
            }
            case "list" -> {
                List<SpawnData> list = plugin.getDataController().getMonsterSpawns();
                if (list.isEmpty()) {
                    sender.sendMessage(Component.text("No spawn points set.", NamedTextColor.YELLOW));
                    return true;
                }
                sender.sendMessage(Component.text("Monster Spawn Points (" + list.size() + "):", NamedTextColor.AQUA));
                for (int i = 0; i < list.size(); i++) {
                    SpawnData d = list.get(i);
                    sender.sendMessage(Component.text()
                            .append(Component.text("#" + i, NamedTextColor.GRAY))
                            .append(Component.text(": " + d.x + ", " + d.y + ", " + d.z, NamedTextColor.WHITE))
                            .build());
                }
                return true;
            }
            case "remove" -> {
                if (args.length != 2) {
                    sender.sendMessage(Component.text("Usage: /" + label + " remove <index>", NamedTextColor.RED));
                    return true;
                }
                try {
                    int idx = Integer.parseInt(args[1]);
                    List<SpawnData> list = plugin.getDataController().getMonsterSpawns();
                    if (idx < 0 || idx >= list.size()) {
                        sender.sendMessage(Component.text("Index out of range 0-" + (list.size() - 1), NamedTextColor.RED));
                        return true;
                    }
                    SpawnData removed = list.remove(idx);
                    sender.sendMessage(Component.text("Removed spawn #" + idx + " (" + removed.x + ", " + removed.y + ", " + removed.z + ")", NamedTextColor.GREEN));
                } catch (NumberFormatException ex) {
                    sender.sendMessage(Component.text("Index must be a number.", NamedTextColor.RED));
                }
                return true;
            }
            case "addhere" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Component.text("Player only command.", NamedTextColor.RED));
                    return true;
                }
                Location loc = p.getLocation();
                SpawnData data = new SpawnData(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                plugin.getDataController().addMonsterSpawn(data);
                sender.sendMessage(Component.text("Added spawn at your location: " + data.x + ", " + data.y + ", " + data.z, NamedTextColor.GREEN));
                return true;
            }
            case "addrel" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Component.text("Player only command.", NamedTextColor.RED));
                    return true;
                }
                if (args.length != 4) {
                    sender.sendMessage(Component.text("Usage: /" + label + " addrel <dx> <dy> <dz>", NamedTextColor.RED));
                    return true;
                }
                try {
                    double dx = Double.parseDouble(args[1]);
                    double dy = Double.parseDouble(args[2]);
                    double dz = Double.parseDouble(args[3]);
                    Location base = p.getLocation();
                    SpawnData data = new SpawnData(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    plugin.getDataController().addMonsterSpawn(data);
                    sender.sendMessage(Component.text("Added relative spawn at: " + data.x + ", " + data.y + ", " + data.z, NamedTextColor.GREEN));
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("dx dy dz must be numbers.", NamedTextColor.RED));
                }
                return true;
            }
            case "add" -> {
                if (args.length != 4) {
                    sender.sendMessage(Component.text("Usage: /" + label + " add <x> <y> <z>", NamedTextColor.RED));
                    return true;
                }
                if (!(sender instanceof Player) && (args[1].startsWith("~") || args[2].startsWith("~") || args[3].startsWith("~"))) {
                    sender.sendMessage(Component.text("Relative (~) coordinates require a player executor.", NamedTextColor.RED));
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
                    sender.sendMessage(Component.text("Added spawn at: " + x + ", " + y + ", " + z, NamedTextColor.GREEN));
                } catch (NumberFormatException ex) {
                    sender.sendMessage(Component.text("Coordinates must be numbers (or ~relative).", NamedTextColor.RED));
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
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {

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
                if (args.length <= 4) return List.of("0");
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