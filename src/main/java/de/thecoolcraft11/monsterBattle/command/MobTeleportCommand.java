package de.thecoolcraft11.monsterBattle.command;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import de.thecoolcraft11.monsterBattle.util.GameState;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;


public class MobTeleportCommand implements CommandExecutor, TabCompleter {

    private final MonsterBattle plugin;

    private final Map<UUID, List<UUID>> lastLists = new HashMap<>();

    public MobTeleportCommand(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Players only.");
            return true;
        }
        if (!player.hasPermission("monsterbattle.mobtp")) {
            player.sendMessage(ChatColor.RED + "You lack permission: monsterbattle.mobtp");
            return true;
        }
        if (plugin.getDataController().getGameState() != GameState.BATTLE) {
            player.sendMessage(ChatColor.RED + "Not in battle phase.");
            return true;
        }
        var sb = Bukkit.getScoreboardManager();
        Team team = sb.getMainScoreboard().getEntryTeam(player.getName());
        if (team == null) {
            player.sendMessage(ChatColor.RED + "You're not on a team.");
            return true;
        }
        String teamName = team.getName();
        Set<UUID> tracked = plugin.getDataController().getActiveMonstersView().getOrDefault(teamName, Collections.emptySet());
        if (tracked.isEmpty()) {
            player.sendMessage(ChatColor.GREEN + "No remaining tracked mobs for your team.");
            return true;
        }

        if (args.length == 0) {
            return teleportNearest(player, tracked);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "nearest":
                return teleportNearest(player, tracked);
            case "random":
                return teleportRandom(player, tracked);
            case "list":
                return listMobs(player, tracked);
            default:

                if (sub.length() <= 3 && sub.chars().allMatch(Character::isDigit)) {
                    try {
                        int idx = Integer.parseInt(sub);
                        return teleportIndex(player, tracked, idx);
                    } catch (NumberFormatException ignored) {
                    }
                }
                try {
                    UUID uuid = UUID.fromString(sub);
                    return teleportUUID(player, tracked, uuid);
                } catch (IllegalArgumentException ignored) {
                    player.sendMessage(ChatColor.RED + "Unknown argument. Use /" + label + " [nearest|random|list|index|uuid]");
                    return true;
                }
        }
    }

    private boolean listMobs(Player player, Set<UUID> tracked) {
        List<Entity> entities = tracked.stream()
                .map(Bukkit::getEntity)
                .filter(Objects::nonNull)
                .filter(e -> !e.isDead() && e.isValid())
                .collect(Collectors.toList());
        if (entities.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "All tracked mobs appear to be gone (waiting for maintenance cleanup).");
            return true;
        }

        entities.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(player.getLocation())));
        List<UUID> ordering = entities.stream().map(Entity::getUniqueId).collect(Collectors.toList());
        lastLists.put(player.getUniqueId(), ordering);
        player.sendMessage(ChatColor.AQUA + "Remaining mobs (" + ordering.size() + "):");
        for (int i = 0; i < ordering.size(); i++) {
            Entity e = entities.get(i);
            Location l = e.getLocation();
            String info = ChatColor.YELLOW + "#" + i + ChatColor.GRAY + " | " + ChatColor.GOLD + e.getType().name() + ChatColor.GRAY + " - " + l.getWorld().getName() + " " +
                    String.format("(%.1f, %.1f, %.1f)", l.getX(), l.getY(), l.getZ());
            if (e instanceof LivingEntity le) {
                info += ChatColor.DARK_GRAY + " HP:" + ChatColor.RED + String.format("%.1f", le.getHealth());
            }
            double dist = Math.sqrt(l.distanceSquared(player.getLocation()));
            info += ChatColor.BLUE + String.format(" d=%.1f", dist);
            player.sendMessage(info);
        }
        player.sendMessage(ChatColor.DARK_GRAY + "Tip: /mobtp <index> to teleport.");
        return true;
    }

    private boolean teleportNearest(Player player, Set<UUID> tracked) {
        Entity nearest = null;
        double best = Double.MAX_VALUE;
        for (UUID id : tracked) {
            Entity e = Bukkit.getEntity(id);
            if (e == null || e.isDead() || !e.isValid()) continue;
            double d = e.getLocation().distanceSquared(player.getLocation());
            if (d < best) {
                best = d;
                nearest = e;
            }
        }
        if (nearest == null) {
            player.sendMessage(ChatColor.YELLOW + "No valid mobs found (may be waiting for cleanup).");
            return true;
        }
        player.teleport(nearest.getLocation());
        player.sendMessage(ChatColor.GREEN + "Teleported to nearest mob: " + ChatColor.GOLD + nearest.getType().name());
        return true;
    }

    private boolean teleportRandom(Player player, Set<UUID> tracked) {
        List<Entity> list = tracked.stream().map(Bukkit::getEntity)
                .filter(Objects::nonNull)
                .filter(e -> !e.isDead() && e.isValid())
                .collect(Collectors.toList());
        if (list.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No valid mobs found (may be waiting for cleanup).");
            return true;
        }
        Entity e = list.get(new Random().nextInt(list.size()));
        player.teleport(e.getLocation());
        player.sendMessage(ChatColor.GREEN + "Teleported to random mob: " + ChatColor.GOLD + e.getType().name());
        return true;
    }

    private boolean teleportIndex(Player player, Set<UUID> tracked, int idx) {
        List<UUID> last = lastLists.get(player.getUniqueId());
        if (last == null) {
            player.sendMessage(ChatColor.RED + "No cached list. Use /mobtp list first.");
            return true;
        }
        if (idx < 0 || idx >= last.size()) {
            player.sendMessage(ChatColor.RED + "Index out of range 0-" + (last.size() - 1));
            return true;
        }
        UUID id = last.get(idx);
        if (!tracked.contains(id)) {
            player.sendMessage(ChatColor.RED + "That mob is no longer tracked.");
            return true;
        }
        Entity e = Bukkit.getEntity(id);
        if (e == null || e.isDead() || !e.isValid()) {
            player.sendMessage(ChatColor.RED + "Mob is gone.");
            return true;
        }
        player.teleport(e.getLocation());
        player.sendMessage(ChatColor.GREEN + "Teleported to mob #" + idx + ": " + ChatColor.GOLD + e.getType().name());
        return true;
    }

    private boolean teleportUUID(Player player, Set<UUID> tracked, UUID uuid) {
        if (!tracked.contains(uuid)) {
            player.sendMessage(ChatColor.RED + "UUID not a tracked mob for your team.");
            return true;
        }
        Entity e = Bukkit.getEntity(uuid);
        if (e == null || e.isDead() || !e.isValid()) {
            player.sendMessage(ChatColor.RED + "Mob is gone.");
            return true;
        }
        player.teleport(e.getLocation());
        player.sendMessage(ChatColor.GREEN + "Teleported to mob: " + ChatColor.GOLD + e.getType().name());
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player p) || !p.hasPermission("monsterbattle.mobtp")) return List.of();
        if (args.length == 1) {
            String pref = args[0].toLowerCase(Locale.ROOT);
            List<String> base = Arrays.asList("nearest", "random", "list");
            return base.stream().filter(s -> s.startsWith(pref)).collect(Collectors.toList());
        }
        return List.of();
    }
}

