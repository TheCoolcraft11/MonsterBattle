package de.thecoolcraft11.monsterBattle.command;

import de.thecoolcraft11.monsterBattle.MonsterBattle;
import de.thecoolcraft11.monsterBattle.listener.CapturedMobsInventoryListener;
import de.thecoolcraft11.monsterBattle.util.GameState;
import de.thecoolcraft11.monsterBattle.util.MobSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (plugin.getDataController().getGameState() == GameState.LOBBY || plugin.getDataController().getGameState() == GameState.BATTLE) {
            player.sendMessage(ChatColor.RED + "You cannot view captured mobs while in the " + (plugin.getDataController().getGameState() == GameState.LOBBY ? "lobby" : "battle") + " phase.");
            return true;
        }

        if (plugin.getDataController().getGameState() == GameState.ENDED) {
            if (player.hasPermission("monsterbattle.captured.summary")) {

                String targetTeamName = args.length > 0 ? args[0] : null;

                if (targetTeamName == null) {
                    player.sendMessage(ChatColor.RED + "Usage: /capturedmobs <team> - Opens the result screen for all players");
                    return true;
                }


                ScoreboardManager sm = Bukkit.getScoreboardManager();
                Team team = sm.getMainScoreboard().getTeam(targetTeamName);
                if (team == null) {
                    player.sendMessage(ChatColor.RED + "Team '" + targetTeamName + "' does not exist.");
                    return true;
                }


                List<MobSnapshot> kills = plugin.getDataController().getCapturedMobsForTeam(targetTeamName);
                if (kills.isEmpty()) {
                    player.sendMessage(ChatColor.YELLOW + "No captured mobs recorded for team " + ChatColor.GOLD + targetTeamName + ChatColor.YELLOW + ".");
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
                        .collect(Collectors.toList());

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
                    Inventory inv = Bukkit.createInventory(null, size, ChatColor.DARK_GREEN + "Captured - " + targetTeamName);

                    if (sorted.size() > inv.getSize()) {
                        ItemStack overflow = new ItemStack(Material.BARRIER);
                        var meta = overflow.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName(ChatColor.RED + "+" + (sorted.size() - inv.getSize()) + " more types...");
                            overflow.setItemMeta(meta);
                        }
                        inv.setItem(inv.getSize() - 1, overflow);
                    }

                    onlinePlayer.openInventory(inv);
                    onlinePlayer.sendMessage(ChatColor.GREEN + "Results for team " + ChatColor.GOLD + targetTeamName + ChatColor.GREEN + ": " + ChatColor.AQUA + total + ChatColor.GREEN + " total captured");

                    if (listener != null) {
                        listener.startAnimation(onlinePlayer, inv, animationSlots);
                    }

                    playersShown++;
                }

                player.sendMessage(ChatColor.GREEN + "Opened result screen for " + ChatColor.GOLD + playersShown + ChatColor.GREEN + " players.");
                return true;
            } else {

                player.sendMessage(ChatColor.RED + "You cannot view captured mobs in this phase.");
                return true;
            }
        }


        String playerTeam = findPlayerTeam(player.getName());
        String targetTeamName = args.length > 0 ? args[0] : playerTeam;
        if (targetTeamName == null) {
            player.sendMessage(ChatColor.RED + "Team not found. Provide a team name or join a team first.");
            return true;
        }
        if (args.length > 0 && !player.hasPermission("monsterbattle.captured.others") && (playerTeam == null || !playerTeam.equalsIgnoreCase(targetTeamName))) {
            player.sendMessage(ChatColor.RED + "You don't have permission to view other teams' captures.");
            return true;
        }

        List<MobSnapshot> kills = plugin.getDataController().getCapturedMobsForTeam(targetTeamName);
        if (kills.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No captured mobs recorded for team " + ChatColor.GOLD + targetTeamName + ChatColor.YELLOW + ".");
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
                .collect(Collectors.toList());

        int size = Math.min(54, ((sorted.size() - 1) / 9 + 1) * 9);
        if (size <= 0) size = 9;
        Inventory inv = Bukkit.createInventory(null, size, ChatColor.DARK_GREEN + "Captured - " + targetTeamName);


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
                meta.setDisplayName(ChatColor.RED + "+" + (sorted.size() - inv.getSize()) + " more types...");
                overflow.setItemMeta(meta);
            }
            inv.setItem(inv.getSize() - 1, overflow);
        }

        player.openInventory(inv);
        player.sendMessage(ChatColor.GREEN + "Showing captured mobs for team " + ChatColor.GOLD + targetTeamName + ChatColor.GREEN + ": " + ChatColor.AQUA + total + ChatColor.GREEN + " total");


        CapturedMobsInventoryListener listener = plugin.getCapturedMobsInventoryListener();
        if (listener != null) {
            listener.startAnimation(player, inv, animationSlots);
        }

        return true;
    }

    @Nullable
    private String findPlayerTeam(String playerName) {
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm == null) return null;
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
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            ScoreboardManager sm = Bukkit.getScoreboardManager();
            if (sm == null) return Collections.emptyList();
            return sm.getMainScoreboard().getTeams().stream()
                    .map(Team::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .limit(50)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
