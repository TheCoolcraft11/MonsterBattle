package de.thecoolcraft11.monsterBattle.command;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import de.thecoolcraft11.monsterBattle.util.GameState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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

@SuppressWarnings("SameReturnValue")
public class MobTeleportCommand implements CommandExecutor, TabCompleter {

    private final MonsterBattle plugin;

    private final Map<UUID, List<UUID>> lastLists = new HashMap<>();

    public MobTeleportCommand(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("monsterbattle.mobtp")) {
            player.sendMessage(Component.text("You dont have permission to do that!", NamedTextColor.RED));
            return true;
        }
        if (plugin.getDataController().getGameState() != GameState.BATTLE) {
            player.sendMessage(Component.text("Not in battle phase.", NamedTextColor.RED));
            return true;
        }
        var sb = Bukkit.getScoreboardManager();
        Team team = sb.getMainScoreboard().getEntryTeam(player.getName());
        if (team == null) {
            player.sendMessage(Component.text("You're not on a team.", NamedTextColor.RED));
            return true;
        }
        String teamName = team.getName();
        Set<UUID> tracked = plugin.getDataController().getActiveMonstersView().getOrDefault(teamName, Collections.emptySet());
        if (tracked.isEmpty()) {
            player.sendMessage(Component.text("No remaining tracked mobs for your team.", NamedTextColor.GREEN));
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
                    player.sendMessage(Component.text("Unknown argument. Use /" + label + " [nearest|random|list|index|uuid]", NamedTextColor.RED));
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
            player.sendMessage(Component.text("All tracked mobs appear to be gone (waiting for maintenance cleanup).", NamedTextColor.YELLOW));
            return true;
        }

        entities.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(player.getLocation())));
        List<UUID> ordering = entities.stream().map(Entity::getUniqueId).collect(Collectors.toList());
        lastLists.put(player.getUniqueId(), ordering);
        player.sendMessage(Component.text("Remaining mobs (" + ordering.size() + "):", NamedTextColor.AQUA));
        for (int i = 0; i < ordering.size(); i++) {
            Entity e = entities.get(i);
            Location l = e.getLocation();
            Component info = Component.text("#" + i, NamedTextColor.YELLOW)
                    .append(Component.text(" | ", NamedTextColor.GRAY))
                    .append(Component.text(e.getType().name(), NamedTextColor.GOLD))
                    .append(Component.text(" - " + l.getWorld().getName() + " " +
                            String.format("(%.1f, %.1f, %.1f)", l.getX(), l.getY(), l.getZ()), NamedTextColor.GRAY));
            if (e instanceof LivingEntity le) {
                info = info.append(Component.text(" HP:", NamedTextColor.DARK_GRAY))
                        .append(Component.text(String.format("%.1f", le.getHealth()), NamedTextColor.RED));
            }
            double dist = Math.sqrt(l.distanceSquared(player.getLocation()));
            info = info.append(Component.text(String.format(" d=%.1f", dist), NamedTextColor.BLUE));
            player.sendMessage(info);
        }
        player.sendMessage(Component.text("Tip: /mobtp <index> to teleport.", NamedTextColor.DARK_GRAY));
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
            player.sendMessage(Component.text("No valid mobs found (may be waiting for cleanup).", NamedTextColor.YELLOW));
            return true;
        }
        player.teleport(nearest.getLocation());
        player.sendMessage(Component.text("Teleported to nearest mob: ", NamedTextColor.GREEN)
                .append(Component.text(nearest.getType().name(), NamedTextColor.GOLD)));
        return true;
    }

    private boolean teleportRandom(Player player, Set<UUID> tracked) {
        List<Entity> list = tracked.stream().map(Bukkit::getEntity)
                .filter(Objects::nonNull)
                .filter(e -> !e.isDead() && e.isValid())
                .toList();
        if (list.isEmpty()) {
            player.sendMessage(Component.text("No valid mobs found (may be waiting for cleanup).", NamedTextColor.YELLOW));
            return true;
        }
        Entity e = list.get(new Random().nextInt(list.size()));
        player.teleport(e.getLocation());
        player.sendMessage(Component.text("Teleported to random mob: ", NamedTextColor.GREEN)
                .append(Component.text(e.getType().name(), NamedTextColor.GOLD)));
        return true;
    }

    private boolean teleportIndex(Player player, Set<UUID> tracked, int idx) {
        List<UUID> last = lastLists.get(player.getUniqueId());
        if (last == null) {
            player.sendMessage(Component.text("No cached list. Use /mobtp list first.", NamedTextColor.RED));
            return true;
        }
        if (idx < 0 || idx >= last.size()) {
            player.sendMessage(Component.text("Index out of range 0-" + (last.size() - 1), NamedTextColor.RED));
            return true;
        }
        UUID id = last.get(idx);
        if (!tracked.contains(id)) {
            player.sendMessage(Component.text("That mob is no longer tracked.", NamedTextColor.RED));
            return true;
        }
        Entity e = Bukkit.getEntity(id);
        if (e == null || e.isDead() || !e.isValid()) {
            player.sendMessage(Component.text("Mob is gone.", NamedTextColor.RED));
            return true;
        }
        player.teleport(e.getLocation());
        player.sendMessage(Component.text("Teleported to mob #" + idx + ": ", NamedTextColor.GREEN)
                .append(Component.text(e.getType().name(), NamedTextColor.GOLD)));
        return true;
    }

    private boolean teleportUUID(Player player, Set<UUID> tracked, UUID uuid) {
        if (!tracked.contains(uuid)) {
            player.sendMessage(Component.text("UUID not a tracked mob for your team.", NamedTextColor.RED));
            return true;
        }
        Entity e = Bukkit.getEntity(uuid);
        if (e == null || e.isDead() || !e.isValid()) {
            player.sendMessage(Component.text("Mob is gone.", NamedTextColor.RED));
            return true;
        }
        player.teleport(e.getLocation());
        player.sendMessage(Component.text("Teleported to mob: ", NamedTextColor.GREEN)
                .append(Component.text(e.getType().name(), NamedTextColor.GOLD)));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player p) || !p.hasPermission("monsterbattle.mobtp")) return List.of();
        if (args.length == 1) {
            String pref = args[0].toLowerCase(Locale.ROOT);
            List<String> base = Arrays.asList("nearest", "random", "list");
            return base.stream().filter(s -> s.startsWith(pref)).collect(Collectors.toList());
        }
        return List.of();
    }
}

