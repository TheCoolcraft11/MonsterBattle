package de.thecoolcraft11.monsterBattle.command;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import de.thecoolcraft11.monsterBattle.listener.CapturedMobsInventoryListener;
import de.thecoolcraft11.monsterBattle.util.GameState;
import de.thecoolcraft11.monsterBattle.util.MobSnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;


public class CapturedMobsCommand implements CommandExecutor, TabCompleter {

    private final MonsterBattle plugin;

    public CapturedMobsCommand(MonsterBattle plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        if (plugin.getDataController().getGameState() == GameState.LOBBY || plugin.getDataController().getGameState() == GameState.BATTLE) {
            player.sendMessage(Component.text("You cannot view captured mobs while in the " + (plugin.getDataController().getGameState() == GameState.LOBBY ? "lobby" : "battle") + " phase.", NamedTextColor.RED));
            return true;
        }

        if (plugin.getDataController().getGameState() == GameState.ENDED) {
            if (player.hasPermission("monsterbattle.captured.summary")) {

                String targetTeamName = args.length > 0 ? args[0] : null;

                if (targetTeamName == null) {
                    player.sendMessage(Component.text("Usage: /capturedmobs <team> - Opens the result screen for all players", NamedTextColor.RED));
                    return true;
                }


                ScoreboardManager sm = Bukkit.getScoreboardManager();
                Team team = sm.getMainScoreboard().getTeam(targetTeamName);
                if (team == null) {
                    player.sendMessage(Component.text("Team '" + targetTeamName + "' does not exist.", NamedTextColor.RED));
                    return true;
                }


                List<MobSnapshot> kills = plugin.getDataController().getCapturedMobsForTeam(targetTeamName);
                if (kills.isEmpty()) {
                    player.sendMessage(Component.text("No captured mobs recorded for team ", NamedTextColor.YELLOW)
                            .append(Component.text(targetTeamName, NamedTextColor.GOLD))
                            .append(Component.text(".", NamedTextColor.YELLOW)));
                    return true;
                }


                Map<EntityType, Integer> counts = new HashMap<>();
                for (MobSnapshot snap : kills) counts.merge(snap.getType(), 1, Integer::sum);
                int total = kills.size();

                List<Map.Entry<EntityType, Integer>> sorted = counts.entrySet().stream()
                        .sorted((a, b) -> {
                            int cmp = Integer.compare(b.getValue(), a.getValue());
                            if (cmp != 0) return cmp;
                            return a.getKey().name().compareToIgnoreCase(b.getKey().name());
                        })
                        .toList();

                int size = Math.min(54, ((sorted.size() - 1) / 9 + 1) * 9);
                if (size <= 0) size = 9;

                List<CapturedMobsInventoryListener.SlotData> animationSlots = new ArrayList<>();
                int slot = 0;

                for (Map.Entry<EntityType, Integer> e : sorted) {
                    if (slot >= size) break;
                    int count = e.getValue();
                    if (count <= 0) continue;

                    EntityType type = e.getKey();
                    Material mat = spawnEggFor(type);
                    if (mat == null) mat = Material.PAPER;

                    int displayAmount = Math.min(count, 99);
                    double pct = (count * 100.0) / total;

                    animationSlots.add(new CapturedMobsInventoryListener.SlotData(
                            slot, type, mat, displayAmount, count, pct
                    ));

                    slot++;
                }


                int playersShown = 0;
                CapturedMobsInventoryListener listener = plugin.getCapturedMobsInventoryListener();

                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    Inventory inv = Bukkit.createInventory(null, size, Component.text("Captured - " + targetTeamName, NamedTextColor.DARK_GREEN));

                    if (sorted.size() > inv.getSize()) {
                        ItemStack overflow = new ItemStack(Material.BARRIER);
                        var meta = overflow.getItemMeta();
                        if (meta != null) {
                            meta.displayName(Component.text("+" + (sorted.size() - inv.getSize()) + " more types...", NamedTextColor.RED));
                            overflow.setItemMeta(meta);
                        }
                        inv.setItem(inv.getSize() - 1, overflow);
                    }

                    onlinePlayer.openInventory(inv);
                    onlinePlayer.sendMessage(Component.text("Results for team ", NamedTextColor.GREEN)
                            .append(Component.text(targetTeamName, NamedTextColor.GOLD))
                            .append(Component.text(": ", NamedTextColor.GREEN))
                            .append(Component.text(total, NamedTextColor.AQUA))
                            .append(Component.text(" total captured", NamedTextColor.GREEN)));

                    if (listener != null) {
                        listener.startAnimation(onlinePlayer, inv, animationSlots);
                    }

                    playersShown++;
                }

                player.sendMessage(Component.text("Opened result screen for ", NamedTextColor.GREEN)
                        .append(Component.text(playersShown, NamedTextColor.GOLD))
                        .append(Component.text(" players.", NamedTextColor.GREEN)));
            } else {

                player.sendMessage(Component.text("You cannot view captured mobs in this phase.", NamedTextColor.RED));
            }
            return true;
        }


        String playerTeam = findPlayerTeam(player.getName());
        String targetTeamName = args.length > 0 ? args[0] : playerTeam;
        if (targetTeamName == null) {
            player.sendMessage(Component.text("Team not found. Provide a team name or join a team first.", NamedTextColor.RED));
            return true;
        }
        if (args.length > 0 && !player.hasPermission("monsterbattle.captured.others") && (playerTeam == null || !playerTeam.equalsIgnoreCase(targetTeamName))) {
            player.sendMessage(Component.text("You don't have permission to view other teams' captures.", NamedTextColor.RED));
            return true;
        }

        List<MobSnapshot> kills = plugin.getDataController().getCapturedMobsForTeam(targetTeamName);
        if (kills.isEmpty()) {
            player.sendMessage(Component.text("No captured mobs recorded for team ", NamedTextColor.YELLOW)
                    .append(Component.text(targetTeamName, NamedTextColor.GOLD))
                    .append(Component.text(".", NamedTextColor.YELLOW)));
            return true;
        }

        Map<EntityType, Integer> counts = new HashMap<>();
        for (MobSnapshot snap : kills) counts.merge(snap.getType(), 1, Integer::sum);
        int total = kills.size();

        List<Map.Entry<EntityType, Integer>> sorted = counts.entrySet().stream()
                .sorted((a, b) -> {
                    int cmp = Integer.compare(b.getValue(), a.getValue());
                    if (cmp != 0) return cmp;
                    return a.getKey().name().compareToIgnoreCase(b.getKey().name());
                })
                .toList();

        int size = Math.min(54, ((sorted.size() - 1) / 9 + 1) * 9);
        if (size <= 0) size = 9;
        Inventory inv = Bukkit.createInventory(null, size, Component.text("Captured - " + targetTeamName, NamedTextColor.DARK_GREEN));


        List<CapturedMobsInventoryListener.SlotData> animationSlots = new ArrayList<>();
        int slot = 0;

        for (Map.Entry<EntityType, Integer> e : sorted) {
            if (slot >= inv.getSize()) break;
            int count = e.getValue();
            if (count <= 0) continue;

            EntityType type = e.getKey();
            Material mat = spawnEggFor(type);
            if (mat == null) mat = Material.PAPER;

            int displayAmount = Math.min(count, 99);
            double pct = (count * 100.0) / total;


            animationSlots.add(new CapturedMobsInventoryListener.SlotData(
                    slot, type, mat, displayAmount, count, pct
            ));


            slot++;
        }

        if (sorted.size() > inv.getSize()) {
            ItemStack overflow = new ItemStack(Material.BARRIER);
            var meta = overflow.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text("+" + (sorted.size() - inv.getSize()) + " more types...", NamedTextColor.RED));
                overflow.setItemMeta(meta);
            }
            inv.setItem(inv.getSize() - 1, overflow);
        }

        player.openInventory(inv);
        player.sendMessage(Component.text("Showing captured mobs for team ", NamedTextColor.GREEN)
                .append(Component.text(targetTeamName, NamedTextColor.GOLD))
                .append(Component.text(": ", NamedTextColor.GREEN))
                .append(Component.text(total, NamedTextColor.AQUA))
                .append(Component.text(" total", NamedTextColor.GREEN)));


        CapturedMobsInventoryListener listener = plugin.getCapturedMobsInventoryListener();
        if (listener != null) {
            listener.startAnimation(player, inv, animationSlots);
        }

        return true;
    }

    @Nullable
    private String findPlayerTeam(String playerName) {
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        for (Team t : sm.getMainScoreboard().getTeams()) {
            if (t.getEntries().contains(playerName)) return t.getName();
        }
        return null;
    }

    private Material spawnEggFor(EntityType type) {
        String candidate = type.name() + "_SPAWN_EGG";
        try {
            return Material.valueOf(candidate);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            ScoreboardManager sm = Bukkit.getScoreboardManager();
            return sm.getMainScoreboard().getTeams().stream()
                    .map(Team::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .limit(50)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
